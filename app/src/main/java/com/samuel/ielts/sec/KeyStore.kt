package com.samuel.ielts.sec

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apiKeyPrefs by preferencesDataStore(name = "ielts secrets")

/**
 * Meta API key storage. The key is encrypted with Tink (AES-256-GCM,
 * keyset guarded by an Android Keystore master key) and only the
 * ciphertext lands in DataStore. Plaintext exists only in memory.
 *
 * Never log [get] output. Use [masked] for any UI display.
 */
class ApiKeyStore(private val context: Context) {

    companion object {
        private const val KEYSET_PREF = "ielts_tink_keyset"
        private const val MASTER_KEY_URI = "android-keystore://ielts_master_key"
        private val ENC_KEY = stringPreferencesKey("meta_api_key_enc")
        private const val ASSOCIATED_DATA = "ielts-meta-api-key"

        @Volatile
        private var registered = false

        private fun ensureRegistered() {
            if (!registered) {
                synchronized(this) {
                    if (!registered) {
                        AeadConfig.register()
                        registered = true
                    }
                }
            }
        }
    }

    private fun aead(): Aead {
        ensureRegistered()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_PREF, KEYSET_PREF)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return handle.getPrimitive(Aead::class.java)
    }

    suspend fun save(rawKey: String) {
        val trimmed = rawKey.trim()
        require(trimmed.isNotEmpty()) { "API key must not be blank" }
        val ct = aead().encrypt(
            trimmed.toByteArray(Charsets.UTF_8),
            ASSOCIATED_DATA.toByteArray(Charsets.UTF_8)
        )
        val b64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        context.apiKeyPrefs.edit { it[ENC_KEY] = b64 }
    }

    suspend fun get(): String? {
        val b64 = context.apiKeyPrefs.data.map { it[ENC_KEY] }.first()
            ?: return null
        return try {
            val ct = Base64.decode(b64, Base64.NO_WRAP)
            val pt = aead().decrypt(
                ct, ASSOCIATED_DATA.toByteArray(Charsets.UTF_8)
            )
            String(pt, Charsets.UTF_8).ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clear() {
        context.apiKeyPrefs.edit { it.remove(ENC_KEY) }
    }

    /** Display form only, e.g. "key ends 9f2a". Empty when no key stored. */
    suspend fun masked(): String {
        val key = get() ?: return ""
        val tail = key.takeLast(4)
        return "ends $tail"
    }
}
