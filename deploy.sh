#!/bin/sh

docker build --build-arg JAR_FILE=target/pil-discord-bot.jar -t ghcr.io/wouterg/pil-discord:dev .
docker push ghcr.io/wouterg/pil-discord:dev
