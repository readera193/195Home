# build context 為 repo 根目錄（見 docker-compose.yml），以便同時存取 frontend/ 與 app-service/
FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY app-service/pom.xml ./
RUN mvn -B dependency:go-offline
COPY app-service/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/app-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
