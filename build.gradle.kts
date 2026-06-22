import org.gradle.plugin.compatibility.compatibility

plugins {
  `kotlin-dsl`
  `maven-publish`
  jacoco
  alias(libs.plugins.pluginPublish)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
}

group = "io.github.woolph.git-version-check"

version = "0.1.0"

gradlePlugin {
  website.set("https://github.com/woolph/git-version-check-gradle-plugin")
  vcsUrl.set("https://github.com/woolph/git-version-check-gradle-plugin")
  plugins {
    create("git-version-check") {
      id = "io.github.woolph.git-version-check"
      implementationClass = "io.github.woolph.gradle.GitVersionCheckPlugin"
      displayName = "Git Version Check"
      description =
          "Adds a verification task to check whether the version set in the project matches up with the supposed version according to the git commit history."
      tags.set(listOf("verification", "ci"))

      compatibility { // extension added by the Compatibility plugin
        features {
          configurationCache = true // or false if the feature isn't supported
        }
      }
    }
  }
}

repositories {
  maven(url = "https://plugins.gradle.org/m2/")
  mavenCentral()
}

dependencies {
  implementation(libs.jgit)
  implementation(libs.semver4j)

  // region unit test dependencies
  testImplementation(gradleTestKit())
  testImplementation(platform(libs.test.junit.bom))
  testImplementation(libs.test.junit.params)
  testRuntimeOnly(libs.test.junit.engine)
  testRuntimeOnly(libs.test.junit.launcher)
  // endregion
}

kotlin { jvmToolchain(libs.versions.jvmTarget.map { it.toInt() }.get()) }

java {
  withSourcesJar()
  withJavadocJar()
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
  systemProperty(
      "SUPPORTED_GRADLE_VERSION",
      listOf(
              "9.6.0",
              "9.5.1",
              "9.4.1",
              "9.3.1",
              "9.2.1",
              "9.1.0",
              "9.0.0",
              "8.14.5",
              //    "7.6.6", // 7.6.6 seems to be incompatible due to kotlin.protobuf lib
          )
          .joinToString(","),
  )
}

spotless {
  kotlin {
    ratchetFrom("origin/main")
    ktfmt()
    licenseHeaderFile("./.license-header")
  }
  kotlinGradle { ktfmt() }
}
