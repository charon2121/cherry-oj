FROM eclipse-temurin:21-jre
WORKDIR /app
ARG SERVICE
# Maven's jar exclusion removes private local resources before this copy.
COPY ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar /app/application.jar
RUN mkdir -p /app/data /app/logs && chown -R 10001:10001 /app
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
