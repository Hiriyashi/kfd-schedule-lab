plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
kotlin {
    jvmToolchain(25)
    compilerOptions { allWarningsAsErrors.set(true) }
}
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
application {
    mainClass.set("schedule.MainKt")
    applicationName = "schedule"
}
tasks.test {
    useJUnitPlatform()
    systemProperty("repo.root", rootProject.projectDir.absolutePath)
    testLogging { events("passed", "skipped", "failed") }
}
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
