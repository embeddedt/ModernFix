plugins {
    id("net.neoforged.moddev.legacyforge") version("2.0.134")
    id("me.modmuss50.mod-publish-plugin") version("1.1.0")
}

val minecraft_version = rootProject.properties["minecraft_version"].toString()

group = "org.embeddedt"

val gitVersion = providers.of(GitVersionSource::class) {
    parameters {
        minecraftVersion.set(minecraft_version)
        projectDir.set(rootProject.layout.projectDirectory)
    }
}

version = gitVersion.get()

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
        create("auditClient") {
            client()
            jvmArguments.addAll("-Dmodernfix.auditAndExit=true", "-Djava.awt.headless=true")
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

java {
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
    modCompileOnly("curse.maven:terrablender-563928:6290448")
}

tasks.named<Jar>("jar") {
    from(embed.map { if (it.isDirectory) it else zipTree(it) })
}

val checkDanglingMixinPackageInfo by tasks.registering {
    val mixinDir = file("src/main/java/org/embeddedt/modernfix/common/mixin")
    inputs.dir(mixinDir)
    doLast {
        val dangling = mutableListOf<File>()
        mixinDir.walkTopDown()
            .filter { it.name == "package-info.java" }
            .forEach { pkgInfo ->
                val dir = pkgInfo.parentFile
                val hasOtherJava = dir.listFiles { f -> f.name != "package-info.java" && f.extension == "java" }?.isNotEmpty() == true
                val hasSubpackage = dir.listFiles { f -> f.isDirectory }?.isNotEmpty() == true
                if (!hasOtherJava && !hasSubpackage) {
                    dangling += pkgInfo
                }
            }
        if (dangling.isNotEmpty()) {
            throw GradleException(
                "Dangling package-info.java files found (no sibling classes or subpackages):\n" +
                dangling.joinToString("\n") { "  ${it.relativeTo(projectDir)}" }
            )
        }
    }
}

// For the AP
tasks.withType<JavaCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        dependsOn(checkDanglingMixinPackageInfo)
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

publishMods {
    file.set(tasks.named<Jar>(finalJarTask).flatMap { it.archiveFile })
    displayName.set(tasks.named<Jar>(finalJarTask).flatMap { it.archiveFileName })
    changelog = "Please check the [GitHub wiki](https://github.com/embeddedt/ModernFix/wiki/Changelog) for major changes."
    type = STABLE

    modLoaders.add("forge")

    curseforge {
        projectId = "790626"
        projectSlug = "modernfix"
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.add(minecraft_version)
    }
    modrinth {
        projectId = "nmDcB62a"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.add(minecraft_version)
    }
}

tasks.named("publishMods") {
    dependsOn(finalJarTask)
}