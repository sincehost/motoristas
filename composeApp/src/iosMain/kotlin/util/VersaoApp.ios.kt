package util

import platform.Foundation.NSBundle

actual fun buildAtualApp(): Long =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L

actual fun versaoAtualApp(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
