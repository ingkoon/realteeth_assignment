# 멀티 스테이지 빌드: 빌드 산출물만 경량 JRE 이미지에 담는다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 위해 빌드 스크립트/래퍼를 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 소스 복사 후 실행 가능한 boot jar 생성.
# OpenAPI 스펙은 정적 리소스(src/main/resources/static/docs/openapi3.json)로 포함돼 있으므로
# 이미지 빌드에서 테스트를 돌릴 필요가 없다(빠르고 테스트 인프라와 분리).
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/realteeth_assignment-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
