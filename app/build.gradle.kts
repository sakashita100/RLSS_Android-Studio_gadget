plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapp"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}



dependencies {




    implementation ("androidx.compose.runtime:runtime-saveable:1.3.0")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    implementation("androidx.compose.material:material:1.5.4") // バージョンはComposeに合わせて
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.activity:activity-compose:1.7.2")

    //implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // build.gradle (Module: app)
    //implementation("io.github.crownpku.paho:org.eclipse.paho.android.service:1.1.1")
    //implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    //("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.6")  // 別のバージョンを試す

    // implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.4")
    // implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")


    /* NOP: Use the client module as is */
    //implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    /* DEL: Comment out the service module until fixed */
    //implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")

    /* ADD: A temporary fix for the Paho Android Service */
    //  implementation("net.sinetstream:pahomqttandroid-bugfix:1.0.0")

    //implementation("com.github.amitshekhariitbhu.Fast-Android-Networking:android-networking:1.0.4")
    /*
    implementation("com.github.amitshekhariitbhu.Fast-Android-Networking:android-networking:1.0.4")
    implementation("com.github.amitshekhariitbhu.Fast-Android-Networking:jackson-android-networking:1.0.4")
    implementation("com.github.amitshekhariitbhu.Fast-Android-Networking:rx2-android-networking:1.0.4")
    implementation("com.github.amitshekhariitbhu.Fast-Android-Networking:rx-android-networking:1.0.4")
*/

    implementation ("javax.annotation:javax.annotation-api:1.3.2")


    implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation ("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation("androidx.appcompat:appcompat:1.7.0")



    //implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    //implementation ("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")

    implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:+")



    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:+")
    androidTestImplementation("androidx.test.espresso:espresso-core:+")
    //androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}
