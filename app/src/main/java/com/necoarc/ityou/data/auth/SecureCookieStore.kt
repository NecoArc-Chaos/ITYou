package com.necoarc.ityou.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 可插拔的文本加解密接口，便于在 Android Keystore 与单元测试实现间解耦。
 */
interface StringCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

/**
 * 基于 Android Keystore (AES-256-GCM) 的硬件级密钥加解密器（API 26+ 原生支持）。
 */
class AndroidKeystoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : StringCipher {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // 拼接格式：[1字节 IV长度][IV字节][密文+GCM Tag]
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        return Base64.getEncoder().encodeToString(payload)
    }

    override fun decrypt(cipherText: String): String {
        val payload = Base64.getDecoder().decode(cipherText)
        require(payload.size > 2) { "Invalid encrypted payload" }
        val ivLength = payload[0].toInt() and 0xFF
        require(ivLength in 12..16 && payload.size > 1 + ivLength) { "Invalid GCM IV length" }

        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val DEFAULT_KEY_ALIAS = "ityou_auth_cookie_aes_key"
    }
}

/**
 * 持久化 Cookie 记录模型。
 */
data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean
)

/**
 * 加密 Cookie 存储器：将用户会话 Cookie 通过 AES-GCM 加密后持久化到私有 SharedPreferences。
 */
class SecureCookieStore(
    private val prefs: SharedPreferences?,
    private val cipher: StringCipher
) {

    fun loadAll(): List<StoredCookie> {
        val encrypted = prefs?.getString(KEY_COOKIES_CIPHER, null) ?: return emptyList()
        return runCatching {
            val json = cipher.decrypt(encrypted)
            decodeCookies(json)
        }.getOrElse {
            // Keystore 异常或密文损坏时安全清空，避免应用启动崩溃
            clear()
            emptyList()
        }
    }

    fun saveAll(cookies: List<StoredCookie>) {
        if (prefs == null) return
        if (cookies.isEmpty()) {
            clear()
            return
        }
        runCatching {
            val json = encodeCookies(cookies)
            val encrypted = cipher.encrypt(json)
            prefs.edit().putString(KEY_COOKIES_CIPHER, encrypted).apply()
        }
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_COOKIES_CIPHER)?.apply()
    }

    internal fun encodeCookies(cookies: List<StoredCookie>): String {
        val array = JSONArray()
        cookies.forEach { c ->
            array.put(
                JSONObject().apply {
                    put("name", c.name)
                    put("value", c.value)
                    put("expiresAt", c.expiresAt)
                    put("domain", c.domain)
                    put("path", c.path)
                    put("secure", c.secure)
                    put("httpOnly", c.httpOnly)
                    put("hostOnly", c.hostOnly)
                }
            )
        }
        return array.toString()
    }

    internal fun decodeCookies(json: String): List<StoredCookie> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val domain = obj.optString("domain")
                if (name.isBlank() || domain.isBlank()) continue
                add(
                    StoredCookie(
                        name = name,
                        value = obj.optString("value"),
                        expiresAt = obj.optLong("expiresAt", Long.MAX_VALUE),
                        domain = domain,
                        path = obj.optString("path", "/"),
                        secure = obj.optBoolean("secure", false),
                        httpOnly = obj.optBoolean("httpOnly", false),
                        hostOnly = obj.optBoolean("hostOnly", false)
                    )
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "ityou_auth_secure"
        private const val KEY_COOKIES_CIPHER = "cookies_aes_gcm"

        fun create(context: Context): SecureCookieStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SecureCookieStore(prefs = prefs, cipher = AndroidKeystoreCipher())
        }

        /** 供单元测试使用的无持久化内存存储 */
        fun inMemory(cipher: StringCipher): SecureCookieStore =
            SecureCookieStore(prefs = null, cipher = cipher)
    }
}
