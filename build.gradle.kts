plugins {
    id("net.neoforged.moddev.legacyforge") version("2.0.134")
    id("org.ajoberstar.grgit") version("5.2.0")
    id("com.palantir.git-version") version("1.0.0")
}

val minecraft_version = rootProject.properties["minecraft_version"].toString()

group = "org.embeddedt"

val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
// extract base version from tag, generate other metadata ourselves
val details = versionDetails()

var plusIndex = details.lastTag.indexOf("+")
if (plusIndex == -1) {
    plusIndex = details.lastTag.length
}

var baseVersion = details.lastTag.substring(0, plusIndex)

val dirtyMarker = if (grgit.status().isClean) "" else ".dirty"

val commitHashMarker =
    if (details.commitDistance > 0)
        "." + details.gitHash.substring(0, minOf(4, details.gitHash.length))
    else
        ""

var preMarker =
    if (details.commitDistance > 0 || !details.isCleanTag)
        "-beta.${details.commitDistance}"
    else
        ""

if (preMarker.isNotEmpty()) {
    // bump to next patch release
    val versionParts = baseVersion.split(".")
    baseVersion =
        "${versionParts[0]}.${versionParts[1]}.${versionParts[2].toInt() + 1}"
}

val versionString =
    "${baseVersion}${preMarker}+mc${minecraft_version}${commitHashMarker}${dirtyMarker}"

version = versionString

base.archivesName = "modernfix-forge"

legacyForge {
    enable {
        forgeVersion = rootProject.properties["forge_version"].toString()
        isDisableRecompilation = System.getenv("CI") == "true"
    }

    rootProject.properties["parchment_version"]?.let { parchmentVer ->
        parchment {
            minecraftVersion = minecraft_version
            mappingsVersion = parchmentVer.toString()
        }
    }

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
        }
    }

    mods {
        create("modernfix") {
            sourceSet(sourceSets.main.get())
        }
    }
}

mixin {
    add(sourceSets.main.get(), "modernfix.refmap.json")
    config("modernfix-modernfix.mixins.json")
}

tasks.named<Jar>("jar") {
    manifest.attributes(mapOf(
        "MixinConfigs" to "modernfix-modernfix.mixins.json",
        "Specification-Version" to "1",
        "Implementation-Title" to project.name,
        "Implementation-Version" to version
    ))
}

// We must force the Java 21 compiler to be used because our AP requires Java 21

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    val curSourceCompatLevel = JavaVersion.VERSION_17
    sourceCompatibility = curSourceCompatLevel
    targetCompatibility = curSourceCompatLevel
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                // location of the maven that hosts JEI files
                name = "Progwml6 maven"
                url = uri("https://dvs1.progwml6.com/files/maven/")
            }
        }
        forRepository {
            maven {
                name = "ModMaven"
                url = uri("https://modmaven.dev")
            }
        }
        filter {
            includeGroup("mezz.jei")
        }
    }
    exclusiveContent {
        forRepository {
            maven("https://cursemaven.com")
        }
        filter {
            includeGroup("curse.maven")
        }
    }
}

val embed by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
}

dependencies {
    implementation(project(":annotations"))
    embed(project(":annotations"))
    "additionalRuntimeClasspath"(project(":annotations"))
    annotationProcessor(project(path = ":annotation-processor", configuration = "shadow"))

    val mixinextrasVersion = rootProject.properties["mixinextras_version"].toString()
    implementation("io.github.llamalad7:mixinextras-common:${mixinextrasVersion}")
    annotationProcessor("net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${mixinextrasVersion}")
    implementation("io.github.llamalad7:mixinextras-forge:${mixinextrasVersion}")
    "jarJar"("io.github.llamalad7:mixinextras-forge:${mixinextrasVersion}")

    val jei_version = rootProject.properties["jei_version"].toString()
    modCompileOnly("mezz.jei:jei-${minecraft_version}-forge:${jei_version}")
    modCompileOnly("curse.maven:spark-361579:${rootProject.properties["spark_version"].toString()}")
    modCompileOnly("curse.maven:ctm-267602:${rootProject.properties["ctm_version"].toString()}")
    modCompileOnly("curse.maven:ldlib-626676:${rootProject.properties["ldlib_version"].toString()}")
    modCompileOnly("curse.maven:supermartijncore-454372:4455391")
    modCompileOnly("curse.maven:patchouli-306770:6164575")
    modCompileOnly("curse.maven:cofhcore-69162:5374122")
    modCompileOnly("curse.maven:resourcefullib-570073:5659871")
    modCompileOnly("curse.maven:kubejs-238086:5853326")
}

tasks.named<Jar>("jar") {
    from(embed.map { if (it.isDirectory) it else zipTree(it) })
}

// For the AP
tasks.withType<JavaCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        options.compilerArgs.addAll(
            listOf(
                "-ArootProject.name=${rootProject.name}",
                "-Aproject.name=${project.name}"
            )
        )
    }
}

sourceSets {
    main {
        resources.srcDir(
            layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main/resources")
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(tasks.named("compileJava"))

    inputs.property("version", project.version)

    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version)
    }
}

val finalJarTask = "reobfJar"

tasks.register<Copy>("copyJarNameConsistent") {
    from(tasks.named<Jar>(finalJarTask).get().outputs.files)
    into(project.file("build/libs"))
    rename { _ -> "modernfix-" + project.name + "-latest.jar" }
}

tasks.register<Copy>("copyJarToBin") {
    from(tasks.named<Jar>(finalJarTask).get().outputs.files)
    into(rootProject.file("bin"))
    mustRunAfter(tasks.named("copyJarNameConsistent"))
}

tasks.named("build") {
    dependsOn("copyJarToBin", "copyJarNameConsistent")
}