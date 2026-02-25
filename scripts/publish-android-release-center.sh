#!/usr/bin/env bash
set -euo pipefail

APP_ID="local-relay-android"
PLATFORM="android"
CHANNEL="${CHANNEL:-stable}"
BASE_URL="${BASE_URL:-http://118.196.100.121:18080}"
RELEASE_ROOT="${RELEASE_ROOT:-/opt/apk-downloads/releases}"

VERSION_NAME="${VERSION_NAME:-}"
VERSION_CODE="${VERSION_CODE:-}"
SOURCE_APK="${SOURCE_APK:-/opt/apk-downloads/local-relay-android/local-relay-android-debug.apk}"
RELEASE_NOTES="${RELEASE_NOTES:-local relay release ${VERSION_NAME}}"

if [[ -z "${VERSION_NAME}" || -z "${VERSION_CODE}" ]]; then
  echo "Usage:"
  echo "  VERSION_NAME=0.3.2 VERSION_CODE=5 [SOURCE_APK=/path/to/app.apk] [RELEASE_NOTES='...'] $0"
  exit 1
fi

if [[ ! -f "${SOURCE_APK}" ]]; then
  echo "Source APK not found: ${SOURCE_APK}"
  exit 1
fi

DEST_DIR="${RELEASE_ROOT}/${APP_ID}/${PLATFORM}/${CHANNEL}/${VERSION_NAME}"
DEST_APK="${DEST_DIR}/${APP_ID}-${VERSION_NAME}.apk"
LATEST_JSON="${RELEASE_ROOT}/${APP_ID}/${PLATFORM}/${CHANNEL}/latest.json"

mkdir -p "${DEST_DIR}" "$(dirname "${LATEST_JSON}")"
cp "${SOURCE_APK}" "${DEST_APK}"

FILE_SIZE_BYTES="$(stat -c%s "${DEST_APK}")"
SHA256="$(sha256sum "${DEST_APK}" | awk '{print $1}')"
UPDATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")"
DOWNLOAD_URL="${BASE_URL}/releases/${APP_ID}/${PLATFORM}/${CHANNEL}/${VERSION_NAME}/${APP_ID}-${VERSION_NAME}.apk"

cat > "${LATEST_JSON}" <<JSON
{
  "app_id": "${APP_ID}",
  "platform": "${PLATFORM}",
  "channel": "${CHANNEL}",
  "version_code": ${VERSION_CODE},
  "version_name": "${VERSION_NAME}",
  "file_name": "${APP_ID}-${VERSION_NAME}.apk",
  "file_size_bytes": ${FILE_SIZE_BYTES},
  "sha256": "${SHA256}",
  "updated_at": "${UPDATED_AT}",
  "download_url": "${DOWNLOAD_URL}",
  "release_notes": "${RELEASE_NOTES}"
}
JSON

echo "Published: ${DEST_APK}"
echo "Updated latest.json: ${LATEST_JSON}"
echo "Download URL: ${DOWNLOAD_URL}"
