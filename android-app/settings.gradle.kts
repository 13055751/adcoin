pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pangle（穿山甲）SDK
        maven { url = uri("https://repo.pangle.cn/repo/maven-public") }
    }
}

rootProject.name = "AdCoin"
include(":app")
