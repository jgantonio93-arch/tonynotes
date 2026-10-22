// Número da execução do CI (0 em builds locais). Entra no versionCode, para cada APK
// novo instalar por cima do anterior, e no BuildConfig, para o app comparar com a
// tag "buildN" da última versão publicada no GitHub e avisar quando houver uma nova.
val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jgantonio.notasinfinitas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jgantonio.notasinfinitas"
        minSdk = 29
        targetSdk = 35
        versionCode = if (ciBuild > 0) 1000 + ciBuild else 11
        versionName = "0.26.0"
        buildConfigField("int", "BUILD_NUMBER", ciBuild.toString())
    }

    signingConfigs {
        // Chave de debug fixa no repositório: assim cada APK novo do CI instala
        // por cima do anterior sem precisar desinstalar (e sem perder as notas).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("shots.dir", rootProject.file("docs/screenshots").absolutePath)
                // Vídeo de demonstração (só quando pedido: ./gradlew ... -Ddemo.dir=pasta)
                System.getProperty("demo.dir")?.let { d -> it.systemProperty("demo.dir", d) }
                it.testLogging {
                    events("passed", "failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

dependencies {
    // Front buffer para tinta com latência mínima
    implementation("androidx.graphics:graphics-core:1.0.0")
    // Login do Google para o backup no Drive (escopo drive.file)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

// O APK sai como RosaNotes-debug.apk (o id do pacote continua o mesmo: atualiza sem perder as notas).
base {
    archivesName.set("RosaNotes")
}
