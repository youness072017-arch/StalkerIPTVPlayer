#!/usr/bin/env sh
gradle wrapper --gradle-version 8.2
exec ./gradlew "$@"
