# ============================================================================
# DocRecordBE — imagen de contenedor
#
# Build:
#   docker build -t docrecordbe .
#
# Run (todo lo que no sea un default de DESARROLLO se pasa por -e; ver
# src/main/resources/application.properties para la lista completa de
# variables que el jar entiende):
#   docker run -p 8080:8080 \
#     -e DB_HOST=... -e DB_PORT=5432 -e DB_NAME=... -e DB_USER=... -e DB_PASSWORD=... \
#     -e MAIL_PASSWORD=... -e JWT_SECRET=... \
#     docrecordbe
#
# Esta imagen NO trae ningun valor de aplicacion de produccion cableado, ni
# siquiera "solo para desarrollo": esos defaults ya viven en
# application.properties (patron ${VAR:default}) y se documentan alli. Aqui
# no se fija ningun ENV de aplicacion.
# ============================================================================

# ----------------------------------------------------------------------------
# Etapa 1: build. JDK completo (no solo JRE) porque hace falta compilar. Se
# usa el wrapper (./mvnw) en vez de asumir un Maven instalado en la imagen.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Primero el wrapper y el pom.xml, nada mas: mientras esos dos no cambien,
# Docker reutiliza esta capa (con las dependencias ya descargadas por
# go-offline) aunque el codigo fuente si cambie en el siguiente build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Recien ahora el codigo fuente, y se empaqueta el jar. Las pruebas ya
# corrieron en CI (.github/workflows/ci.yml); no hace falta repetirlas aqui.
COPY src/ src/
RUN ./mvnw -B -DskipTests package

# ----------------------------------------------------------------------------
# Etapa 2: runtime. SOLO JRE — sin JDK, sin Maven, sin ninguna herramienta de
# compilacion. Lo unico que entra de la etapa de build es el jar ya armado.
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Usuario de sistema dedicado: la aplicacion NO corre como root. Sin home,
# sin shell de login: solo existe para ser dueno del proceso de la JVM.
RUN groupadd --system docrecord \
    && useradd --system --gid docrecord --no-create-home --shell /usr/sbin/nologin docrecord

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown docrecord:docrecord app.jar

USER docrecord
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
