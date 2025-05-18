plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "spbstu.mcs.telegramBot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

dependencies {
    // Telegram Bot API
    implementation("org.telegram:telegrambots:6.9.7.1")
    implementation("org.telegram:telegrambots-abilities:6.9.7.1")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.7.0")
    implementation("org.springframework.kafka:spring-kafka:3.1.3")

    // Spring Framework
    implementation("org.springframework:spring-web:6.1.3")
    implementation("org.springframework:spring-context:6.1.3")
    implementation("org.springframework:spring-webflux:6.1.3")

    // Spring Security (WebFlux support)
    implementation("org.springframework.security:spring-security-config:6.1.3")
    implementation("org.springframework.security:spring-security-web:6.1.3")
    implementation("org.springframework.security:spring-security-core:6.1.3")

    //Modulith
    implementation("org.springframework.modulith:spring-modulith-core:1.1.3")
    implementation("org.springframework.modulith:spring-modulith-docs:1.3.1") // для генерации документации

    // Spring Data MongoDB
    implementation("org.springframework.data:spring-data-mongodb:4.2.0")
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    // Reactor (required for WebFlux)
    implementation("io.projectreactor:reactor-core:3.6.3")

    // Configuration
    implementation("org.yaml:snakeyaml:2.2")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("ch.qos.logback:logback-core:1.4.14")

    // Spring Vault
    implementation("org.springframework.vault:spring-vault-core:3.1.2")
    implementation("org.springframework:spring-context:6.1.6")
    implementation("org.springframework:spring-web:6.1.6")

    implementation("io.projectreactor:reactor-core:3.4.0")

    // Testing - добавляем явно на все конфигурации для тестов
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.12.4")
    testImplementation("io.projectreactor:reactor-test:3.4.0")
    
   
}

application {
    mainClass.set("spbstu.mcs.telegramBot.Application")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "23"
    targetCompatibility = "23"
}

val jarBaseName = "app"

tasks.withType<Jar> {
    archiveBaseName.set(jarBaseName)
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.distZip {
    isEnabled = false
}
tasks.distTar {
    isEnabled = false
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("crypto-telegram-bot")
    archiveVersion.set("1.0-SNAPSHOT")
    archiveClassifier.set("fat")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it).matching {
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/Log4j2Plugins.dat")
        }
    })
    with(tasks.jar.get() as CopySpec)
}

tasks.named("build") {
    dependsOn("fatJar")
}

tasks.named<Jar>("jar") {
    enabled = false
}