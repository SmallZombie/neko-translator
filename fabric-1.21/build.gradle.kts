import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm")
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("neko-translator") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

/** Shared properties live in the root gradle.properties, module specific ones as "[module].[key]". */
fun prop(key: String): String =
	providers.gradleProperty("${project.name}.$key").orElse(providers.gradleProperty(key)).get()

// Mod version + target Minecraft version
version = "${rootProject.version}+mc${prop("minecraft_version")}"

base {
	archivesName.set("${rootProject.name}-fabric")
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
	// 1.21 is still obfuscated, so official Mojang mappings are what the code is written against.
	mappings(loom.officialMojangMappings())

	modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")

	// Loader/version independent logic, merged into this jar below.
	implementation(project(":common"))
}

tasks.processResources {
	val version = version
	val description = prop("description")

	inputs.property("version", version)
	inputs.property("description", description)

	filesMatching("fabric.mod.json") {
		expand(
			mapOf(
				"version" to version,
				"description" to description,
			)
		)
	}

	// Shared assets (icon, language files) live in :common so every module ships the same files.
	// They are copied into this module's resources, which is also what the dev client loads.
	from(project(":common").file("src/main/resources"))
}

// 1.21 runs on Java 21; the common module is built for the same target.
java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}

	withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

val commonMain = project(":common").extensions.getByType<SourceSetContainer>()["main"]

tasks.jar {
	val projectName = rootProject.name
	inputs.property("projectName", projectName)

	// The common module is not a mod on its own: its classes are merged into this jar.
	// Its resources were already copied in by processResources, so skip them here.
	from(commonMain.output) {
		exclude("assets/**")
	}

	from(rootProject.file("LICENSE")) {
		rename { "${it}_$projectName" }
	}
}

tasks.named<Jar>("sourcesJar") {
	from(commonMain.allSource)
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
	}
}
