plugins {
    `java-library`
    application
}

val lwjglVersion = "3.3.3"
val jomlVersion = "1.10.9"

dependencies {
    api(project(":bs-api"))
    api(project(":bs-gfx-core"))

    implementation("org.joml:joml:$jomlVersion")
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")

    listOf("natives-windows", "natives-linux", "natives-macos").forEach { native ->
        runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$native")
        runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$native")
        runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$native")
    }
}

application {
    mainClass.set("ua.battlesim.gfx.units.internal.DemoMain")
}

tasks.withType<Test> {
    maxHeapSize = "256m"
}
