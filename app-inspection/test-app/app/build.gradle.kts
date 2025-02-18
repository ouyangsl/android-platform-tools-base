@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.kotlinAndroid)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sqldelight)
}

sqldelight {
  databases {
    create("SqlDelightDatabase") {
      packageName.set("com.google.test.inspectors")
    }
  }
}

android {
  namespace = "com.google.test.inspectors"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.google.test.inspectors"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.12" }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {

  implementation(project(":shared"))
  implementation(libs.activity.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.work)
  implementation(libs.core.ktx)
  implementation(libs.grpc.android)
  implementation(libs.grpc.core)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  implementation(libs.hilt.android)
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.material3)
  implementation(libs.okhttp)
  implementation(libs.sqldelight)
  implementation(libs.okhttp3)
  implementation(libs.ui)
  implementation(libs.ui.graphics)
  implementation(libs.ui.tooling.preview)
  implementation(platform(libs.compose.bom))

  kapt(libs.hilt.compiler)
  kapt(libs.androidx.hilt.compiler)
  ksp(libs.androidx.room.compiler)
  debugImplementation(libs.androidx.ui.tooling)
}
