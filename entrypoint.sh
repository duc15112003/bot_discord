#!/bin/bash
set -e

if [ -z "$SPRING_PROFILES_ACTIVE" ]; then
    echo "SPRING_PROFILES_ACTIVE environment variable is not set"
    exit 1
fi

exec java -jar app.jar --spring.profiles.active=prd
