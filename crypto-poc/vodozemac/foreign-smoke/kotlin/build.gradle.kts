plugins {
    kotlin("jvm") version "2.2.21"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("net.java.dev.jna:jna:5.17.0")
}

sourceSets {
    main {
        kotlin.srcDir("../../generated/kotlin")
    }
}
