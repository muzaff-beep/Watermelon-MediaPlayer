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
    }
}

rootProject.name = "watermelon-mediaplayer"

include(
    ":common-interfaces",
    ":playback-engine",
    ":library-storage",
    ":subtitle-engine",
    ":media-tools",
    ":ui-presentation",
    ":app",
    ":benchmarks"
)
