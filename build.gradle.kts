import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    val isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null
    intellijPlatform {
        if (!isCI && file("/Applications/Android Studio.app").exists()) {
            androidStudio("2025.1.1.13")
        } else {
            intellijIdeaCommunity("2025.1.6")
        }

        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)

    }
}

intellijPlatform {
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.6")
        }
    }
}
