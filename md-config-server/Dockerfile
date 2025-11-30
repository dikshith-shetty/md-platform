FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app
COPY target/md-config-server-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8888
