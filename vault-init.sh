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
  telegram.bot.token= \
  telegram.bot.username= \
  kafka.bootstrap-servers=\
  kafka.consumer.group-id= \
  kafka.topics.incoming= \
  kafka.topics.outgoing=\
  mongodb.connection-string= \
  mongodb.database= \
  bingx.api.key= \
  bingx.api.secret= \
  bingx.api.url= \
  currency.api.url=

echo "Секреты успешно сохранены в Vault!"
echo "Инициализация завершена, продолжаем запуск приложения..." 