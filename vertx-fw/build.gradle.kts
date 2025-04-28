plugins {
  kotlin("jvm") version "1.9.20"
  id("com.diffplug.spotless") version "6.25.0"
  id("com.github.johnrengelman.shadow") version "8.1.1"
  `java-library`
}

group = "org.aikrai"
version = "1.0.0-SNAPSHOT"

val vertxVersion = "4.5.14"

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
  }
}

kotlin {
  jvmToolchain(17)
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
  api("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
  api("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
  api("io.vertx:vertx-core:$vertxVersion")
  api("io.vertx:vertx-web:$vertxVersion")
  api("io.vertx:vertx-config:$vertxVersion")
  api("io.vertx:vertx-config-yaml:$vertxVersion")
  api("io.vertx:vertx-sql-client-templates:$vertxVersion")
  api("io.vertx:vertx-auth-jwt:$vertxVersion")

  api("com.google.inject:guice:7.0.0")
  api("org.reflections:reflections:0.10.2")
  api("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  api("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

  // hutool
  api("cn.hutool:hutool-core:5.8.35")

  // log
  implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
  implementation("org.slf4j:slf4j-api:2.0.17")
  implementation("ch.qos.logback:logback-classic:1.5.18")

  // doc
  api("io.swagger.core.v3:swagger-core:2.2.27")
}
