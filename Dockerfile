# Используем официальный образ Azul Zulu для Java 23
FROM azul/zulu-openjdk:23 AS build

# Установка Gradle
RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-8.6-bin.zip && \
    unzip gradle-8.6-bin.zip -d /opt && \
    rm gradle-8.6-bin.zip

ENV GRADLE_HOME=/opt/gradle-8.6
ENV PATH=$PATH:$GRADLE_HOME/bin

# Копируем только необходимые файлы для кэширования
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY src ./src

# Собираем проект
RUN gradle build --no-daemon

# Финальный образ
FROM azul/zulu-openjdk:23-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
COPY target/your-app.jar app.jar
COPY src/main/resources/application.properties .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]