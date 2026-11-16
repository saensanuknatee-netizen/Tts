package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class AppCheckDiagnosticState(
    val initialized: Boolean = false,
    val mode: String = "UNINITIALIZED",
    val tokenConfigured: Boolean = false,
    val statusMessage: String = "กำลังรอการเริ่มต้น Firebase App Check..."
)

object AppCheckStatusHolder {
    private val _state = MutableStateFlow(AppCheckDiagnosticState())
    val state: StateFlow<AppCheckDiagnosticState> = _state.asStateFlow()

    fun update(state: AppCheckDiagnosticState) {
        _state.value = state
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // CRITICAL: MUST use commit() instead of apply() so the secret is written
                // synchronously before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            AppCheckStatusHolder.update(
                AppCheckDiagnosticState(
                    initialized = true,
                    mode = "DEBUG_PROVIDER",
                    tokenConfigured = !debugToken.isNullOrEmpty(),
                    statusMessage = if (!debugToken.isNullOrEmpty()) {
                        "DebugAppCheckProvider พร้อมใช้งาน (รับ Debug Token จาก Intent แล้ว)"
                    } else {
                        "DebugAppCheckProvider ติดตั้งแล้ว (ใช้ Debug Secret ในเครื่อง)"
                    }
                )
            )
        } else {
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    AppCheckStatusHolder.update(
                        AppCheckDiagnosticState(
                            initialized = true,
                            mode = "RECAPTCHA_ENTERPRISE",
                            tokenConfigured = true,
                            statusMessage = "RecaptchaAppCheckProviderFactory พร้อมใช้งาน"
                        )
                    )
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    AppCheckStatusHolder.update(
                        AppCheckDiagnosticState(
                            initialized = false,
                            mode = "RELEASE_MISSING_KEY",
                            tokenConfigured = false,
                            statusMessage = "ไม่พบค่า recaptcha_site_key ใน strings.xml"
                        )
                    )
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                AppCheckStatusHolder.update(
                    AppCheckDiagnosticState(
                        initialized = false,
                        mode = "RELEASE_NO_RES",
                        tokenConfigured = false,
                        statusMessage = "ไม่พบทรัพยากร recaptcha_site_key"
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Log explicitly to keep App Check attestation failures visible
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckStatusHolder.update(
            AppCheckDiagnosticState(
                initialized = false,
                mode = "ERROR",
                tokenConfigured = false,
                statusMessage = "การเริ่มต้น App Check ล้มเหลว: ${e.localizedMessage}"
            )
        )
    }
}
