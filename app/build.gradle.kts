plugins {
  alias(libs.plugins.android.application)
}

import java.util.Properties

android {
  namespace = "com.example.portalphotoframe"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example.portalphotoframe"
    // Portal max compatibility — see https://developers.meta.com/horizon/documentation/android-apps/portal-development
    minSdk = 28
    targetSdk = 29
    versionCode = 4
    versionName = "1.0"
  }

  signingConfigs {
    create("portal") {
      val props = Properties()
      val propsFile = rootProject.file("signing/keystore.properties")
      if (propsFile.exists()) {
        props.load(propsFile.inputStream())
        storeFile = rootProject.file(props.getProperty("storeFile"))
        storePassword = props.getProperty("storePassword")
        keyAlias = props.getProperty("keyAlias")
        keyPassword = props.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("portal")
    }
    debug {
      signingConfig = signingConfigs.getByName("portal")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.nanohttpd)
  implementation(libs.gson)
  implementation(libs.androidx.exifinterface)
}
