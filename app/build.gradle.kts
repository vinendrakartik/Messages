import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun getSigningProperty(propName: String): String? {
    val snakeName = propName.replace("([a-z])([A-Z])".toRegex(), "$1_$2").uppercase()
    val keysToTry = listOf(
        "SIGNING_$snakeName",
        propName,
        propName.lowercase(),
        "signing.$propName",
        "RELEASE_$snakeName"
    )
    
    for (key in keysToTry) {
        val value = project.findProperty(key)?.toString() 
            ?: project.providers.gradleProperty(key).orNull
            ?: project.providers.environmentVariable(key).orNull
        
        if (!value.isNullOrBlank()) {
            return value
        }
    }
    
    return keystoreProperties.getProperty(propName)
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = project.property("VERSION_CODE").toString().toInt()
        setProperty("archivesBaseName", "messages-$versionCode")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    val sKeyAlias = getSigningProperty("keyAlias")
    val sKeyPassword = getSigningProperty("keyPassword")
    val sStoreFile = getSigningProperty("storeFile")
    val sStorePassword = getSigningProperty("storePassword")
    
    val missing = mutableListOf<String>()
    if (sKeyAlias.isNullOrBlank()) missing.add("keyAlias (looked for SIGNING_KEY_ALIAS)")
    if (sKeyPassword.isNullOrBlank()) missing.add("keyPassword (looked for SIGNING_KEY_PASSWORD)")
    if (sStoreFile.isNullOrBlank()) missing.add("storeFile (looked for SIGNING_STORE_FILE)")
    if (sStorePassword.isNullOrBlank()) missing.add("storePassword (looked for SIGNING_STORE_PASSWORD)")
    
    val canSign = missing.isEmpty()

    signingConfigs {
        if (canSign) {
            register("release") {
                keyAlias = sKeyAlias
                keyPassword = sKeyPassword
                storeFile = file(sStoreFile!!)
                storePassword = sStorePassword
            }
        } else {
            // This will show up in your console
            println("---------------------------------------------------------")
            println("SIGNING DEBUG:")
            println("Checking global properties in C:/Users/vk/.gradle/gradle.properties...")
            println("Missing signatures: ${missing.joinToString(", ")}")
            println("---------------------------------------------------------")
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSign) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
        // Limit languages to English and Indian languages to reduce APK size
        localeFilters += listOf("en", "hi", "mr", "te", "ta", "kn", "ml")
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.eventbus)
    implementation(libs.indicator.fast.scroll)
    implementation(libs.mmslib)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ez.vcard)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
}
