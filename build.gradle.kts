import io.micronaut.gradle.docker.tasks.BuildLayersTask
import org.jreleaser.gradle.plugin.tasks.JReleaserAssembleTask
import org.jreleaser.gradle.plugin.tasks.JReleaserPackageTask
import org.jreleaser.model.Active
import org.jreleaser.model.Archive
import org.jreleaser.model.Distribution
import org.jreleaser.model.Distribution.DistributionType
import org.jreleaser.model.Stereotype

plugins {
    alias(libs.plugins.rewrite)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.shadow)
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.osdetector)
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
    implementation(libs.image.metadata)
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    rewrite(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
}

rewrite {
    plainTextMask("**/*.txt")
}

application {
    mainClass = "dev.fzymgc.ImageOrganizerCommand"
}
java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_23.majorVersion))
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

graalvmNative {
    binaries {
        named("main") {
            imageName.set("${project.name}-${project.version}-${osdetector.classifier}")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(23))
                vendor.set(JvmVendorSpec.matching("GraalVM Community"))
            })
        }
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "23"
}

val projectLayout = project.layout
val projectName = project.name
val projectVersion = project.version
val buildLayersTask = tasks.named<BuildLayersTask>("buildNativeLayersTask")
val nativeCompileTask = tasks.named("nativeCompile")
val buildTask = tasks.named("build")
jreleaser {
    catalog {
        active.set(Active.ALWAYS)
        sbom {
            cyclonedx {
                active.set(Active.ALWAYS)
            }
        }
    }
    matrix {
        variable("os", listOf("linux", "osx"))
        variable("arch", listOf("arm64", "amd64"))
    }
    project {
        authors.set(listOf("Sean Brandt"))
        license = "Apache-2.0"
        inceptionYear = "2024"
        description = "Image Organizer"
        website = "https://github.com/seanb4t/image-organizer"
        docsUrl = "https://github.com/seanb4t/image-organizer/blob/main/README.md"
        stereotype = Stereotype.CLI
    }
    release {
        github {
            repoOwner = "seanb4t"
            overwrite = true
        }
    }
    distributions {
        create("image-organizer") {
            distributionType.set(DistributionType.FLAT_BINARY)
            artifact {
                path.set(projectLayout.buildDirectory.file("native/nativeCompile/{{distributionName}}-{{projectEffectiveVersion}}-{{osName}}-{{osArch}}"))
                platform.set(osdetector.classifier)
            }
        }
    }
    platform {
        // Key-value pairs.
        // Keys match a full platform or an os.name, os.arch.
        replacements = mapOf(
            "osx-x86_64" to "darwin-amd64",
            "osx-aarch_64" to "darwin-aarch64",
            "aarch_64" to "aarch64",
            "x86_64" to "amd64",
            "linux_musl" to "alpine"
        )
    }
}

val createJreleaserBuildDirTask = tasks.register("createJreleaserBuildDir") {
    doLast {
        mkdir(project.layout.buildDirectory.dir("jreleaser"))
    }
}

val assembleTask = tasks.named("assemble")
val assembleDistTask = tasks.named("assembleDist")

assembleTask.configure {
    dependsOn(nativeCompileTask, createJreleaserBuildDirTask, buildLayersTask)
}

assembleDistTask.configure { dependsOn(assembleTask) }

tasks.withType<JReleaserAssembleTask>().configureEach {
    dependsOn(assembleTask)
}
