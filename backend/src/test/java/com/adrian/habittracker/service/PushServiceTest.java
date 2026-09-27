package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.push.PushSubscriptionRequest;
import com.adrian.habittracker.dto.push.ReminderRunResponse;
import com.adrian.habittracker.dto.push.ReminderSettingsRequest;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.entity.PushSubscription;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.InvalidRequestException;
import com.adrian.habittracker.push.WebPushSender;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.PushSubscriptionRepository;
import com.adrian.habittracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    private static final Long USER_ID = 5L;
    // 27/09/2026 es horario de verano: Madrid = UTC+2
    private static final LocalDate SEPT_27 = LocalDate.of(2026, 9, 27);

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitLogRepository habitLogRepository;
    @Mock
    private WebPushSender sender;

    @Test
    void enviaElRecordatorioSiEnLaZonaDelUsuarioYaHaPasadoSuHora() {
        User user = userWithReminder(21, "Europe/Madrid");
        givenUsersWithReminder(user);
        // 19:30 UTC = 21:30 en Madrid: ya han pasado las 21:00
        PushService service = serviceAt("2026-09-27T19:30:00Z");
        when(userRepository.claimDailyReminder(USER_ID, SEPT_27)).thenReturn(1);
        givenPendingHabits(2);
        givenOneSubscription();
        when(sender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(WebPushSender.Result.DELIVERED);

        ReminderRunResponse result = service.sendDueReminders();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("https://fcm.googleapis.com/fcm/send/abc"), eq("p256dh"), eq("auth"), payload.capture());
        assertThat(payload.getValue()).contains("Te quedan 2 hábitos por marcar hoy");
        assertThat(result.usersNotified()).isEqualTo(1);
    }

    @Test
    void noEnviaNadaAntesDeLaHoraDelUsuario() {
        givenUsersWithReminder(userWithReminder(21, "Europe/Madrid"));
        PushService service = serviceAt("2026-09-27T18:30:00Z"); // 20:30 en Madrid

        service.sendDueReminders();

        verify(userRepository, never()).claimDailyReminder(anyLong(), any());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void usaLaZonaHorariaDelUsuarioNoLaDelServidor() {
        // Mismo instante (21:30 en Madrid), pero en Nueva York son las 15:30
        givenUsersWithReminder(userWithReminder(21, "America/New_York"));
        PushService service = serviceAt("2026-09-27T19:30:00Z");

        service.sendDueReminders();

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void siOtraEjecucionYaReclamoElDiaNoSeEnviaDosVeces() {
        givenUsersWithReminder(userWithReminder(21, "Europe/Madrid"));
        PushService service = serviceAt("2026-09-27T19:30:00Z");
        when(userRepository.claimDailyReminder(USER_ID, SEPT_27)).thenReturn(0);

        service.sendDueReminders();

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void siNoQuedanHabitosPendientesNoSeMolestaAlUsuario() {
        givenUsersWithReminder(userWithReminder(21, "Europe/Madrid"));
        PushService service = serviceAt("2026-09-27T19:30:00Z");
        when(userRepository.claimDailyReminder(USER_ID, SEPT_27)).thenReturn(1);
        givenPendingHabits(0);

        service.sendDueReminders();

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unaSuscripcionCaducadaSeBorra() {
        givenUsersWithReminder(userWithReminder(21, "Europe/Madrid"));
        PushService service = serviceAt("2026-09-27T19:30:00Z");
        when(userRepository.claimDailyReminder(USER_ID, SEPT_27)).thenReturn(1);
        givenPendingHabits(1);
        PushSubscription subscription = givenOneSubscription();
        when(sender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(WebPushSender.Result.EXPIRED);

        service.sendDueReminders();

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void subscribeRechazaEndpointsQueNoSonDeUnServicioDePush() {
        PushService service = serviceAt("2026-09-27T19:30:00Z");
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://localhost:8080/actuator", new PushSubscriptionRequest.Keys("p256dh", "auth"));

        assertThatThrownBy(() -> service.subscribe(USER_ID, request))
                .isInstanceOf(InvalidRequestException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void updateSettingsRechazaUnaZonaHorariaInventada() {
        PushService service = serviceAt("2026-09-27T19:30:00Z");

        assertThatThrownBy(() -> service.updateSettings(USER_ID, new ReminderSettingsRequest(21, "Europa/Sevilla")))
                .isInstanceOf(InvalidRequestException.class);
    }

    // --- helpers ---

    private PushService serviceAt(String isoInstant) {
        // Clock.fixed: el "ahora" del servicio queda congelado en este instante.
        Clock clock = Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
        return new PushService(subscriptionRepository, userRepository, habitRepository, habitLogRepository,
                sender, new ObjectMapper(), clock);
    }

    private void givenUsersWithReminder(User... users) {
        when(sender.isEnabled()).thenReturn(true);
        when(userRepository.findByReminderHourIsNotNull()).thenReturn(List.of(users));
    }

    private User userWithReminder(int hour, String zone) {
        User user = new User();
        user.setId(USER_ID);
        user.setReminderHour(hour);
        user.setReminderTimeZone(zone);
        return user;
    }

    /** Crea 3 habitos activos, de los que "pending" siguen sin marcar hoy. */
    private void givenPendingHabits(int pending) {
        List<Habit> habits = List.of(habit(1L), habit(2L), habit(3L));
        when(habitRepository.findByUserId(USER_ID)).thenReturn(habits);
        for (int i = 0; i < habits.size(); i++) {
            Long habitId = habits.get(i).getId();
            if (i < pending) {
                // Sin registro para hoy = pendiente
                when(habitLogRepository.findByHabitIdAndLogDate(habitId, SEPT_27)).thenReturn(Optional.empty());
            } else {
                HabitLog done = new HabitLog();
                done.setCompleted(true);
                when(habitLogRepository.findByHabitIdAndLogDate(habitId, SEPT_27)).thenReturn(Optional.of(done));
            }
        }
    }

    private PushSubscription givenOneSubscription() {
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/abc");
        subscription.setP256dh("p256dh");
        subscription.setAuth("auth");
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription));
        return subscription;
    }

    private Habit habit(Long id) {
        Habit habit = new Habit();
        habit.setId(id);
        return habit;
    }
}