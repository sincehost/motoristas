package util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate

/**
 * Wrapper do Keychain do iOS (Security framework) pra guardar strings
 * sensíveis (token de sessão, URL da API) — em vez de NSUserDefaults em
 * texto puro, que entra em backups do iTunes/iCloud e é legível por
 * qualquer processo com acesso ao sandbox do app.
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

        // Tenta atualizar um item já existente primeiro — SecItemAdd falha
        // com erro de duplicidade se o item já existir.
        val updateQuery = baseQuery(key)
        val attributesToUpdate = NSMutableDictionary().apply {
            setObject(data, forKey = kSecValueData)
        }
        val updateStatus = SecItemUpdate(
            updateQuery as CFDictionaryRef,
            attributesToUpdate as CFDictionaryRef
        )
        if (updateStatus == errSecSuccess) return

        val addQuery = baseQuery(key).apply {
            setObject(data, forKey = kSecValueData)
        }
        SecItemAdd(addQuery as CFDictionaryRef, null)
    }

    fun get(key: String): String? {
        val query = baseQuery(key).apply {
            setObject(true, forKey = kSecReturnData)
            setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
        }
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            @Suppress("UNCHECKED_CAST")
            val data = result.value as? NSData ?: return@memScoped null
            NSString.create(data, NSUTF8StringEncoding) as String?
        }
    }

    fun delete(key: String) {
        SecItemDelete(baseQuery(key) as CFDictionaryRef)
    }
}
