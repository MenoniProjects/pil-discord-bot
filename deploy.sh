#!/bin/sh

docker build --build-arg JAR_FILE=target/pil-discord-bot.jar -t ghcr.io/menoniprojects/pil-discord:dev .
docker push ghcr.io/menoniprojects/pil-discord:dev
