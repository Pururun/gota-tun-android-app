import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

metro {
    debug = true
    enableKotlinVersionCompatibilityChecks = false
}

android {
    namespace = "net.mullvad.gotatunandroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.mullvad.gotatunandroid"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    sourceSets["main"].jniLibs.directories.add("src/main/jniLibs")
}

// ======== Rust / cargo-ndk build tasks ========

val ndkHome: String = System.getenv("ANDROID_NDK_HOME")
    ?: run {
        val ndkDir = File("${System.getenv("HOME")}/Library/Android/sdk/ndk")
        val latestNdk = ndkDir.listFiles()?.sortedDescending()?.firstOrNull()?.name ?: "27.2.12479018"
        "${ndkDir.absolutePath}/$latestNdk"
    }

val rustDir = rootProject.file("rust").absolutePath

val buildRustDebug by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build Rust JNI library for Android (debug)"
    workingDir(rustDir)
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", file("src/main/jniLibs").absolutePath,
        "--",
        "build"
    )
    environment("ANDROID_NDK_HOME", ndkHome)
}

val buildRustRelease by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build Rust JNI library for Android (release)"
    workingDir(rustDir)
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", file("src/main/jniLibs").absolutePath,
        "--",
        "build", "--release"
    )
    environment("ANDROID_NDK_HOME", ndkHome)
}

// Wire Rust build into the Android build pipeline
afterEvaluate {
    tasks.named("mergeDebugJniLibFolders") { dependsOn(buildRustDebug) }
    tasks.named("mergeReleaseJniLibFolders") { dependsOn(buildRustRelease) }
}

dependencies {
    implementation(libs.javax.inject)
    implementation(libs.metro.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.accompanist.permissions)
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.core)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}
