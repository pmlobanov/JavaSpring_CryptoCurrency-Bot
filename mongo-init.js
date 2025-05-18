db = db.getSiblingDB('BitBotDB');

db.createUser({
  user: "Robert_Polson",
  pwd: "here123",
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
db.admins.createIndex({ "username": 1 }, { unique: true });

