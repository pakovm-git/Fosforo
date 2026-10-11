plugins { id("com.android.application") }

android {
    namespace = "dev.pako.bfi"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.pako.bfi"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.6.4"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

dependencies {
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    testImplementation("junit:junit:4.13.2")
}
