FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN bash gradlew bootJar --no-daemon && \
    mkdir -p /out && \
    find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /out/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app app && \
    mkdir -p /app/uploads && chown -R app:app /app
COPY --from=build --chown=app:app /out/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
