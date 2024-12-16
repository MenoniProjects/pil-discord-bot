FROM openjdk:17-jdk-alpine as builder
MAINTAINER wouto1997
WORKDIR application
ARG JAR_FILE=target/pil-discord-bot.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM openjdk:17-jdk-alpine
MAINTAINER wouto1997
WORKDIR application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
