plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.fujivibe.render.tools.BakeClassicNegPixelLut")
}

// BakeClassicNegPixelLut reads/writes repo-root-relative paths (raw-assets/, derived-luts/),
// so `./gradlew :render:run` must execute with the repo root as its working directory rather
// than Gradle's per-module default.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// ./gradlew :render:previewFilmSimulation --args="<input> <output> [PANEL[,PANEL...]]"
// Renders a real photo through one or more Film Simulations for by-eye tuning, without
// installing the app. Each PANEL is ORIGINAL or a FilmSimulation name.
tasks.register<JavaExec>("previewFilmSimulation") {
    group = "application"
    description = "Renders an input image through a Film Simulation into a side-by-side comparison image."
    mainClass.set("com.fujivibe.render.tools.PreviewFilmSimulation")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}

// ./gradlew :render:bakeNostalgicNegPixelLut
// Regenerates derived-luts/Nostalgic Neg Pixel sRGB.cube from NostalgicNegCorrection's current
// constants. A registered task, not the application block's mainClass, so both bake tools stay
// independently runnable.
tasks.register<JavaExec>("bakeNostalgicNegPixelLut") {
    group = "application"
    description = "Regenerates derived-luts/Nostalgic Neg Pixel sRGB.cube from current NostalgicNegCorrection constants."
    mainClass.set("com.fujivibe.render.tools.BakeNostalgicNegPixelLut")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}

sourceSets {
    val proviaConversionLutsDir =
        rootProject.layout.projectDirectory.dir("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts")
    val derivedLutsDir = rootProject.layout.projectDirectory.dir("derived-luts")

    // Test-only: lets unit tests load the real .cube files to verify the Film Simulation
    // registry is wired to the correct sRGB variants. Both derived files are FujiVibe's own
    // committed work (see ADR 0005/0007) and always present. Neither original, unmodified stock
    // conversion LUT (Classic Neg., Nostalgic Neg.) is in this include list — both were removed
    // from the registry (ADR 0006, ADR 0008), so no test loads them via classpath resource
    // anymore. The `provia conversion luts/` directory is still wired as a resources srcDir
    // (rather than removed) since `BakeClassicNegPixelLut` and `BakeNostalgicNegPixelLut` both
    // read their source files directly from it by path, independent of this include list.
    // Production bundling of these files into the app is out of scope for this module.
    test {
        resources.srcDir(proviaConversionLutsDir)
        resources.srcDir(derivedLutsDir)
        resources.include(
            "Classic Neg Pixel sRGB.cube",
            "Nostalgic Neg Pixel sRGB.cube",
        )
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
