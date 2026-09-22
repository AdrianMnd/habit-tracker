# --- Etapa 1: build ---
# Imagen con Maven + JDK 21 solo para compilar; no viaja a produccion.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos primero solo el pom.xml y descargamos dependencias: mientras no
# cambien las dependencias, Docker reutiliza esta capa en cache en builds
# posteriores, aunque cambie el codigo fuente (mas rapido en cada deploy).
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# --- Etapa 2: runtime ---
# Imagen final: solo JRE (no JDK completo) + el jar ya compilado.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/habit-tracker-0.1.0.jar app.jar

# Render (y la mayoria de PaaS) inyectan la variable PORT en tiempo de
# ejecucion; application.properties ya la lee via server.port=${PORT:8080}.
ENTRYPOINT ["java", "-jar", "app.jar"]
