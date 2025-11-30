FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/md-analytics-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080
