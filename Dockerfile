FROM maven:3.6.1-jdk-8 AS build

ADD . /app
WORKDIR /app
RUN mvn compile assembly:single
RUN ls -la /app/target

FROM openjdk:8u212-b04-jre-stretch

ENV MINECRAFT_SERVER_HOST=127.0.0.1
ENV MINECRAFT_SERVER_PORT=19132

EXPOSE 8080
WORKDIR /app
COPY --from=build /app/target/papyruschatmonitor-1.0-SNAPSHOT-jar-with-dependencies.jar /app/papyrusbot.jar
COPY --from=build /app/src/main/resources/log4j.properties /app/log4j.properties
ENTRYPOINT [ "java", "-jar", "/app/papyrusbot.jar" ]