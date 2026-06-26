// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.rust.android) apply false
    alias(libs.plugins.detekt) apply true
}

detekt {
    val baselineFile = file("$rootDir/config/detekt-baseline.xml")
    val configFile = files("$rootDir/config/detekt.yml")
    val projectSource = file(projectDir)

    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(configFile)
    source.setFrom(projectSource)
    parallel = true
    ignoreFailures = false
    autoCorrect = true
    baseline = baselineFile
}
