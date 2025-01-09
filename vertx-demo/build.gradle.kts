plugins {
  application
  kotlin("jvm") version "1.9.20"
  id("com.diffplug.spotless") version "6.25.0"
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.demo"
version = "1.0.0-SNAPSHOT"

val vertxVersion = "4.5.11"

application {
  mainClass.set("app.Application")
  applicationDefaultJvmArgs = listOf(
    "--add-opens",
    "java.base/java.lang=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.time=ALL-UNNAMED"
    // "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
  )
}

repositories {
  mavenLocal()
  mavenCentral()
}

tasks.shadowJar {
  archiveClassifier.set("fat")
  mergeServiceFiles()
}

tasks.test {
  useJUnitPlatform()
  testLogging {
    showStandardStreams = true
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
//    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.compileKotlin {
  kotlinOptions {
    jvmTarget = "17"
  }
}

tasks.compileTestKotlin {
  kotlinOptions {
    jvmTarget = "17"
  }
}

spotless {
  kotlin {
    ktlint()
      .editorConfigOverride(
        mapOf(
          "ktlint_standard_no-wildcard-imports" to "disabled",
          "ktlint_standard_trailing-comma-on-call-site" to "disabled",
          "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
          "indent_size" to "2"
        )
      )
    target("src/**/*.kt")
  }
}

dependencies {
  implementation(project(":vertx-fw"))
  //  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
//  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
  implementation("io.vertx:vertx-core:$vertxVersion")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-web-client:$vertxVersion")
  implementation("io.vertx:vertx-config:$vertxVersion")
  implementation("io.vertx:vertx-config-yaml:$vertxVersion")
  implementation("io.vertx:vertx-pg-client:$vertxVersion")
  implementation("io.vertx:vertx-mysql-client:$vertxVersion")
  implementation("io.vertx:vertx-sql-client-templates:$vertxVersion")
  implementation("io.vertx:vertx-auth-jwt:$vertxVersion")

  implementation("com.google.inject:guice:5.1.0")
  implementation("org.reflections:reflections:0.9.12")
  implementation("cn.hutool:hutool-all:5.8.24")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
//  implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
  implementation("dev.hsbrysk:caffeine-coroutines:1.0.0")

  // log
  implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
  implementation("org.slf4j:slf4j-api:2.0.6")
  implementation("ch.qos.logback:logback-classic:1.4.14")
  implementation("org.codehaus.janino:janino:3.1.8")

  // jpa
  implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
  implementation("jakarta.validation:jakarta.validation-api:3.1.0")

//  implementation("com.mysql:mysql-connector-j:9.1.0")
  implementation("mysql:mysql-connector-java:5.1.49")
  // doc
  implementation("io.swagger.core.v3:swagger-core:2.2.27")

  testImplementation("io.vertx:vertx-junit5:$vertxVersion")

  implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}
