package com.indeavour.coreader

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.initialize

class CoReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        // Must be installed before any Firebase AI call so requests carry an App Check token.
        // The provider differs per build type; see AppCheckProvider.kt in src/debug and src/release.
        Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
