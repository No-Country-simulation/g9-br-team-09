# syntax=docker/dockerfile:1

# ---------------------------------------------------------
# Stage 1: resolução e cache das dependências
# ---------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS dependencies

WORKDIR /build

COPY --chmod=0755 mvnw ./mvnw
COPY .mvn/ .mvn/
COPY pom.xml ./pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline -DskipTests


# ---------------------------------------------------------
# Stage 2: build da aplicação
# ---------------------------------------------------------
FROM dependencies AS build

WORKDIR /build

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp clean package -DskipTests \
    && mv \
        "target/$(./mvnw help:evaluate \
            -Dexpression=project.artifactId \
            -q \
            -DforceStdout)-$(./mvnw help:evaluate \
            -Dexpression=project.version \
            -q \
            -DforceStdout).jar" \
        target/app.jar


# ---------------------------------------------------------
# Stage 3: extração das camadas do Spring Boot
# ---------------------------------------------------------
FROM build AS extract

WORKDIR /build

RUN java \
    -Djarmode=tools \
    -jar target/app.jar \
    extract \
    --layers \
    --destination target/extracted


# ---------------------------------------------------------
# Stage 4: imagem final de runtime
# ---------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ARG UID=10001

RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser

COPY --from=extract --chown=appuser:appuser \
    /build/target/extracted/dependencies/ ./

COPY --from=extract --chown=appuser:appuser \
    /build/target/extracted/spring-boot-loader/ ./

COPY --from=extract --chown=appuser:appuser \
    /build/target/extracted/snapshot-dependencies/ ./

COPY --from=extract --chown=appuser:appuser \
    /build/target/extracted/application/ ./

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]