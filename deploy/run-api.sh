#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
PID_FILE="${PID_FILE:-$ROOT/adricore-sms-api.pid}"
LOG_DIR="${LOG_DIR:-$ROOT/logs}"
JAR="${JAR_PATH:-$ROOT/adricore-sms-gateway.jar}"

usage() {
  cat <<'EOF'
Usage: run-api.sh <start|stop|restart|status>

Reads database and runtime settings from deploy/.env (or $ENV_FILE).

  start    Start the API in the background
  stop     Stop the running API
  restart  Stop then start
  status   Show whether the API is running
EOF
}

load_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "Missing $ENV_FILE"
    echo "Copy deploy/.env.example to deploy/.env and set DB_USERNAME / DB_PASSWORD."
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  : "${DB_HOST:=127.0.0.1}"
  : "${DB_PORT:=5432}"
  : "${DB_NAME:=adricore_messaging}"
  : "${SERVER_PORT:=8080}"
  : "${SPRING_PROFILES_ACTIVE:=prod}"
  : "${JAVA_OPTS:=-Xms256m -Xmx512m}"
  : "${APP_PORTAL_URL:=https://sms.adricore.co.ug}"

  if [[ -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "DB_USERNAME and DB_PASSWORD must be set in $ENV_FILE"
    exit 1
  fi

  export SPRING_PROFILES_ACTIVE
  export SERVER_PORT
  export DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD
  export DB_URL="${DB_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}}"
  export SPRING_DATASOURCE_URL="$DB_URL"
  export SPRING_DATASOURCE_USERNAME="$DB_USERNAME"
  export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
  export APP_PORTAL_URL
}

is_running() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE")"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "$pid"
      return 0
    fi
    rm -f "$PID_FILE"
  fi
  return 1
}

cmd_start() {
  load_env
  if pid="$(is_running)"; then
    echo "Already running (pid $pid)"
    exit 0
  fi
  if [[ ! -f "$JAR" ]]; then
    echo "JAR not found: $JAR"
    echo "Build with: mvn -DskipTests package && cp target/adricore-sms-gateway.jar deploy/"
    exit 1
  fi
  mkdir -p "$LOG_DIR"
  nohup java $JAVA_OPTS -jar "$JAR" \
    --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
    --server.port="$SERVER_PORT" \
    >>"$LOG_DIR/api.log" 2>&1 &
  echo $! >"$PID_FILE"
  echo "Started Adricore SMS API (pid $(cat "$PID_FILE")) on port $SERVER_PORT"
  echo "Logs: $LOG_DIR/api.log"
}

cmd_stop() {
  if pid="$(is_running)"; then
    kill "$pid"
    for _ in $(seq 1 20); do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      sleep 0.25
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid"
    fi
    rm -f "$PID_FILE"
    echo "Stopped pid $pid"
  else
    echo "Not running"
  fi
}

cmd_status() {
  if pid="$(is_running)"; then
    echo "Running (pid $pid)"
  else
    echo "Not running"
    exit 1
  fi
}

case "${1:-}" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status) cmd_status ;;
  *) usage; exit 1 ;;
esac
