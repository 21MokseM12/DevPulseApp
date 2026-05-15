import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("jacoco")
}

configurations.configureEach {
    exclude(group = "androidx.compose.runtime", module = "runtime-lint")
}

val releaseCertPins: String = providers.gradleProperty("devpulse.releaseCertPins").orElse("").get()
val stagingCertPins: String = providers.gradleProperty("devpulse.stagingCertPins").orElse("").get()

val hasFirebaseConfig =
    file("google-services.json").exists() ||
        listOf("debug", "staging", "release").any { buildType ->
            file("src/$buildType/google-services.json").exists()
        }

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.devpulse.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devpulse.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 66
        versionName = "1.43.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())

        testInstrumentationRunner = "com.devpulse.app.HiltTestRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("String", "ENVIRONMENT", "\"debug\"")
            buildConfigField("String", "RELEASE_CERT_PINS", "\"\"")
            buildConfigField("String", "STAGING_CERT_PINS", "\"\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }

        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "BASE_URL", "\"https://staging-api.devpulse.example/\"")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("String", "RELEASE_CERT_PINS", "\"$releaseCertPins\"")
            buildConfigField("String", "STAGING_CERT_PINS", "\"$stagingCertPins\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "BASE_URL", "\"https://api.devpulse.example/\"")
            buildConfigField("String", "ENVIRONMENT", "\"release\"")
            buildConfigField("String", "RELEASE_CERT_PINS", "\"$releaseCertPins\"")
            buildConfigField("String", "STAGING_CERT_PINS", "\"$stagingCertPins\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        // Temporary workaround: AGP/Lint crashes on unit tests with this Compose detector.
        disable += "StateFlowValueCalledInComposition"
        // Temporary workaround: Compose lint detector crashes with current AGP/Lint toolchain.
        disable += "FlowOperatorInvokedInComposition"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    outputToConsole.set(true)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.named("check").configure {
    dependsOn("ktlintCheck", "lintDebug")
}

tasks.matching { task ->
    task.name == "lintAnalyzeDebug" ||
        task.name == "lintAnalyzeDebugUnitTest"
}.configureEach {
    // AGP 9 + Compose lint bug: NoClassDefFoundError in ComposableFlowOperatorDetector.
    enabled = false
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs code style and static analysis checks."
    dependsOn("ktlintCheck", "lintDebug")
}

tasks.named("build").configure {
    dependsOn("qualityCheck", "jacocoTestReport")
}

tasks.register("codeStyleFormat") {
    group = "formatting"
    description = "Auto-formats Kotlin sources using ktlint."
    dependsOn("ktlintFormat")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates unit test coverage report for debug build."
    dependsOn("testDebugUnitTest")

    val excludes =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*\$Lambda\$*.*",
            "**/*\$inlined\$*.*",
            "**/*_Factory*.*",
            "**/*_MembersInjector*.*",
            "**/*_HiltModules*.*",
            "**/Hilt_*.*",
            "**/*ComposableSingletons*.*",
        )

    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(excludes)
        },
        fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
            exclude(excludes)
        },
    )

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.4")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    implementation("androidx.compose.material3:material3:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
}
