#!/usr/bin/env bash
# Install the Adricore SMS API on Ubuntu (systemd + OpenJDK 17).
# Run from the folder that contains this script, the JAR, and .env.example:
#   sudo ./install-ubuntu.sh
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="/opt/adricore-sms"
ENV_DIR="/etc/adricore-sms"
ENV_FILE="${ENV_DIR}/api.env"
SERVICE_NAME="adricore-sms-api"
APP_USER="adricore"
JAR_NAME="adricore-sms-gateway.jar"

find_jar() {
  if [[ -f "${SCRIPT_DIR}/${JAR_NAME}" ]]; then
    echo "${SCRIPT_DIR}/${JAR_NAME}"
    return
  fi
  if [[ -f "${SCRIPT_DIR}/../target/${JAR_NAME}" ]]; then
    echo "${SCRIPT_DIR}/../target/${JAR_NAME}"
    return
  fi
  echo "JAR not found. Expected ${SCRIPT_DIR}/${JAR_NAME}"
  echo "Build on your machine with: mvn -DskipTests package"
  echo "Then copy target/${JAR_NAME} next to this script."
  exit 1
}

echo "Installing OpenJDK 17 if needed..."
if ! command -v java >/dev/null 2>&1; then
  apt-get update -y
  DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless
fi
java -version

if ! id -u "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --home "${APP_DIR}" --shell /usr/sbin/nologin "${APP_USER}"
  echo "Created system user ${APP_USER}"
fi

mkdir -p "${APP_DIR}" "${ENV_DIR}"
cp -f "$(find_jar)" "${APP_DIR}/${JAR_NAME}"
cp -f "${SCRIPT_DIR}/run-api.sh" "${APP_DIR}/run-api.sh"
chmod 755 "${APP_DIR}/run-api.sh"
chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${SCRIPT_DIR}/.env.example" "${ENV_FILE}"
  echo "Created ${ENV_FILE} — edit DB_USERNAME and DB_PASSWORD before starting."
else
  echo "Keeping existing ${ENV_FILE}"
fi
chmod 640 "${ENV_FILE}"
chown root:"${APP_USER}" "${ENV_FILE}"

cp -f "${SCRIPT_DIR}/${SERVICE_NAME}.service" "/etc/systemd/system/${SERVICE_NAME}.service"
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"

echo
echo "Installed."
echo "  1. Edit database settings:  sudo nano ${ENV_FILE}"
echo "  2. Start the API:           sudo systemctl start ${SERVICE_NAME}"
echo "  3. Check status:            sudo systemctl status ${SERVICE_NAME}"
echo "  4. Follow logs:             sudo journalctl -u ${SERVICE_NAME} -f"
echo
echo "Keep port 8080 off the public internet; nginx should proxy https://sms.adricore.co.ug/api/"
