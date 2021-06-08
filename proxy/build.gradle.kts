import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
  application
  id("set-manifest-impl-version")
  id("com.github.johnrengelman.shadow")
}

val adventureVersion: String by project.extra
val asyncHttpClientVersion: String by project.extra
val bstatsVersion: String by project.extra
val caffeinVersion: String by project.extra
val completableFuturesVersion: String by project.extra
val disruptorVersion: String by project.extra
val fastUtilVersion: String by project.extra
val jansiVersion: String by project.extra
val joptSimpleVersion: String by project.extra
val lmbdaVersion: String by project.extra
val log4jVersion: String by project.extra
val nettyVersion: String by project.extra
val nightConfigVersion: String by project.extra
val semver4jVersion: String by project.extra
val terminalConsoleAppenderVersion: String by project.extra

license {
  header = project.rootProject.file("HEADER.txt")
}

dependencies {
  implementation(project(":velocity-api"))
  implementation(project(":velocity-annotation-processor"))
  implementation(project(":velocity-native"))

  compileOnly("com.github.spotbugs:spotbugs-annotations:4.1.2")
  implementation("net.kyori:adventure-nbt:$adventureVersion")
  implementation("org.asynchttpclient:async-http-client:$asyncHttpClientVersion")
  implementation("org.bstats:bstats-base:$bstatsVersion")
  implementation("com.github.ben-manes.caffeine:caffeine:$caffeinVersion")
  implementation("com.spotify:completable-futures:$completableFuturesVersion")
  implementation("it.unimi.dsi:fastutil:$fastUtilVersion")
  implementation("net.sf.jopt-simple:jopt-simple:$joptSimpleVersion")
  implementation("org.lanternpowered:lmbda:$lmbdaVersion")
  implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
  implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
  implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
  implementation("org.apache.logging.log4j:log4j-iostreams:$log4jVersion")
  implementation("org.apache.logging.log4j:log4j-jul:$log4jVersion")
  implementation("io.netty:netty-codec:$nettyVersion")
  implementation("io.netty:netty-codec-haproxy:$nettyVersion")
  implementation("io.netty:netty-codec-http:$nettyVersion")
  implementation("io.netty:netty-handler:$nettyVersion")
  implementation("io.netty:netty-transport-native-epoll:$nettyVersion")
  implementation("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")
  implementation("io.netty:netty-transport-native-epoll:$nettyVersion:linux-aarch_64")
  implementation("com.electronwill.night-config:toml:$nightConfigVersion")
  implementation("com.vdurmont:semver4j:$semver4jVersion")
  implementation("net.minecrell:terminalconsoleappender:$terminalConsoleAppenderVersion")
  runtimeOnly("com.lmax:disruptor:$disruptorVersion")
  runtimeOnly("org.jline:jline-terminal-jansi:$jansiVersion")
}

application {
  mainClass.set("com.velocitypowered.proxy.Velocity")
}

tasks {
  withType<Checkstyle> {
    exclude("**/com/velocitypowered/proxy/protocol/packet/*.java")
  }
  jar {
    manifest {
      attributes["Implementation-Title"] = "Velocity"
      attributes["Implementation-Vendor"] = "Velocity Contributors"
      attributes["Multi-Release"] = "true"
      attributes["Add-Opens"] = "java.base/java.lang"
    }
  }
  shadowJar {
    exclude("it/unimi/dsi/fastutil/booleans/**")
    exclude("it/unimi/dsi/fastutil/bytes/**")
    exclude("it/unimi/dsi/fastutil/chars/**")
    exclude("it/unimi/dsi/fastutil/doubles/**")
    exclude("it/unimi/dsi/fastutil/floats/**")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Boolean*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Byte*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Char*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Double*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Float*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Int*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Long*")
    exclude("it/unimi/dsi/fastutil/ints/*Int2Short*")
    exclude("it/unimi/dsi/fastutil/ints/IntAVL*")
    exclude("it/unimi/dsi/fastutil/ints/IntArray*")
    exclude("it/unimi/dsi/fastutil/ints/IntBi*")
    exclude("it/unimi/dsi/fastutil/ints/IntLinked*")
    exclude("it/unimi/dsi/fastutil/ints/IntList*")
    exclude("it/unimi/dsi/fastutil/ints/IntOpen*")
    exclude("it/unimi/dsi/fastutil/ints/IntRB*")
    exclude("it/unimi/dsi/fastutil/ints/IntSorted*")
    exclude("it/unimi/dsi/fastutil/ints/*Priority*")
    exclude("it/unimi/dsi/fastutil/ints/*BigList*")
    exclude("it/unimi/dsi/fastutil/io/**")
    exclude("it/unimi/dsi/fastutil/longs/**")
    exclude("it/unimi/dsi/fastutil/objects/*ObjectArray*")
    exclude("it/unimi/dsi/fastutil/objects/*ObjectAVL*")
    exclude("it/unimi/dsi/fastutil/objects/*Object*Big*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Boolean*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Byte*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Char*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Double*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Float*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2IntArray*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2IntAVL*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2IntRB*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Long*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Object*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Reference*")
    exclude("it/unimi/dsi/fastutil/objects/*Object2Short*")
    exclude("it/unimi/dsi/fastutil/objects/*ObjectRB*")
    exclude("it/unimi/dsi/fastutil/objects/*Reference*")
    exclude("it/unimi/dsi/fastutil/shorts/**")
    exclude("org/checkerframework/checker/**")
    //exclude("**/Log4j2Plugins.dat")

    relocate("org.bstats", "com.velocitypowered.proxy.bstats")

    transform(Log4j2PluginsCacheFileTransformer::class.java)
  }
}
