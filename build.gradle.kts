plugins {
    id("java")
}

group = "ru.spbstu.telematics.java"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    // Spring Context (версия Spring 6.x)
    implementation("org.springframework:spring-context:6.1.6")

    // Spring Data MongoDB (актуальная версия)
    implementation("org.springframework.data:spring-data-mongodb:4.2.0")

    // MongoDB Java Driver (совместимый с Spring Data 4.x)
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.springframework:spring-test:2.5")
}
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

