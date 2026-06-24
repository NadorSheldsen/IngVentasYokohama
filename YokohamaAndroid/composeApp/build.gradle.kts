import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

// Forzar el uso de versiones estables y compatibles de kotlinx.serialization
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3"
        )
    }
}

kotlin {
    androidTarget {
        // Usamos la cadena de herramientas de JVM para asegurar compatibilidad
        jvmToolchain(11)
    }

    // Configuración nativa para iOS (Target Apple)
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

    // Target para la aplicación de Escritorio (JVM)
    jvm()

    // Configurar opciones del compilador Kotlin
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xexpect-actual-classes"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation("com.russhwolf:multiplatform-settings-datastore:1.1.1") 
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            implementation("org.slf4j:slf4j-simple:2.0.7") 
        }
        
        iosMain.dependencies {
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }
        
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            
            // Ktor y Serialización
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            implementation("io.ktor:ktor-client-logging:3.0.3")
            
            // Librerías de soporte Multiplatform
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("com.russhwolf:multiplatform-settings:1.1.1") 
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") 
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0") 
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("io.ktor:ktor-client-java:3.0.3")
        }
    }
}

android {
    namespace = "com.megatransportes.yokohama"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.megatransportes.yokohama"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
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
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.megatransportes.yokohama.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.megatransportes.yokohama"
            packageVersion = "1.0.0"
        }
    }
}