db = db.getSiblingDB('BitBotDB');

db.createUser({
  user: "appuser",
  pwd: "apppassword",
  roles: [
    { role: "readWrite", db: "BitBotDB" },
    { role: "dbAdmin", db: "BitBotDB" }
  ]
});

// Инициализация коллекций
db.createCollection('TrackedCryptoCurrencies');
db.createCollection('notifications');
db.createCollection('portfolios');
db.createCollection('users');
