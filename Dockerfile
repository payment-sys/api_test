FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
