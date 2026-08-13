plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.aura.app"
    compileSdk = 36

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "DIRECT_SMS_AVAILABLE", "false")
        }
        create("unrestricted") {
            dimension = "distribution"
            versionNameSuffix = "-unrestricted"
            buildConfigField("boolean", "DIRECT_SMS_AVAILABLE", "true")
        }
    }

    defaultConfig {
        applicationId = "com.aura.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"

        val stalkyApiUrl = providers.gradleProperty("stalkyApiUrl").orElse("")
        val supabaseUrl = providers.gradleProperty("stalkySupabaseUrl").orElse("")
        val supabasePublishableKey = providers.gradleProperty("stalkySupabasePublishableKey").orElse("")
        val googleWebClientId = providers.gradleProperty("stalkyGoogleWebClientId").orElse("")
        val backgroundDefault = providers.gradleProperty("auraEnableBackgroundListeningDefault").orElse("false")
        fun quoteBuildConfig(value: String): String =
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        buildConfigField("String", "STALKY_API_URL", quoteBuildConfig(stalkyApiUrl.get()))
        buildConfigField("String", "STALKY_SUPABASE_URL", quoteBuildConfig(supabaseUrl.get()))
        buildConfigField(
            "String",
            "STALKY_SUPABASE_PUBLISHABLE_KEY",
            quoteBuildConfig(supabasePublishableKey.get())
        )
        buildConfigField("String", "STALKY_GOOGLE_WEB_CLIENT_ID", quoteBuildConfig(googleWebClientId.get()))
        buildConfigField("boolean", "AURA_ENABLE_BACKGROUND_LISTENING_DEFAULT", backgroundDefault.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api34") {
                    device = "Pixel 2"
                    apiLevel = 34
                    systemImageSource = "aosp"
                }
                create("pixel2Api35") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
                create("pixel2Api36") {
                    device = "Pixel 2"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
            groups {
                create("phoneApiMatrix") {
                    targetDevices.add(allDevices["pixel2Api34"])
                    targetDevices.add(allDevices["pixel2Api35"])
                    targetDevices.add(allDevices["pixel2Api36"])
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // MongoDB 3.12 is the final official Java driver line with Android support.
    // Local mode rejects SRV URIs and MongoDB Server 8.1+ because that legacy
    // transport cannot safely support either on Android.
    implementation("org.mongodb:mongodb-driver-sync:3.12.14")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("androidx.work:work-testing:2.11.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val validateReleaseBackendUrl = tasks.register("validateReleaseBackendUrl") {
    group = "verification"
    description = "Ensures release builds have secure Stalky and Supabase configuration."
    doLast {
        val releaseApiUrl = providers.gradleProperty("stalkyApiUrl").orNull?.trim().orEmpty()
        if (!releaseApiUrl.startsWith("https://")) {
            throw org.gradle.api.GradleException(
                "Release builds require -PstalkyApiUrl=https://...; the checked-in build has no secure default."
            )
        }
        val releaseSupabaseUrl = providers.gradleProperty("stalkySupabaseUrl")
            .orNull?.trim().orEmpty()
        if (!releaseSupabaseUrl.startsWith("https://")) {
            throw org.gradle.api.GradleException(
                "Release builds require -PstalkySupabaseUrl=https://<project>.supabase.co."
            )
        }
        val releasePublishableKey = providers.gradleProperty("stalkySupabasePublishableKey")
            .orNull?.trim().orEmpty()
        if (releasePublishableKey.isEmpty() || releasePublishableKey.any(Char::isWhitespace)) {
            throw org.gradle.api.GradleException(
                "Release builds require -PstalkySupabasePublishableKey=<public key>."
            )
        }
        val releaseGoogleWebClientId = providers.gradleProperty("stalkyGoogleWebClientId")
            .orNull?.trim().orEmpty()
        if (!releaseGoogleWebClientId.endsWith(".apps.googleusercontent.com")) {
            throw org.gradle.api.GradleException(
                "Release builds require -PstalkyGoogleWebClientId=<web OAuth client id>."
            )
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseBackendUrl)
    }
}
