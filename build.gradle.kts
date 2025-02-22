plugins {
    alias(libs.plugins.rewrite)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.shadow)
    alias(libs.plugins.asciidoctor)
}

version = "0.1"
group = "dev.fzymgc"

apply(from = "gradle/asciidoc.gradle")

val kotlinVersion = libs.versions.kotlin.get()

repositories {
    mavenCentral()
}

dependencies {
    kapt("info.picocli:picocli-codegen")
    kapt("io.micronaut.serde:micronaut-serde-processor")
    implementation("info.picocli:picocli")
    implementation("io.micrometer:context-propagation")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.toml:micronaut-toml")
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.jdk8)
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    rewrite(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
}

rewrite {
    activeRecipe(
        "org.openrewrite.java.AddApache2LicenseHeader"
    )
    plainTextMask("**/*.txt")
}

application {
    mainClass = "dev.fzymgc.ImageOrganizerCommand"
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_21.majorVersion))
    }
}

tasks {
    dockerBuild {
        images = listOf("${System.getenv("DOCKER_IMAGE") ?: project.name}:${project.version}")
    }

    dockerBuildNative {
        images = listOf("${System.getenv("DOCKER_IMAGE") ?: project.name}:${project.version}")
    }
}

micronaut {
    testRuntime("kotest5")
    processing {
        incremental(true)
        annotations("dev.fzymgc.*")
    }
}


tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "23"
}


