// Плагины
plugins {
    java
    application // Для запуска приложения через mainClass
}

// Базовые настройки проекта
group = "ru.spbstu.telematics.bitbotx"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_23 // Java 23

repositories {
    mavenCentral()
}

dependencies {
    // Spring Framework
    implementation("org.springframework:spring-web:6.1.3")
    implementation("org.springframework:spring-context:6.1.3")
    implementation("org.springframework:spring-webflux:6.1.3")
    implementation("org.springframework:spring-tx:6.1.3")
    implementation("org.springframework:spring-aop:6.1.3")
    implementation("org.springframework.security:spring-security-core:6.1.3")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.22.1")

    // Testing
    testImplementation("org.springframework:spring-test:6.1.3")
    testImplementation("io.projectreactor:reactor-test:3.6.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

// Настройка запуска приложения
application {
    mainClass.set("ru.spbstu.telematics.bitbotx.BitBotX") // Главный класс
}

// Конфигурация JAR (сборка fatJar)
tasks.jar {
    manifest {
        attributes["Main-Class"] = "ru.spbstu.telematics.bitbotx.BitBotX" // Точка входа
    }

    // Включаем все зависимости в JAR (fatJar)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    // Стратегия обработки дубликатов
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
} 