plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fujivibe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fujivibe"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }

    // Test-only from the LUT-licensing perspective, but bundled into every build type here,
    // since (unlike :render's tests) the running app genuinely needs these to render Review
    // previews. Loaded from the developer's local, gitignored copy of the abpy pack rather than
    // committed source — see ADR 0003/0004 and render/build.gradle.kts for the same pattern.
    // AGP's AndroidSourceDirectorySet has no include()/exclude() filter, so this pulls in the
    // whole "provia conversion luts" folder rather than just the two v1 files; the extras
    // (Bleach Bypass, DisplayP3 variants) sit unused since ResourceLutLoader only ever asks for
    // names in the FilmSimulation registry.
    //
    // derived-luts/ is different: it's FujiVibe's own committed derivative work (see ADR 0005),
    // not the gitignored third-party pack, so it's always present regardless of local setup.
    sourceSets {
        getByName("main") {
            resources.srcDir(
                rootProject.layout.projectDirectory.dir(
                    "raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts"
                )
            )
            resources.srcDir(rootProject.layout.projectDirectory.dir("derived-luts"))
        }
    }
}

dependencies {
    implementation(project(":render"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
