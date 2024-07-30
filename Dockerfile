FROM openjdk:17-alpine
MAINTAINER isKONSTANTIN <me@knst.su>

WORKDIR /finwave-bot

COPY ./FinWave-Bot.jar ./

ENTRYPOINT exec java $JAVA_OPTS -jar FinWave-Bot.jar