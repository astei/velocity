import com.velocitypowered.script.VelocityCheckstylePlugin
import com.velocitypowered.script.VelocityErrorPronePlugin
import com.velocitypowered.script.VelocityJavadocPlugin
import com.velocitypowered.script.VelocityPublishPlugin
import org.cadixdev.gradle.licenser.Licenser

plugins {
  `java-library`
  id("org.cadixdev.licenser").version("0.5.1")
  id("com.github.johnrengelman.shadow").version("7.0.0")
}

val junitVersion: String by project.extra

subprojects {
  group = "com.velocitypowered"
  version = "4.0.0-SNAPSHOT"

  apply<JavaLibraryPlugin>()
  apply<Licenser>()

  apply<VelocityCheckstylePlugin>()
  apply<VelocityErrorPronePlugin>()
  apply<VelocityJavadocPlugin>()
  apply<VelocityPublishPlugin>()

  java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  repositories {
    mavenCentral()
    // kyoripowered
    maven("https://oss.sonatype.org/content/groups/public/")
    // velocity
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
  }
  dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
  }
  tasks {
    test {
      useJUnitPlatform()
      reports {
        junitXml.isEnabled = true
      }
    }
  }
}
