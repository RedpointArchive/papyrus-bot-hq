FROM maven:3.6.1-jdk-8 AS build

ADD . /app
WORKDIR /app
RUN mvn compile assembly:single
RUN ls -la /app/target

FROM openjdk:8u212-b04-jre-stretch

ENV MINECRAFT_SERVER_HOST=127.0.0.1
ENV MINECRAFT_SERVER_PORT=19132
ENV BOT_TICK_RATE=500

EXPOSE 8080
WORKDIR /app
COPY --from=build /app/target/papyruschatmonitor-1.0-SNAPSHOT-jar-with-dependencies.jar /app/papyrusbot.jar
ENTRYPOINT [ "java", "-jar", "/app/papyrusbot.jar" ]

# FROM maven:3.6.1-jdk-8
# 
# WORKDIR /app
# ADD pom.xml /app/pom.xml
# ADD deps /app/deps
# RUN mvn dependency:go-offline
# ADD src /app/src
# RUN mvn package
# 
# ENV MINECRAFT_SERVER_HOST=127.0.0.1
# ENV MINECRAFT_SERVER_PORT=19132
# 
# EXPOSE 8080
# ENTRYPOINT [ "mvn", "exec:java", "-Dexec.mainClass=games.redpoint.App" ]