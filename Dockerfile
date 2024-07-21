FROM openjdk:17-alpine
MAINTAINER isKONSTANTIN <me@knst.su>

WORKDIR /finwave-bot

COPY ./FinWave-Bot.jar ./

ENTRYPOINT ["java", "-jar", "FinWave-Bot.jar"]