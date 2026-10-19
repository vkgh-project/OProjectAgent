plugins { `java-library` }
dependencies {
    api(project(":bs-api"))
    api(project(":bs-math"))
    api(project(":bs-terrain"))
    api(project(":bs-features"))
    api(project(":bs-nav"))
    api(project(":bs-weather"))
    api(project(":bs-combat"))
    api(project(":bs-movement"))
    api(project(":bs-scenario"))
    api(project(":bs-engine"))
    api(project(":bs-session"))
}
