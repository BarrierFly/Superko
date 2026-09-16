plugins {
    id("net.fabricmc.fabric-loom-remap")
}

group = property("maven.group") as String
version = property("mod.version") as String

base {
    archivesName.set(property("mod.id") as String)
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

loom {
    mods {
        create("superko") {
            sourceSet(sourceSets.main.get())
        }
    }
    // register the source set as containing mixins so Loom wires the Mixin annotation
    // processor (without it no refmap is produced and the mixins would not resolve in
    // the obfuscated runtime)
    mixin {
        useLegacyMixinAp = true
        add(sourceSets.main.get(), "superko-refmap.json")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("deps.minecraft")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("it.unimi.dsi:fastutil:8.5.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    // The agreement tests drive the naive specification oracle, which keeps a full
    // configuration copy per moment on purpose.
    maxHeapSize = "1g"
}
