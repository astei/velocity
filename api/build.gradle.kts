val adventureVersion: String by project
val coffeeVersion: String by project
val configurateVersion: String by project
val checkerFrameworkVersion: String by project
val guavaVersion: String by project
val guiceVersion: String by project
val gsonVersion: String by project
val slf4jVersion: String by project

license {
  header = project.file("HEADER.txt")
}

dependencies {
  api("net.kyori:adventure-api:$adventureVersion")
  api("net.kyori:adventure-text-serializer-gson:$adventureVersion")
  api("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
  api("net.kyori:adventure-text-serializer-plain:$adventureVersion")
  api("com.velocitypowered:velocity-brigadier:1.0.0-SNAPSHOT")
  api("net.kyori:coffee:$coffeeVersion")
  api("org.spongepowered:configurate-hocon:$configurateVersion")
  api("org.spongepowered:configurate-gson:$configurateVersion")
  api("org.spongepowered:configurate-yaml:$configurateVersion")
  api("org.checkerframework:checker-qual:$checkerFrameworkVersion")
  api("com.google.inject:guice:$guiceVersion")
  api("com.google.code.gson:gson:$gsonVersion")
  api("com.google.guava:guava:$guavaVersion")
  api("org.slf4j:slf4j-api:$slf4jVersion")
}
