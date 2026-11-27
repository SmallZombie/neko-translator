import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	`java-library`
	id("org.jetbrains.kotlin.jvm")
}

repositories {
	mavenCentral()
}

dependencies {
	// Provided by the game / loader at runtime; never bundled into the mod jar.
	compileOnly("com.google.code.gson:gson:2.14.0")
	compileOnly("org.slf4j:slf4j-api:2.0.17")
}

// 26.3 requires java 25
// 1.21.11 requires java 21
// 1.20.4 requires java 17
tasks.withType<JavaCompile>().configureEach {
	options.release = 17
}

kotlin {
	jvmToolchain(17)
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}
