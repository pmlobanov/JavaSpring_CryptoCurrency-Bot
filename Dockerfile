# Используем официальный образ Java 23
FROM eclipse-temurin:23-jre-alpine

# Устанавливаем рабочую директорию
WORKDIR /app

# Создаем директорию для логов и устанавливаем права
RUN mkdir -p /app/logs && chmod 777 /app/logs

# Копируем только JAR файл
COPY build/libs/*.jar app.jar

# Оптимизируем JVM для контейнера
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Запускаем приложение
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]