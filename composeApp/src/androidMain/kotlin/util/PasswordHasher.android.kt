package util

import java.security.MessageDigest

/**
 * PasswordHasher — Implementação Android (java.security)
 */
actual object PasswordHasher {

    actual fun hash(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    actual fun verify(password: String, storedHash: String): Boolean {
        return hash(password) == storedHash
    }
}
