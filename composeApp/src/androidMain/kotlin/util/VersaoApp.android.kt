package util

import br.com.lfsystem.app.BuildConfig

actual fun buildAtualApp(): Long = BuildConfig.VERSION_CODE.toLong()

actual fun versaoAtualApp(): String = BuildConfig.VERSION_NAME
