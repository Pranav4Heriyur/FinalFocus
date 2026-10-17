package com.pranav.focus.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens and API secrets: AES-GCM with a hardware-backed Android Keystore key, kept in their own prefs file. */
object Secure {
    private const val ALIAS = "focus_secure_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("focus_secure", Context.MODE_PRIVATE)

    fun put(ctx: Context, name: String, value: String) {
        if (value.isEmpty()) { remove(ctx, name); return }
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(value.toByteArray())
            prefs(ctx).edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        }
    }

    fun get(ctx: Context, name: String): String {
        val s = prefs(ctx).getString(name, null) ?: return ""
        return runCatching {
            val raw = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        }.getOrDefault("")
    }

    fun remove(ctx: Context, name: String) = prefs(ctx).edit().remove(name).apply()
    fun clearAll(ctx: Context) = prefs(ctx).edit().clear().apply()
}
