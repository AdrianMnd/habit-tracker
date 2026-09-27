package com.adrian.habittracker.service;

import com.adrian.habittracker.dto.push.PushConfigResponse;
import com.adrian.habittracker.dto.push.PushSubscriptionRequest;
import com.adrian.habittracker.dto.push.ReminderRunResponse;
import com.adrian.habittracker.dto.push.ReminderSettingsRequest;
import com.adrian.habittracker.dto.push.ReminderSettingsResponse;
import com.adrian.habittracker.entity.Habit;
import com.adrian.habittracker.entity.HabitLog;
import com.adrian.habittracker.entity.PushSubscription;
import com.adrian.habittracker.entity.User;
import com.adrian.habittracker.exception.InvalidRequestException;
import com.adrian.habittracker.exception.ResourceNotFoundException;
import com.adrian.habittracker.push.WebPushSender;
import com.adrian.habittracker.repository.HabitLogRepository;
import com.adrian.habittracker.repository.HabitRepository;
import com.adrian.habittracker.repository.PushSubscriptionRepository;
import com.adrian.habittracker.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final WebPushSender sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PushConfigResponse config() {
        return new PushConfigResponse(sender.isEnabled(), sender.publicKey().orElse(null));
    }

    /**
     * Alta de una suscripcion ("upsert" por endpoint): el mismo navegador
     * puede volver a suscribirse con claves nuevas, o pasar a otra cuenta
     * (logout + login con otro usuario en el mismo navegador). En ambos
     * casos se actualiza la fila existente en vez de duplicarla.
     */
    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        if (!WebPushSender.isAllowedEndpoint(request.endpoint())) {
            throw new InvalidRequestException("El endpoint de la suscripcion no es de un servicio de push reconocido");
        }
        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setUser(userRepository.getReferenceById(userId));
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscriptionRepository.save(subscription);
    }

    public void unsubscribe(Long userId, String endpoint) {
        subscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
    }

    @Transactional(readOnly = true)
    public ReminderSettingsResponse getSettings(Long userId) {
        User user = findUser(userId);
        return new ReminderSettingsResponse(user.getReminderHour(), user.getReminderTimeZone(),
                subscriptionRepository.countByUserId(userId));
    }

    @Transactional
    public ReminderSettingsResponse updateSettings(Long userId, ReminderSettingsRequest request) {
        ZoneId zone;
        try {
            // ZoneId.of valida contra la base de datos de zonas horarias
            // (IANA) que trae la JVM: "Europe/Madrid" vale, "Europa/Sevilla"
            // o "GMT+25" lanzan DateTimeException.
            zone = ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw new InvalidRequestException("Zona horaria no valida: " + request.timeZone());
        }
        User user = findUser(userId);
        user.setReminderHour(request.hour());
        user.setReminderTimeZone(zone.getId());
        return new ReminderSettingsResponse(user.getReminderHour(), user.getReminderTimeZone(),
                subscriptionRepository.countByUserId(userId));
    }

    /** Notificacion de prueba a todos los dispositivos del usuario. Devuelve cuantos la recibieron. */
    public int sendTest(Long userId) {
        requireEnabled();
        return sendToUser(userId, payload("Las notificaciones funcionan correctamente."));
    }

    /**
     * Lo llama el cron (GitHub Actions) cada hora. Para cada usuario con
     * recordatorio: si en SU zona horaria ya ha pasado su hora y hoy aun no
     * se le ha avisado, cuenta sus habitos pendientes y le avisa.
     *
     * "Ya ha pasado la hora" (>=) y no "es exactamente la hora" (==): el cron
     * de GitHub Actions no es puntual (puede llegar con decenas de minutos de
     * retraso, o saltarse alguna ejecucion en horas punta). Con >=, un
     * recordatorio de las 21:00 que llega a las 22:10 se envia igual; y
     * claimDailyReminder garantiza que no se repita.
     */
    public ReminderRunResponse sendDueReminders() {
        if (!sender.isEnabled()) {
            log.warn("Recordatorios omitidos: Web Push no esta configurado");
            return new ReminderRunResponse(0, 0, 0);
        }

        List<User> users = userRepository.findByReminderHourIsNotNull();
        int notified = 0;
        int delivered = 0;

        for (User user : users) {
            ZonedDateTime localNow = clock.instant().atZone(zoneOf(user));
            LocalDate localToday = localNow.toLocalDate();

            if (localNow.getHour() < user.getReminderHour() || localToday.equals(user.getLastReminderDate())) {
                continue;
            }
            // Marcamos ANTES de enviar ("como mucho una vez"): si el envio
            // fallara a medias, ese dia el usuario se quedaria sin aviso - un
            // mal menor comparado con la alternativa ("al menos una vez"), en
            // la que un reintento podria mandarle el mismo aviso varias veces.
            if (userRepository.claimDailyReminder(user.getId(), localToday) == 0) {
                continue; // otra ejecucion del cron se nos adelanto
            }

            long pending = countPendingHabits(user.getId(), localToday);
            if (pending == 0) {
                continue; // todo hecho: no molestamos
            }

            String message = pending == 1
                    ? "Te queda 1 hábito por marcar hoy. ¡Tú puedes!"
                    : "Te quedan " + pending + " hábitos por marcar hoy. ¡Tú puedes!";
            int deliveredToUser = sendToUser(user.getId(), payload(message));
            if (deliveredToUser > 0) {
                notified++;
                delivered += deliveredToUser;
            }
        }

        log.info("Recordatorios: {} usuarios con recordatorio, {} avisados, {} notificaciones entregadas",
                users.size(), notified, delivered);
        return new ReminderRunResponse(users.size(), notified, delivered);
    }

    private long countPendingHabits(Long userId, LocalDate day) {
        List<Habit> habits = habitRepository.findByUserId(userId); // solo activos
        return habits.stream()
                .filter(habit -> habitLogRepository.findByHabitIdAndLogDate(habit.getId(), day)
                        .map(HabitLog::isCompleted)
                        .map(completed -> !completed)
                        .orElse(true))
                .count();
    }

    private int sendToUser(Long userId, String payloadJson) {
        int delivered = 0;
        for (PushSubscription subscription : subscriptionRepository.findByUserId(userId)) {
            WebPushSender.Result result = sender.send(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payloadJson);
            switch (result) {
                case DELIVERED -> delivered++;
                case EXPIRED -> subscriptionRepository.delete(subscription);
                case FAILED -> { /* ya registrado en WebPushSender; se reintentara otro dia */ }
            }
        }
        return delivered;
    }

    /**
     * El JSON que recibira el Service Worker en el evento "push". "url" es a
     * donde llevar al usuario si toca la notificacion.
     */
    private String payload(String body) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", "Habit Tracker", "body", body, "url", "/"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ZoneId zoneOf(User user) {
        try {
            return user.getReminderTimeZone() == null ? ZoneId.of("UTC") : ZoneId.of(user.getReminderTimeZone());
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private void requireEnabled() {
        if (!sender.isEnabled()) {
            throw new InvalidRequestException("Las notificaciones push no estan configuradas en este servidor");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}