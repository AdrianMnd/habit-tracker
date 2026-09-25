# Habit Tracker

Proyecto de práctica para afianzar **Vue 3** en frontend y **Java + Spring Boot** en backend, con integración de IA (Gemini, con streaming) para sugerir y comentar hábitos. Monorepo, desplegado en producción: backend con Docker en Render, frontend en Vercel.

## Objetivo del proyecto

Aprender/consolidar:
- **Backend**: Spring Boot (Web, Data JPA, Validation, Security, Actuator), Java moderno (records, Streams, LocalDate), programación reactiva (WebClient, Flux, Server-Sent Events).
- **Frontend**: Vue 3 con Composition API (`<script setup>`), Pinia, Vue Router, consumo de streams (`fetch` + `ReadableStream`).
- **IA**: integración con Gemini, streaming en tiempo real, prompt engineering para combinar texto libre con datos estructurados.
- **DevOps**: monorepo con git, Docker (build multi-stage), despliegue en Render + Vercel.
- **Seguridad**: autenticación stateless con JWT, multiusuario.

## Estado actual

- [x] CRUD de hábitos + registro de cumplimiento diario + cálculo de racha
- [x] Tests unitarios del cálculo de racha (JUnit + Mockito)
- [x] Chat con Gemini, con streaming (SSE) y sugerencias de hábitos parseadas en vivo
- [x] Tema visual oscuro con sistema de tokens CSS
- [x] Autenticación JWT + multiusuario (Spring Security)
- [x] Despliegue: backend con Docker en Render, frontend en Vercel
- [x] Calendario semanal de hábitos
- [x] Logo/favicon
- [x] Tests e2e (Playwright)
- [x] CI (GitHub Actions)
- [x] PWA (instalable, con caché offline del app shell vía Service Worker)

## Estructura

```
habit-tracker/
├── backend/    Spring Boot (Java 21, Maven, Docker)
└── frontend/   Vue 3 + TypeScript (Vite)
```

## Cómo arrancar en local

### Backend
```bash
cd backend
cp .env.example .env   # rellena tus credenciales reales
mvn spring-boot:run
```
Variables de entorno (ver `.env.example`): `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `GEMINI_API_KEY`, `JWT_SECRET`. Se cargan automáticamente desde `.env` vía `springboot3-dotenv` (no hace falta exportarlas a mano).

### Frontend
```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

### Postgres para desarrollo/e2e
Los tests e2e (y opcionalmente el backend en local, si no quieres usar la BD de Neon en desarrollo) necesitan una Postgres accesible en `localhost:5432`. `docker-compose.yml`, en la raíz del repo, levanta una con los mismos valores que ya asume `application.properties` por defecto (`habit_tracker` / `postgres` / `postgres`), así que no hace falta tocar `DATABASE_URL` en el `.env` para usarla:
```bash
docker compose up -d
```

## Tests

### Unitarios
```bash
cd backend && mvn test        # JUnit + Mockito + AssertJ
cd frontend && npm run test:unit   # Vitest + jsdom
```

### End-to-end (Playwright)
Necesitan el backend real arrancado y accesible en `http://localhost:8080` (con Postgres arriba, ver más arriba) - Playwright arranca el frontend automáticamente, pero no el backend, porque este necesita su propio `.env` (`JWT_SECRET` sobre todo) y no tiene sentido orquestarlo desde el config de tests:
```bash
docker compose up -d
cd backend && mvn spring-boot:run   # en una terminal aparte, déjalo corriendo
cd frontend && npm run test:e2e
```
Cada test registra su propio usuario con un email único (timestamp), así que se pueden ejecutar repetidamente sin limpiar la base de datos entre tandas - solo va acumulando usuarios y hábitos de prueba, sin interferir entre ejecuciones.

## Autenticación

La API es multiusuario: cada hábito pertenece a quien lo creó, y ningún endpoint de hábitos/chat funciona sin un token JWT válido (excepto `/api/auth/**` y `/actuator/health`, que son públicos). El token se obtiene en `/api/auth/login` o `/api/auth/register` y se envía en cada petición como `Authorization: Bearer <token>`. El frontend gestiona esto automáticamente una vez has iniciado sesión.

## Endpoints actuales

| Método | Ruta                       | Descripción                          | Requiere token |
|--------|-----------------------------|---------------------------------------|:---:|
| POST   | `/api/auth/register`        | Crear cuenta                          | No |
| POST   | `/api/auth/login`           | Iniciar sesión                        | No |
| GET    | `/api/habits`               | Listar hábitos del usuario            | Sí |
| GET    | `/api/habits/{id}`          | Detalle de un hábito                  | Sí |
| POST   | `/api/habits`                | Crear hábito                          | Sí |
| PUT    | `/api/habits/{id}`          | Editar hábito                         | Sí |
| DELETE | `/api/habits/{id}`          | Eliminar hábito                       | Sí |
| POST   | `/api/habits/{id}/logs`     | Marcar/desmarcar cumplimiento de un día | Sí |
| GET    | `/api/habits/{id}/streak`   | Racha actual + % cumplimiento semanal | Sí |
| POST   | `/api/ai/chat/stream`       | Chat de recomendación (streaming SSE) | Sí |
| GET    | `/actuator/health`          | Health check (usado por Render)       | No |

### Claves necesarias

- **Gemini**: clave gratuita en [Google AI Studio](https://aistudio.google.com/app/apikey) → `GEMINI_API_KEY`.
- **JWT**: cualquier cadena aleatoria de al menos 32 caracteres → `JWT_SECRET`. Genera una con:
  ```powershell
  [System.Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
  ```

## PWA

La app es instalable desde el navegador (Chrome/Edge) y cachea su app shell para arrancar sin conexión:
- `frontend/public/manifest.json`: nombre, iconos (192/512 + variante maskable para Android), colores de tema a juego con el modo oscuro por defecto.
- `frontend/public/sw.js`: Service Worker con estrategia cache-first para el app shell y paso directo a red para todo lo que vaya a `/api/` (nunca se cachean datos de hábitos ni peticiones que no sean `GET`).
- El registro (`frontend/src/main.ts`) solo se activa en producción (`import.meta.env.PROD`) para no interferir con el hot-reload de `npm run dev`.

Para comprobarlo: Chrome DevTools → pestaña **Application** → **Manifest** (valida los criterios de instalabilidad) y **Service Workers** (estado del SW, útil para forzar una reinstalación con "Update on reload" mientras se depura).

## Despliegue

- **Backend**: Render, servicio Docker (`backend/Dockerfile`, build multi-stage). Health check en `/actuator/health`.
- **Frontend**: Vercel, `frontend/` como root directory, framework Vite.
- Variables de entorno de producción se configuran en el panel de cada plataforma (no en `.env`, que es solo para local).
- El Auto-Deploy del backend en Render está en modo "After CI checks pass" (espera a que el pipeline de CI esté en verde antes de desplegar).
