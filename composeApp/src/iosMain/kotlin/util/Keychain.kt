package util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Armazenamento seguro (iOS Keychain) pro token de sessão e URL da API.
 *
 * Antes (MainViewController.kt) ficavam em NSUserDefaults — um plist em
 * texto puro, legível por qualquer processo com acesso ao sandbox do app
 * (extração forense, dispositivo jailbroken) e incluído em backups do
 * iTunes/iCloud. O Keychain é isolado por hardware e não entra em backup
 * de terceiros pra itens como este. Mesmo princípio da correção já feita
 * no Android (EncryptedSharedPreferences).
 */
@OptIn(ExperimentalForeignApi::class)
object Keychain {
    private const val SERVICE = "br.com.lfsystem.app.Motorista"

    private fun baseQuery(key: String): NSMutableDictionary {
        return NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, forKey = kSecClass)
            setObject(SERVICE, forKey = kSecAttrService)
            setObject(key, forKey = kSecAttrAccount)
        }
    }

    fun set(key: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        // Remove item existente antes — SecItemAdd falha (errSecDuplicateItem)
        // se a chave já existir; mais simples que fazer update condicional.
        delete(key)
        val query = baseQuery(key).apply {
            setObject(data, forKey = kSecValueData)
        }
        SecItemAdd(query as CFDictionaryRef, null)
    }

    fun get(key: String): String? {
        val query = baseQuery(key).apply {
            setObject(kCFBooleanTrue, forKey = kSecReturnData)
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
        }
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = result.value as? NSData ?: return@memScoped null
            NSString.create(data, NSUTF8StringEncoding) as String?
        }
    }

    fun delete(key: String) {
        SecItemDelete(baseQuery(key) as CFDictionaryRef)
    }
}
