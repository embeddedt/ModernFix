plugins {
    id("net.neoforged.moddev") version("2.0.140")
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

base.archivesName = "modernfix-neoforge"

neoForge {
    enable {
        version = rootProject.properties["forge_version"].toString()
        isDisableRecompilation = System.getenv("CI") == "true"
    }

    rootProject.properties["parchment_version"]?.let { parchmentVer ->
        parchment {
            minecraftVersion = rootProject.properties["parchment_mc_version"].toString()
            mappingsVersion = parchmentVer.toString()
        }
    }

    runs {
        configureEach {
            systemProperty("modernfix.auditMixinsAtStart", "true")
        }
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

tasks.named<Jar>("jar") {
    manifest.attributes(mapOf(
        "Specification-Version" to "1",
        "Implementation-Title" to project.name,
        "Implementation-Version" to version
    ))
}

java {
    val curSourceCompatLevel = JavaVersion.VERSION_25
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
    annotationProcessor(project(path = ":annotation-processor", configuration = "shadow"))

    val jei_version = rootProject.properties["jei_version"].toString()
    val jei_minecraft_version = rootProject.properties["jei_minecraft_version"]?.toString() ?: minecraft_version
    compileOnly("mezz.jei:jei-${jei_minecraft_version}-neoforge:${jei_version}")
    compileOnly("curse.maven:spark-361579:${rootProject.properties["spark_version"].toString()}")
    compileOnly("curse.maven:ctm-267602:${rootProject.properties["ctm_version"].toString()}")
    compileOnly("curse.maven:ldlib-626676:${rootProject.properties["ldlib_version"].toString()}")
    compileOnly("curse.maven:supermartijncore-454372:4455391")
    compileOnly("curse.maven:cofhcore-69162:5374122")
    compileOnly("curse.maven:resourcefullib-570073:5659871")
    compileOnly("curse.maven:kubejs-238086:5853326")
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
    // Show more errors when porting
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
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

    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

val finalJarTask = "jar"

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

    modLoaders.add("neoforge")

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
