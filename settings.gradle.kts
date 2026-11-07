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
        // usb-serial-for-android (the OBD dongle's USB chip)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CarDash"
include(":app")
