import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val aholoKey: String = (localProps.getProperty("aholo.api.key") ?: "").trim()
val subjectEnabled = !project.hasProperty("noSubject")

android {
    namespace = "com.shiwu.scan3d"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shiwu.scan3d"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEFAULT_API_KEY", "\"$aholoKey\"")
        buildConfigField("boolean", "SUBJECT_ENABLED", subjectEnabled.toString())

        // MediaPipe 的 native 库每个 ABI 各带一份，只保留真机在用的 64 位 ARM。
        ndk {
            abiFilters += "arm64-v8a"
            // 本机 x86 模拟器调试用；正式包不带。
            if (project.hasProperty("withX86")) abiFilters += "x86"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.webkit:webkit:1.12.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 类别无关的主体分割（MagicTouch，只用来取主体外接框）
    implementation("com.google.mediapipe:tasks-vision:0.10.21")

    // 本地引擎：OpenCV 做特征匹配与相机位姿估计（增量式 SfM）
    implementation("org.opencv:opencv:4.10.0")

    // 端上离线出模：TripoSR 拆成两张 ONNX 图，fp16 权重放在应用私有目录
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
