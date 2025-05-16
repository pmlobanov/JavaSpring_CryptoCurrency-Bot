FROM azul/zulu-openjdk:23 as build
WORKDIR /app

# Копируем только необходимые файлы для кэширования
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradlew.bat ./
COPY src ./src

# Make gradlew executable
RUN chmod +x ./gradlew

# The application's jar file
ARG JAR_TASK=fatJar
RUN ./gradlew $JAR_TASK --no-daemon

FROM azul/zulu-openjdk:23
WORKDIR /app

# Install required packages only if they are not already installed
RUN apt-get update && \
    # Check if curl and gpg are installed, install if not
    if ! command -v curl > /dev/null || ! command -v gpg > /dev/null; then \
        apt-get install -y curl gpg; \
    fi && \
    # Install diagnostic tools
    apt-get install -y netcat-openbsd iputils-ping && \
    # Install MongoDB client
    curl -fsSL https://pgp.mongodb.com/server-6.0.asc | gpg -o /usr/share/keyrings/mongodb-server-6.0.gpg --dearmor && \
    echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-6.0.gpg] http://repo.mongodb.org/apt/debian bullseye/mongodb-org/6.0 main" | tee /etc/apt/sources.list.d/mongodb-org-6.0.list && \
    apt-get update && \
    apt-get install -y mongodb-mongosh && \
    # Check if vault is installed, install if not
    if ! command -v vault > /dev/null; then \
        # Check if unzip is installed, install if not
        if ! command -v unzip > /dev/null; then \
            apt-get install -y unzip; \
        fi && \
        curl -fsSL https://hashicorp-releases.yandexcloud.net/vault/1.15.5/vault_1.15.5_linux_amd64.zip -o vault.zip && \
        unzip vault.zip && \
        mv vault /usr/local/bin/ && \
        rm vault.zip; \
    fi && \
    # Clean up
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/crypto-telegram-bot-1.0-SNAPSHOT-fat.jar /app.jar
COPY vault-init.sh ./

# HTTP port
EXPOSE 8080
# Make vault-init.sh executable
RUN chmod +x ./vault-init.sh

ENTRYPOINT ["sh", "-c", "./vault-init.sh && java -jar /app.jar"]