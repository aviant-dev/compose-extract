import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

val targetIde = providers.gradleProperty("platformIde").orElse("idea-community")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// Ladybug runs on Java 21 and supplies Kotlin 1.9 APIs.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

tasks.test {
    systemProperty("idea.kotlin.plugin.use.k2", providers.gradleProperty("kotlinK2").orElse("false").get())
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        when (targetIde.get()) {
            "android-studio" -> androidStudio("2024.2.1.9")
            "idea-community" -> intellijIdeaCommunity("2024.2.1")
            else -> error(
                "Unsupported platformIde '${targetIde.get()}'. Use 'idea-community' or 'android-studio'."
            )
        }

        bundledPlugin("org.jetbrains.kotlin")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)

    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242.21829.142")
            untilBuild.set("251.*")
        }
    }
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.1")
            ide(IntelliJPlatformType.AndroidStudio, "2024.2.1.9")
            ide(IntelliJPlatformType.AndroidStudio, "2025.1.1.13")
        }
    }
}
