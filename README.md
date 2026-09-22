# Habit Tracker

Proyecto pequeño de práctica para afianzar **Vue 3** en frontend y **Java + Spring Boot** en backend, con una integración ligera de IA (Gemini) para sugerir y comentar hábitos.

## Objetivo del proyecto

Aprender/consolidar:
- **Backend**: Spring Boot (Web, Data JPA, Validation), Java moderno (records, Streams, LocalDate).
- **Frontend**: Vue 3 con Composition API (`<script setup>`), Pinia, Vue Router.
- **IA**: integración puntual con Gemini para un chat de recomendación de hábitos (pendiente de implementar).

## Estado actual

- [x] Estructura de carpetas backend + frontend
- [x] Entidades `Habit` y `HabitLog`
- [x] CRUD de hábitos (API REST)
- [x] Registro de cumplimiento diario (`POST /api/habits/{id}/logs`)
- [x] Cálculo de racha y % de cumplimiento semanal (`GET /api/habits/{id}/streak`)
- [x] Tests unitarios del cálculo de racha (JUnit + Mockito)
- [x] Vistas Vue: lista de hábitos, detalle con racha
- [x] Store de Pinia + cliente API
- [x] Chat con Gemini (`AiChatController` + `AiChatService` en el backend, `ChatPanel.vue` + `useHabitChat` en el frontend)
- [ ] Autenticación (deliberadamente fuera del alcance de la v1)
- [ ] Despliegue (Render backend + Vercel frontend + Neon Postgres)

## Estructura

```
habit-tracker/
├── backend/    Spring Boot (Java 21, Maven)
└── frontend/   Vue 3 + TypeScript (Vite)
```

## Cómo arrancar en local

### Backend
```bash
cd backend
# Necesitas Postgres local o apuntar DATABASE_URL a Neon
./mvnw spring-boot:run
```
Variables de entorno relevantes (ver `application.properties`):
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `CORS_ALLOWED_ORIGINS`
- `GEMINI_API_KEY` (se usará al implementar el chat)

### Frontend
```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

## Endpoints actuales

| Método | Ruta                       | Descripción                          |
|--------|-----------------------------|---------------------------------------|
| GET    | `/api/habits`               | Listar hábitos                        |
| GET    | `/api/habits/{id}`          | Detalle de un hábito                  |
| POST   | `/api/habits`                | Crear hábito                          |
| PUT    | `/api/habits/{id}`          | Editar hábito                         |
| DELETE | `/api/habits/{id}`          | Eliminar hábito                       |
| POST   | `/api/habits/{id}/logs`     | Marcar/desmarcar cumplimiento de un día |
| GET    | `/api/habits/{id}/streak`   | Racha actual + % cumplimiento semanal |
| POST   | `/api/ai/chat`               | Chat de recomendación de hábitos con Gemini |

### Configurar la clave de Gemini

1. Crea una clave gratuita en [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Expórtala como variable de entorno antes de levantar el backend:
   ```powershell
   $env:GEMINI_API_KEY="tu_clave_aqui"
   ```
3. Si no la configuras, el endpoint `/api/ai/chat` responderá con un error 502 indicando que falta la clave — el resto de la app (CRUD, rachas) sigue funcionando igual.

## Próximos pasos

1. Validar el backend (levantar, probar endpoints con Postman/curl o `.http`).
2. Instalar dependencias del frontend y verificar la conexión con la API.
3. Diseñar el prompt del chat de Gemini y completar `AiChatController` / `ChatPanel.vue`.
4. Desplegar (Render + Vercel + Neon).
5. Documentación de cierre (README técnico ampliado, PDF de estudio, actualización de CV) según cierre habitual de proyectos.
