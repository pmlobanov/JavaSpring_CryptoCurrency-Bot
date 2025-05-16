db = db.getSiblingDB('BitBotDB');

db.createUser({
  user: "appuser",
  pwd: "apppassword",
  roles: [
    { role: "readWrite", db: "BitBotDB" },
    { role: "dbAdmin", db: "BitBotDB" }
  ]
});


db.createCollection('notifications');
db.createCollection('portfolios');
db.createCollection('users');
db.createCollection('admins');

// Создаем индексы для быстрого поиска
db.users.createIndex({ "telegramId": 1 }, { unique: true });
db.admins.createIndex({ "username": 1 }, { unique: true });

