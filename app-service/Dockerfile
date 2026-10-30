# build context 為 repo 根目錄（見 docker-compose.yml），以便同時存取 frontend/ 與 app-service/
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# LINE Login channel ID 為公開資訊，於建置時內嵌；未提供則登入頁不顯示 LINE 登入按鈕（Demo 體驗不受影響）
ARG VITE_LINE_CHANNEL_ID
RUN npm run build

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY app-service/.mvn .mvn
COPY app-service/mvnw app-service/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY app-service/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/app-service.jar app.jar
EXPOSE 8080
# 預設為正式環境設定：機密與連線資訊須由環境變數提供，缺少時啟動失敗（docker-compose 會覆寫為 dev）
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Taipei"
# 非 root 執行，並以 actuator health 供容器平台判斷存活
RUN addgroup -S app && adduser -S app -G app
USER app
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
