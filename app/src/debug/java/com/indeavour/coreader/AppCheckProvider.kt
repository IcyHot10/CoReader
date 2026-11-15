package com.indeavour.coreader

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the debug provider. On first run of each install it logs a debug secret
 * (Logcat tag "DebugAppCheckProvider": "Enter this debug secret into the allow list...") that must be
 * registered in the Firebase console under App Check > Apps > Manage debug tokens.
 */
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
