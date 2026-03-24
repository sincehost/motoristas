package util

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/**
 * PasswordHasher — Implementação iOS (CommonCrypto)
 */
@OptIn(ExperimentalForeignApi::class)
actual object PasswordHasher {

    actual fun hash(password: String): String {
        val data = password.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

        data.usePinned { pinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(
                    pinned.addressOf(0),
                    data.size.toUInt(),
                    digestPinned.addressOf(0)
                )
            }
        }

        return digest.joinToString("") { it.toString(16).padStart(2, '0') }
    }

    actual fun verify(password: String, storedHash: String): Boolean {
        return hash(password) == storedHash
    }
}
