FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/md-collector-binance*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8888
