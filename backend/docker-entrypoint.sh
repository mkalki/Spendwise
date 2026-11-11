#!/bin/sh
set -eu

if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
    export SPRING_DATASOURCE_URL="jdbc:${DATABASE_URL}"
fi

exec java -jar /app/app.jar
