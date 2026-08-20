package br.com.lfsystem.app

import push.PushTokenManager

/**
 * Ponte chamada pelo AppDelegate.swift (MessagingDelegate.didReceiveRegistrationToken)
 * — exposta ao Swift automaticamente como `PushTokenBridge.shared` (todo
 * `object` Kotlin vira um singleton com `.shared` na interop com Obj-C/Swift).
 */
object PushTokenBridge {
    fun onTokenReceived(token: String) {
        PushTokenManager.setToken(token)
    }
}
