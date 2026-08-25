import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aripd.norda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aripd.norda"
        minSdk = 26          // Android 8.0 — foreground service gerekleri
        // Android 15. Bu seviyeden itibaren kenardan kenara çizim zorunlu;
        // pencere boşluklarını uygulama kendisi bırakıyor (MainActivity.applyInsets).
        targetSdk = 35
        // versionCode = MAJOR×10000 + MINOR×100 + PATCH (docs/MVP.md, 15.1)
        versionCode = 903
        versionName = "0.9.3"
    }

    /**
     * İsteğe bağlı: kendi anahtarınızla imzalı release APK.
     *
     * Bilgiler iki yerden gelebilir. Yerelde kök dizindeki `keystore.properties`
     * okunur. CI'da o dosya yoktur (anahtar da parola da depoya girmez), orada
     * ortam değişkenleri kullanılır. İkisi de yoksa blok sessizce atlanır:
     * release APK imzasız çıkar, debug APK yine üretilir.
     *
     * CI'da dosya yerine ortam değişkeni okunmasının sebebi `.properties`
     * biçiminin kendisi: ters bölü orada kaçış karakteridir ve iki nokta ile
     * eşittir anahtarı bitirir. İçinde bunlardan biri geçen bir parola dosyaya
     * yazıldığında sessizce başka bir parolaya dönüşür ve imzalama "parola
     * yanlış" diyerek kırılır. Ortam değişkeninde böyle bir yorumlama yok.
     */
    val signingValues: Map<String, String>? = run {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val props = Properties().apply { propsFile.inputStream().use { load(it) } }
            props.getProperty("storeFile")?.let { store ->
                mapOf(
                    "storeFile" to store,
                    "storePassword" to props.getProperty("storePassword").orEmpty(),
                    "keyAlias" to props.getProperty("keyAlias").orEmpty(),
                    "keyPassword" to props.getProperty("keyPassword").orEmpty()
                )
            }
        } else {
            System.getenv("NORDA_KEYSTORE_FILE")?.let { store ->
                mapOf(
                    "storeFile" to store,
                    "storePassword" to System.getenv("NORDA_KEYSTORE_PASSWORD").orEmpty(),
                    "keyAlias" to System.getenv("NORDA_KEY_ALIAS").orEmpty(),
                    "keyPassword" to System.getenv("NORDA_KEY_PASSWORD").orEmpty()
                )
            }
        }
    }

    if (signingValues != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingValues["storeFile"]!!)
                storePassword = signingValues["storePassword"]
                keyAlias = signingValues["keyAlias"]
                keyPassword = signingValues["keyPassword"]
                // Üç şemayla da imzala: bazı OEM ROM'ları yalnızca v2 ile
                // imzalı APK'ları "Uygulama yüklenmedi" diyerek reddedebiliyor.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8: kullanılmayan kodu ve kaynakları atar, kalanı küçültür.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingValues != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Uygulama tarafında bilinçli olarak hiçbir dış bağımlılık yok: sadece Android
// SDK + Kotlin stdlib. Aşağıdaki yalnızca testlerde kullanılır ve APK'ya girmez.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
