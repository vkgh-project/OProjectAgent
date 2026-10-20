// bs-app: the composition root (layer 4). It wires every lower module into a
// runnable application (headless or windowed) and owns the main loop.
//
// Same-layer rule: bs-app must never depend on bs-tools (also layer 4) —
// the two talk through the bs-api contracts only.
plugins {
    `java-library`
    application
}

val lwjglVersion = "3.4.3"
val archunitVersion = "1.5.1"

dependencies {
    // Layers 0..3: a composition root may depend on every lower module.
    implementation(project(":bs-api"))
    implementation(project(":bs-math"))
    implementation(project(":bs-terrain"))
    implementation(project(":bs-features"))
    implementation(project(":bs-nav"))
    implementation(project(":bs-weather"))
    implementation(project(":bs-combat"))
    implementation(project(":bs-movement"))
    implementation(project(":bs-scenario"))
    implementation(project(":bs-engine"))
    implementation(project(":bs-session"))
    implementation(project(":bs-gfx-core"))
    implementation(project(":bs-gfx-world"))
    implementation(project(":bs-gfx-units"))
    implementation(project(":bs-gfx-fx"))
    implementation(project(":bs-ui"))

    // LWJGL is `implementation`-scoped in the gfx modules, so the windowed
    // composition declares it (and its natives) for its own compile/runtime
    // classpaths.
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")

    // LWJGL natives per OS (runtime only).
    listOf("natives-windows", "natives-linux", "natives-macos").forEach { native ->
        runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$native")
        runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$native")
        runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$native")
    }

    testImplementation("com.tngtech.archunit:archunit:$archunitVersion")
}

application {
    mainClass = "ua.battlesim.app.Main"
    // GLFW/LWJGL use direct JNI (JVM flags per the project rules):
    // --enable-native-access=ALL-UNNAMED on every OS, -XstartOnFirstThread on macOS.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED") +
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) listOf("-XstartOnFirstThread")
        else emptyList()
}
