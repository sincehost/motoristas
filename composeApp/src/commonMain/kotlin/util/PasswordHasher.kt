package util

/**
 * PasswordHasher — Utilitário para hash de senhas
 *
 * Usa expect/actual para cada plataforma implementar SHA-256.
 * NUNCA armazena senha em texto puro no banco local.
 */
expect object PasswordHasher {
    /**
     * Gera hash SHA-256 da senha.
     * Retorna string hexadecimal de 64 caracteres.
     */
    fun hash(password: String): String

    /**
     * Verifica se a senha corresponde ao hash armazenado.
     */
    fun verify(password: String, storedHash: String): Boolean
}
