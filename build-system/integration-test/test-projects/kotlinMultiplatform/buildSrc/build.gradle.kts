plugins {
  kotlin("jvm") version libs.versions.kotlinVersion.get()
  `java-gradle-plugin`
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlinVersion.get()}")
  implementation("com.android.tools.build:gradle:${libs.versions.buildVersion.get()}")
  implementation("com.google.code.gson:gson:2.8.6")
  implementation("com.google.protobuf:protobuf-java-util:3.21.8")
  implementation(gradleApi())
}

gradlePlugin {
  plugins {
    create("androidTargetDumper") {
      id = "dumpAndroidTarget"
      implementationClass = "com.buildsrc.plugin.DumpAndroidTargetPlugin"
    }
  }
}
