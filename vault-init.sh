#!/bin/sh

# Ждать, пока Vault не станет доступен
echo "Проверка доступности Vault (http://vault:8200)..."
export VAULT_ADDR="http://vault:8200"
export VAULT_TOKEN="root"

until vault status > /dev/null 2>&1; do
  echo "Ожидание запуска Vault..."
  sleep 2
done
echo "Vault доступен!"

echo "Авторизация в Vault..."
vault login $VAULT_TOKEN
echo "Авторизация успешна!"

echo "Сохранение секретов в Vault..."
vault kv put secret/crypto-bot \
  telegram.bot.token="YOUR_TELEGRAM_BOT_TOKEN" \
  telegram.bot.username="YOUR_TELEGRAM_BOT_USERNAME" \
  kafka.bootstrap-servers="kafka:9092" \
  kafka.consumer.group-id="telegram-bot-group" \
  kafka.topics.incoming="telegram-incoming-messages" \
  kafka.topics.outgoing="telegram-outgoing-messages" \
  mongodb.connection-string="YOUR_MONGODB_CONNECTION_STRING" \
  mongodb.database="YOUR_MONGODB_DATABASE" \
  bingx.api.key="YOUR_BINGX_API_KEY" \
  bingx.api.secret="YOUR_BINGX_API_SECRET" \
  bingx.api.url="YOUR_BINGX_API_URL" \
  currency.api.url="YOUR_CURRENCY_API_URL"

echo "Секреты успешно сохранены в Vault!"
echo "Инициализация завершена, продолжаем запуск приложения..." 