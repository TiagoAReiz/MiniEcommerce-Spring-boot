# ---------------------------------------------------------------- build
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /build

# The wrapper and the POM come first, on their own layer. Dependency resolution is the
# expensive step, and copying src/ before it would redo the whole download on every code
# change.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/

# Tests need Postgres, Redis and MinIO, none of which exist during an image build. They run
# in CI against real services instead.
RUN ./mvnw -B clean package -DskipTests

# The jar name carries the project version, which would make the runtime stage depend on it.
RUN cp target/*.jar /build/app.jar

# ---------------------------------------------------------------- runtime
# Alpine over the Ubuntu variant: roughly 220 MB smaller, and busybox already provides the
# wget the healthcheck needs, so nothing has to be installed on top.
FROM eclipse-temurin:17-jre-alpine AS runtime

# A compromised process should not be able to write anywhere that matters, so nothing here
# runs as root.
RUN addgroup --system spring && adduser --system --ingroup spring spring

WORKDIR /app
COPY --from=build --chown=spring:spring /build/app.jar app.jar

USER spring

# A percentage rather than a fixed -Xmx, so the heap follows whatever memory limit the
# container is given instead of being wrong on every host that is not this one.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8080

# exec keeps the JVM as PID 1, so it receives SIGTERM and shuts down gracefully rather than
# being killed after the stop timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
