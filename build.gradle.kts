plugins { base }
allprojects {
    group = "ua.battlesim"
    version = "0.1.0"
    repositories { mavenCentral() }
}
subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(24)) }
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
tasks.named("check") { dependsOn(subprojects.map { it.tasks.named("check") }) }
