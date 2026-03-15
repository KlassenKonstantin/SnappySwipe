import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.detekt)
}

kotlin {
    androidLibrary {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        namespace = "de.kuno.snappyswipe.lib"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        androidResources.enable = true
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Lib"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

detekt {
    source.setFrom("src/commonMain/kotlin")
    config.setFrom(files("detekt.yml"))
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    detektPlugins("io.nlopez.compose.rules:detekt:0.5.6")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates("io.github.klassenkonstantin", "snappyswipe", "0.0.4")

    pom {
        name.set("Snappy Swipe")
        description.set("Material expressive-like snappy swipe behavior")
        inceptionYear.set("2026")
        url.set("https://github.com/KlassenKonstantin/SnappySwipe")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("KlassenKonstantin")
                name.set("Konstantin Klassen")
                url.set("https://github.com/KlassenKonstantin")
            }
        }
        scm {
            url.set("https://github.com/KlassenKonstantin/SnappySwipe")
            connection.set("scm:git:https://github.com/KlassenKonstantin/SnappySwipe")
            developerConnection.set("scm:git:ssh://git@github.com/KlassenKonstantin/ComposePhysicsLayout.git")
        }
    }
}