import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val targetIde = providers.gradleProperty("platformIde").orElse("idea-community")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        when (targetIde.get()) {
            "android-studio" -> androidStudio("2025.1.1.13")
            "idea-community" -> intellijIdeaCommunity("2025.1.6")
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
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.6")
        }
    }
}
