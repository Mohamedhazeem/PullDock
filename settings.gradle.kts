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

rootProject.name = "PullDock"

// Composition root
include(":app")

// Platform infrastructure
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:storage")
include(":core:network")

// Domain (pure Kotlin)
include(":domain:download")
include(":domain:torrent")
include(":domain:extractor")
include(":domain:discovery")

// Data (repository implementations, adapters, coordinator)
include(":data:download")
include(":data:torrent")
include(":data:extractor")
include(":data:discovery")

// Native/runtime engines
include(":engines:torrent-libtorrent")
include(":engines:extractor-ytdlp")
include(":engines:media-ffmpeg")

// Feature presentation modules
include(":feature:onboarding")
include(":feature:home")
include(":feature:add")
include(":feature:queue")
include(":feature:download-detail")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
include(":feature:legal")
include(":feature:discovery")
