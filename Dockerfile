# Gradle wrapper는 빌드할 때마다 services.gradle.org에서 배포판을 받는다. 그 서버가 응답하지 않으면 이미지를 만들 수 없으므로,
# 같은 버전의 Gradle이 든 공식 이미지(Eclipse Temurin JDK 21 기반)로 빌드한다.
# gradle/wrapper/gradle-wrapper.properties의 버전을 바꾸면 이 태그도 함께 바꾼다. CI가 두 버전이 같은지 확인한다.
FROM gradle:9.7.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon && \
    mkdir -p /out && \
    find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /out/app.jar \;
# RDS 인증서를 검증할 신뢰 저장소(#133). RDS 인증서를 발급하는 AWS 전용 CA는 기본 JVM 신뢰 저장소에 없다.
# 묶음 파일은 https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem 에서 받아 저장소에 두었고,
# 바뀌면 빌드가 멈추도록 체크섬을 고정한다. AWS가 묶음을 바꾸면 파일과 체크섬을 함께 바꾼다.
# 저장소 비밀번호는 공개 인증서만 담으므로 비밀이 아니다(ECS 작업 정의의 DB_URL에 같은 값을 넣는다).
RUN echo "90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b  docker/rds/global-bundle.pem" | sha256sum -c - && \
    java docker/rds/BuildTruststore.java docker/rds/global-bundle.pem /out/rds-truststore.p12 rds-truststore

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
RUN groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app app && \
    mkdir -p /app/uploads && chown -R app:app /app
COPY --from=build --chown=app:app /out/app.jar /app/app.jar
COPY --from=build --chown=app:app /out/rds-truststore.p12 /app/rds-truststore.p12
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
