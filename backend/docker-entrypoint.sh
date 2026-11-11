#!/bin/sh
set -eu

if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
    # Render provides a PostgreSQL URI with credentials. JDBC expects the
    # credentials separately, which the Blueprint supplies via env vars.
    database_location="${DATABASE_URL#*://}"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${database_location#*@}"
fi

exec java -jar /app/app.jar
