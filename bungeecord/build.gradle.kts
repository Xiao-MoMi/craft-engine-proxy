import net.momirealms.netty

plugins {
    id("xyz.jpenilla.run-waterfall") version "3.0.2"
    id("net.minecrell.plugin-yml.bungee") version "0.6.0"
}

dependencies {
    implementation(project(":common"))
    netty(project)
    // Platform
    compileOnly("net.md-5:bungeecord-api:${rootProject.properties["bungeecord_version"]}")
    compileOnly("org.jetbrains:annotations:${rootProject.properties["jetbrains_annotations_version"]}")
    compileOnly("com.github.ben-manes.caffeine:caffeine:${rootProject.properties["caffeine_version"]}")
    // Reflection
    compileOnly(files("${rootProject.rootDir}/libs/jni-internal-lookup-1.9.jar"))
    compileOnly("net.momirealms:sparrow-reflection:${rootProject.properties["sparrow_reflection_version"]}")
}

tasks {
    runWaterfall {
        waterfallVersion(rootProject.properties["waterfall_version"] as String)
        jvmArgs("-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
    }

    shadowJar {
        relocation.applyProxy(this)
        archiveFileName = "${rootProject.name}-bungeecord-plugin-${rootProject.properties["project_version"]}.jar"
        destinationDirectory.set(file("$rootDir/target"))
    }
}

bungee {
    name = "CraftEngine"
    version = rootProject.properties["project_version"] as String
    main = "net.momirealms.craftengine.proxy.bungeecord.BungeeCordCraftEngine"
    author = "Catnies"
}

artifacts {
    implementation(tasks.shadowJar)
}
