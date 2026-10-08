#!/bin/bash
# Firebase service account JSON'ni serverdagi backend .env ga FIREBASE_SERVICE_ACCOUNT (base64) qilib yozadi.
# Ishlatish: bash scripts/set-fcm-key.sh ~/Downloads/<firebase-adminsdk>.json
set -euo pipefail
KEY_FILE="${1:-$(ls -t ~/Downloads/*firebase-adminsdk*.json | head -1)}"
REMOTE_DIR="sites/zinfurn/backend"
K=$(base64 -i "$KEY_FILE" | tr -d '\n')
ssh freeserver "cd ~/$REMOTE_DIR && sed -i '/^FIREBASE_SERVICE_ACCOUNT=/d' .env && echo 'FIREBASE_SERVICE_ACCOUNT=$K' >> .env && docker compose -f docker-compose.prod.yml up -d --force-recreate asadbek-zinfurn-api 2>&1 | tail -2"
echo "✅ FIREBASE_SERVICE_ACCOUNT o'rnatildi"
