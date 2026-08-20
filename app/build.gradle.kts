import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 서명 정보는 local.properties에 둔다. 이 파일은 저장소에 올라가지 않는다
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.wemade.smartnoti"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val store = localProps.getProperty("releaseStoreFile")
            if (store != null && file(store).exists()) {
                storeFile = file(store)
                storePassword = localProps.getProperty("releaseStorePassword")
                keyAlias = localProps.getProperty("releaseKeyAlias")
                keyPassword = localProps.getProperty("releaseKeyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.wemade.smartnoti"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "0.20.0"
    }

    buildTypes {
        release {
            // 키가 없는 곳에서 받아도 빌드는 되게 둔다. 그때는 서명 없이 나온다
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // 업데이트 확인에서 현재 버전(VERSION_NAME)을 읽는다
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
