plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    val proviaConversionLutsDir =
        rootProject.layout.projectDirectory.dir("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts")

    // Test-only: lets unit tests load the real, gitignored .cube files (see ADR 0003/0004)
    // to verify the Film Simulation registry is wired to the correct sRGB variants.
    // Production bundling of these files into the app is out of scope for this module.
    test {
        resources.srcDir(proviaConversionLutsDir)
        resources.include("Provia to Classic Neg sRGB.cube", "Provia to Nostalgic Neg sRGB.cube")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
