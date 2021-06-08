plugins {
  `kotlin-dsl`
  checkstyle
}

repositories {
  mavenCentral()
  maven("https://plugins.gradle.org/m2")
}

dependencies {
  implementation("net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:2.0.1")
}

gradlePlugin {
  plugins {
    register("set-manifest-impl-version") {
      id = "set-manifest-impl-version"
      implementationClass = "com.velocitypowered.script.SetManifestImplVersionPlugin"
    }
    register("velocity-checkstyle") {
      id = "velocity-checkstyle"
      implementationClass = "com.velocitypowered.script.VelocityCheckstylePlugin"
    }
    register("velocity-errorprone") {
      id = "velocity-errorprone"
      implementationClass = "com.velocitypowered.script.VelocityErrorPronePlugin"
    }
    register("velocity-javadoc") {
      id = "velocity-javadoc"
      implementationClass = "com.velocitypowered.script.VelocityErrorPronePlugin"
    }
    register("velocity-publish") {
      id = "velocity-publish"
      implementationClass = "com.velocitypowered.script.VelocityPublishPlugin"
    }
  }
}
