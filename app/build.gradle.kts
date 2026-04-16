plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("org.jetbrains.kotlin.kapt")
    id("jacoco")
}

android {
    namespace = "com.example.superstoresimulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.superstoresimulator"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.hilt.android)
    kapt(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        allWarningsAsErrors.set(false)
        freeCompilerArgs.addAll(
            "-Xsuppress-version-warnings",
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true"
        )
    }
}

// JaCoCo Code Coverage Configuration
jacoco {
    // 0.8.12 (April 2024) uses ASM 9.7 which handles Java 22/23 bytecode.
    // The additional excludes below stop the agent from trying to instrument
    // JDK-internal classes (proxy2, sun.*, com.sun.*, org.jcp.*) that are
    // compiled at Java-25 bytecode level (major version 69) — the root cause
    // of the "Unsupported class file major version 69" error with 0.8.10.
    toolVersion = "0.8.12"
}

afterEvaluate {
    tasks.withType<Test> {
        extensions.getByType(JacocoTaskExtension::class).apply {
            isIncludeNoLocationClasses = true
            // Exclude JDK-internal packages from agent instrumentation.
            // These classes are compiled with newer bytecode than JaCoCo's ASM
            // supports, and they carry no project coverage data anyway.
            excludes = listOf(
                "jdk.proxy*",
                "sun.*",
                "com.sun.*",
                "org.jcp.*"
            )
        }
    }

    tasks.register<JacocoReport>("testCoverageReport") {
        dependsOn(tasks.withType<Test>())

        reports {
            xml.required = true
            html.required = true
            csv.required = true
        }

        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
        classDirectories.setFrom(
            fileTree(
                mapOf(
                    "dir" to "${layout.buildDirectory.get()}/intermediates/classes/debug",
                    "excludes" to listOf(
                        "**/R.class",
                        "**/R\$*.class",
                        "**/*Module_*.class",
                        "**/*Hilt_*.class",
                        "**/*_Factory.class",
                        "**/*BuildConfig*"
                    )
                )
            )
        )
        
        executionData.setFrom(
            fileTree(
                mapOf(
                    "dir" to layout.buildDirectory.get(),
                    "includes" to listOf("**/*.exec", "**/*.ec")
                )
            )
        )
    }
}
