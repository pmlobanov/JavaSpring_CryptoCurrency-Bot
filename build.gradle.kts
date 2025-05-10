plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1" // Плагин для fat-JAR
}

group = "spbstu.mcs.telegramBot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Telegram Bot API
    implementation("org.telegram:telegrambots:6.9.7.1")
    implementation("org.telegram:telegrambots-abilities:6.9.7.1")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.7.0")
    implementation("org.springframework.kafka:spring-kafka:3.1.3")

    // Spring Framework (minimal required)
    implementation("org.springframework:spring-web:6.1.3")
    implementation("org.springframework:spring-context:6.1.3")
    implementation("org.springframework:spring-webflux:6.1.3")

    // Spring Data MongoDB
    implementation("org.springframework.data:spring-data-mongodb:4.2.0")
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    // Configuration
    implementation("org.yaml:snakeyaml:2.2")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Testing
    testImplementation("org.springframework:spring-test:6.1.3")
    testImplementation("io.projectreactor:reactor-test:3.6.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

application {
    mainClass.set("spbstu.mcs.telegramBot.Application")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "spbstu.mcs.telegramBot.Application"
    }
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}