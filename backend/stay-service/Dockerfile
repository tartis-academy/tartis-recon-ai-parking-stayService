# Dockerfile canonico del proyecto: generico a proposito (usa target/*.jar y no
# nombra 'vehicle'), asi cada servicio nuevo lo copia sin editar nada.

# --- build ---
# Imagen con Maven ya dentro: mvnw usa distributionType=only-script y se
# descargaria Maven entero en cada build.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# El pom primero: si no cambia, Docker reutiliza la capa de dependencias y no
# vuelve a descargarlas en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# Boot 3.3+/4.x: 'tools extract' separa el jar en capas por volatilidad, las
# dependencias (casi nunca cambian) aparte de tu codigo (cambia en cada commit).
# Rebuilds y push al registry mas rapidos.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

# --- runtime ---
# JRE y no JDK: la imagen final no necesita compilador.
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Usuario sin privilegios: si algo escapa del proceso, no es root.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

EXPOSE 8080
# Boot 4.x: 'extract --layers' ya no genera el JarLauncher de capas anidadas
# (paquete org.springframework.boot.loader.*); el jar extraido es un jar fino
# normal con Class-Path a ./lib/*.jar, se arranca con 'java -jar' tal cual.
# El wildcard cubre el nombre real (<artifactId>-<version>.jar); 'exec' deja
# a java como PID 1 para que reciba SIGTERM directo al parar el contenedor.
# MaxRAMPercentage: el default de la JVM en contenedores pequenos es conservador.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar *.jar"]
