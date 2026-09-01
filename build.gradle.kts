plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.yunovan"
version = "0.1.0-SNAPSHOT"
description = "AI Advent: Java + Spring Boot agents challenge"

java {
    // Bytecode 21, without pinning a JDK 21 toolchain.
    // IDEA with JDK 25 (or any 21+) can import and run the project.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
