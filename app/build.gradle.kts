plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xiaofu.daily"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xiaofu.daily"
        minSdk = 28
        targetSdk = 37
        versionCode = 11
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // release 复用本机 debug keystore（自测签名）：
    // keystore 位置跟随 ANDROID_USER_HOME，未设置时回退 ~/.android。
    // 不可用于应用商店发布；与 debug 同证书，可直接覆盖安装、数据保留。
    signingConfigs {
        create("selfTest") {
            val androidUserHome = System.getenv("ANDROID_USER_HOME")
                ?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.android")
            storeFile = file("$androidUserHome/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("selfTest")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // 迁移测试需要使用导出的 schema 快照
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        // Gradle 9.x 已移除 kotlinOptions DSL，用 compilerOptions 替代
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room 导出 schema，供迁移测试与版本追踪
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Tab 图标所需；显式声明避免经传递依赖在 runtime classpath 丢失
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    // Room 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 到期提醒（每日检查一次，09:00 触发）
    implementation(libs.androidx.work.runtime.ktx)

    // 真机 DAO 集成测试（无模拟器环境，instrumentation 测试跑在真机上）
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
