import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("com.github.gmazzo.buildconfig") version "5.4.0"
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.coroutines.core)
        }
        val baseUrlProp = (project.findProperty("BASE_URL") as String?) ?: "https://jsonplaceholder.typicode.com"
        val connectTimeoutMsProp = (project.findProperty("CONNECT_TIMEOUT_MS") as String?) ?: "8000"
        val sendTimeoutMsProp = (project.findProperty("SEND_TIMEOUT_MS") as String?) ?: "8000"
        val receiveTimeoutMsProp = (project.findProperty("RECEIVE_TIMEOUT_MS") as String?) ?: "8000"
        val enableRetryProp = (project.findProperty("ENABLE_RETRY") as String?) ?: "false"

        sourceSets.named("commonMain").configure {
            buildConfig {
                packageName("net.bench.config")
                buildConfigField("String", "BASE_URL", "\"$baseUrlProp\"")
                buildConfigField("long", "CONNECT_TIMEOUT_MS", connectTimeoutMsProp)
                buildConfigField("long", "SEND_TIMEOUT_MS", sendTimeoutMsProp)
                buildConfigField("long", "RECEIVE_TIMEOUT_MS", receiveTimeoutMsProp)
                buildConfigField("boolean", "ENABLE_RETRY", enableRetryProp)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidUnitTest.dependencies {
            implementation(libs.okhttp3.mockwebserver)
            implementation(libs.kotlin.testJunit)
            implementation(libs.junit)
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    val aBaseUrl = (project.findProperty("BASE_URL") as String?) ?: "https://jsonplaceholder.typicode.com"
    val aConnectMs = (project.findProperty("CONNECT_TIMEOUT_MS") as String?) ?: "8000"
    val aSendMs = (project.findProperty("SEND_TIMEOUT_MS") as String?) ?: "8000"
    val aReceiveMs = (project.findProperty("RECEIVE_TIMEOUT_MS") as String?) ?: "8000"
    val aEnableRetry = (project.findProperty("ENABLE_RETRY") as String?) ?: "false"
    buildConfigField("String", "BASE_URL", "\"$aBaseUrl\"")
    buildConfigField("int", "CONNECT_TIMEOUT_MS", aConnectMs)
    buildConfigField("int", "SEND_TIMEOUT_MS", aSendMs)
    buildConfigField("int", "RECEIVE_TIMEOUT_MS", aReceiveMs)
    buildConfigField("boolean", "ENABLE_RETRY", aEnableRetry)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        getByName("debug") {
            // Logging stays OFF by default; can be toggled via build config if needed.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        abortOnError = false
        disable += listOf("NullSafeMutableLiveData")
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

