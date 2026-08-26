#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
WAR_PATH="$APP_DIR/target/billsoft-0.0.1-SNAPSHOT.war"

if [ ! -f "$WAR_PATH" ]; then
  "$APP_DIR/mvnw" -DskipTests package
fi

exec java -jar "$WAR_PATH"
