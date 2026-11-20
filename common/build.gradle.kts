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

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}
