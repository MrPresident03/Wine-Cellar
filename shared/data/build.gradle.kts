import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "com.houseofvino.data"
        compileSdk = 37
        minSdk = 26
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core)
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("HouseOfVinoDatabase") {
            packageName.set("com.houseofvino.data.db")
            dialect(libs.sqldelight.dialect)
            // Migrations in src/commonMain/sqldelight/migrations are the single source of truth for the schema.
            srcDirs.setFrom("src/commonMain/sqldelight")
            deriveSchemaFromMigrations.set(true)
            verifyMigrations.set(false)
            generateAsync.set(false)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    maxHeapSize = "2g"
}
