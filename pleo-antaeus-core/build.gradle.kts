plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    implementation("org.jetbrains.exposed:exposed:0.12.1")
    compile(project(":pleo-antaeus-models"))
    compile("org.knowm:sundial:2.2.0")
}