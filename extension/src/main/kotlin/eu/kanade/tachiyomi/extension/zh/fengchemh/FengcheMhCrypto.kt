package eu.kanade.tachiyomi.extension.zh.fengchemh

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helpers for 风车漫画.
 *
 * Two independent AES-128-CBC schemes are used by the site:
 *  1. per-chapter `params` blob  -> key "9S8\$vJnU2ANeSRoF", IV = first 16 bytes of payload
 *  2. encrypted images (source_id == 12) -> key = IV = "my2ecret782ecret"
 *
 * The site script's extra "bit rearrangement" after image decryption is just the CryptoJS
 * WordArray -> big-endian byte conversion, i.e. the natural byte order of the AES
 * plaintext, so no extra shuffling is required here.
 */
object FengcheMhCrypto {

    /** AES-128-CBC key for the per-chapter params blob. */
    private const val PARAMS_KEY = "9S8\$vJnU2ANeSRoF"

    /** AES-128-CBC key (also used as IV) for source_id == 12 images. */
    private const val IMAGE_KEY = "my2ecret782ecret"

    /**
     * Decrypts the base64 `params` payload found on a chapter page.
     *
     * Layout: base64-decode -> first 16 bytes = IV, remainder = AES-128-CBC ciphertext
     * (PKCS7 padding). Returns the plaintext JSON object, e.g.
     * `{"host":"www.fengchemh.com","source_id":"10","images":[...],...}`.
     */
    fun decryptParams(enc: String): JSONObject {
        val full = Base64.decode(enc, Base64.DEFAULT)
        require(full.size > 16) { "params payload too short" }
        val iv = full.copyOfRange(0, 16)
        val ciphertext = full.copyOfRange(16, full.size)
        val plaintext = aesCbcDecrypt(ciphertext, PARAMS_KEY.toByteArray(Charsets.UTF_8), iv)
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    /** Decrypts a source_id == 12 image payload into the original image bytes. */
    fun decryptImage(ciphertext: ByteArray): ByteArray {
        val key = IMAGE_KEY.toByteArray(Charsets.UTF_8)
        return aesCbcDecrypt(ciphertext, key, key)
    }

    private fun aesCbcDecrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }
}
