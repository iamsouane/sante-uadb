# syntax=docker/dockerfile:1
# Image unique pour les cinq services Java : docker build --build-arg MODULE=catalogue-service .
FROM maven:3.9-eclipse-temurin-21-alpine AS construction
WORKDIR /src
COPY . .
# Le cache Maven survit aux builds : une coupure réseau ne fait pas tout retélécharger.
# « locked » : les cinq images partagent cette étape, jamais deux Maven sur le même cache.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B package -DskipTests -Daether.connector.http.retryHandler.count=6

FROM eclipse-temurin:21-jre-alpine AS couches
ARG MODULE
WORKDIR /app
COPY --from=construction /src/${MODULE}/target/${MODULE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination couches

FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=couches /app/couches/dependencies/ ./
COPY --from=couches /app/couches/spring-boot-loader/ ./
COPY --from=couches /app/couches/snapshot-dependencies/ ./
COPY --from=couches /app/couches/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
# Vivacité seulement : un conteneur n'est pas « unhealthy » parce que sa base est coupée
HEALTHCHECK --interval=5s --timeout=3s --start-period=90s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT_SANTE:-8080}${CHEMIN_SANTE:-/actuator/health/liveness}" >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
