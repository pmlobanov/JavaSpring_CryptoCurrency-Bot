#!/bin/sh

# Ждать, пока Vault не станет доступен
echo "Проверка доступности Vault (http://vault:8200)..."
until curl -s http://vault:8200/v1/sys/health > /dev/null; do
  echo "Ожидание запуска Vault..."
  sleep 2
done
echo "Vault доступен!"

# Экспортировать переменные окружения для vault CLI
export VAULT_ADDR="http://vault:8200"
export VAULT_TOKEN="root"

echo "Авторизация в Vault..."
vault login $VAULT_TOKEN
echo "Авторизация успешна!"

# Проверим подключение к MongoDB
echo "Проверка доступности MongoDB (mongodb:27017)..."
MAX_ATTEMPTS=3
COUNTER=0
while ! nc -z mongodb 27017; do
  COUNTER=$((COUNTER+1))
  if [ $COUNTER -ge $MAX_ATTEMPTS ]; then
    echo "ОШИБКА: MongoDB недоступен после $MAX_ATTEMPTS попыток. Проверьте настройки."
    exit 1
  fi
  echo "Ожидание MongoDB... ($COUNTER/$MAX_ATTEMPTS)"
  sleep 2
done
echo "MongoDB доступен!"

# Дополнительная пауза для полной инициализации MongoDB
echo "Ожидание полной инициализации MongoDB..."
sleep 5
echo "Продолжаем инициализацию..."

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

# Проверим содержимое секретов в Vault для отладки
echo "Проверка содержимого секретов в Vault (для отладки):"
vault kv get -format=json secret/crypto-bot | grep mongodb

# Диагностика сетевого подключения
echo "Диагностика сетевого подключения:"
echo "ping mongodb:"
ping -c 2 mongodb
echo "ping kafka:"
ping -c 2 kafka
echo "ping vault:"
ping -c 2 vault

# Проверяем доступность MongoDB с учетными данными
echo "Проверка доступа к MongoDB с учетными данными..."
mongosh --host mongodb:27017 -u appuser -p apppassword --authenticationDatabase BitBotDB --eval "db.adminCommand('ping')" || echo "Не удалось подключиться к MongoDB с учетными данными"

echo "Инициализация завершена, продолжаем запуск приложения..." 