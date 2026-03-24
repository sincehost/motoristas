package br.com.lfsystem.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform