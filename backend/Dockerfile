# バックエンド（Spring Boot）のコンテナイメージ。本番は ECS Fargate で動かす（docs/デプロイ.md 3章）。
#   作り方：cd backend && docker build -t raisetime-backend .
# 1段目で jar を作り、2段目には jar と実行に要るもの（JRE）だけを入れる（イメージを小さくし、ビルドの道具を本番に持ち込まない）

# ---------- 1段目：jar を作る ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 先に Gradle の設定だけをコピーして依存を取ってくる（ソースだけを変えたときに、この段を作り直さずに済む）
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null

COPY config config
COPY src src
# テストは CI で済んでいるので、ここでは jar を作るだけ。
# bootJar だけを動かすので、build/libs には依存を含んだ jar が1つだけできる（-plain.jar は jar タスクが作るもので、ここでは作られない）
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && cp build/libs/*.jar app.jar

# ---------- 2段目：動かす ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# root で動かさない（アプリが乗っ取られたときに、できることを減らす）
RUN groupadd --system raisetime && useradd --system --gid raisetime --no-create-home raisetime
COPY --from=build --chown=raisetime:raisetime /workspace/app.jar app.jar
USER raisetime

# アプリの標準は 8081（手元の Taskmanagement と重ならないため）。コンテナでは ALB に合わせて 8080 で待つ
ENV SERVER_PORT=8080
EXPOSE 8080
# JVM が使うメモリを、コンテナに割り当てたメモリ（タスクのメモリ）の 75% までにする
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
