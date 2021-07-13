val checkerFrameworkVersion: String by project.extra
val guavaVersion: String by project.extra
val nettyVersion: String by project.extra

license {
  header = project.rootProject.file("HEADER.txt")
}

dependencies {
  implementation("com.google.guava:guava:$guavaVersion")
  implementation("io.netty:netty-handler:$nettyVersion")
  implementation("org.checkerframework:checker-qual:$checkerFrameworkVersion")
}
