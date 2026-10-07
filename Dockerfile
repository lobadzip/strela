# Server and web app in one image. The Android app is not built here: the web build and the
# server need only a JDK (Kotlin fetches its own Node for webpack).

# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon --quiet :composeApp:wasmJsBrowserDistribution :server:installDist

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home strela && mkdir /data && chown strela /data
COPY --from=build --chown=strela:strela /app/server/build/install/server ./
COPY --from=build --chown=strela:strela /app/composeApp/build/dist/wasmJs/productionExecutable ./web

ENV PORT=8080 \
    STRELA_WEB_DIR=/app/web \
    STRELA_DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
VOLUME /data
USER strela

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && echo -e "GET /health HTTP/1.0\n\n" >&3 && head -1 <&3 | grep -q 200'

ENTRYPOINT ["./bin/server"]
