package com.galeria.data.repository

import com.galeria.data.db.SecurityDao
import com.galeria.data.model.SecurityEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Resultado de uma tentativa de desbloqueio do cofre. */
sealed interface UnlockResult {
    object Success : UnlockResult

    /** Ainda não há senha cadastrada. */
    object NotConfigured : UnlockResult

    /** Senha errada. [attemptsLeft] = tentativas livres antes do 1º bloqueio (0 se já bloqueou). */
    class Failed(val attemptsLeft: Int, val lockedForMs: Long) : UnlockResult

    /** Tentativa recusada sem nem comparar a senha: o cofre está em bloqueio por tentativas. */
    class Locked(val remainingMs: Long) : UnlockResult
}

class SecurityRepository(private val dao: SecurityDao) {

    // Impede duas verificações simultâneas lerem o mesmo contador de tentativas e gravarem o
    // mesmo valor (o que deixaria o atacante "emprestar" tentativas em paralelo).
    private val mutex = Mutex()

    suspend fun getConfig(): SecurityEntity? = dao.get()

    suspend fun hasPassword(): Boolean = dao.get()?.passwordHash != null

    suspend fun setPassword(password: String) = mutex.withLock {
        val previous = dao.get()
        val salt = generateSalt()
        val hash = withContext(Dispatchers.Default) { derive(password, salt, CURRENT_ITERATIONS) }
        dao.set(
            SecurityEntity(
                passwordHash = hash,
                salt = salt,
                biometricEnabled = previous?.biometricEnabled ?: false,
                iterations = CURRENT_ITERATIONS,
                failedAttempts = 0,
                lockedUntil = 0L
            )
        )
    }

    /** Quanto falta (ms) para o bloqueio por tentativas acabar; 0 se não está bloqueado. */
    suspend fun lockRemainingMs(): Long = remainingLock(dao.get(), System.currentTimeMillis())

    suspend fun verifyPassword(password: String): UnlockResult = mutex.withLock {
        val config = dao.get() ?: return@withLock UnlockResult.NotConfigured
        val salt = config.salt ?: return@withLock UnlockResult.NotConfigured
        val expected = config.passwordHash ?: return@withLock UnlockResult.NotConfigured

        val now = System.currentTimeMillis()
        val remaining = remainingLock(config, now)
        if (remaining > 0) return@withLock UnlockResult.Locked(remaining)

        val matches = withContext(Dispatchers.Default) {
            constantTimeEquals(derive(password, salt, config.iterations), expected)
        }

        if (matches) {
            var updated = config.copy(failedAttempts = 0, lockedUntil = 0L)
            // Migra o hash antigo (ou de custo menor) para o atual, já que só agora temos a
            // senha em texto para recalcular.
            if (config.iterations < CURRENT_ITERATIONS) {
                val newSalt = generateSalt()
                val newHash = withContext(Dispatchers.Default) { derive(password, newSalt, CURRENT_ITERATIONS) }
                updated = updated.copy(passwordHash = newHash, salt = newSalt, iterations = CURRENT_ITERATIONS)
            }
            dao.set(updated)
            UnlockResult.Success
        } else {
            val attempts = config.failedAttempts + 1
            val lockMs = lockDurationFor(attempts)
            dao.set(
                config.copy(
                    failedAttempts = attempts,
                    lockedUntil = if (lockMs > 0) now + lockMs else 0L
                )
            )
            UnlockResult.Failed(
                attemptsLeft = (FREE_ATTEMPTS - attempts).coerceAtLeast(0),
                lockedForMs = lockMs
            )
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        val current = dao.get() ?: SecurityEntity()
        dao.set(current.copy(biometricEnabled = enabled))
    }

    suspend fun isBiometricEnabled(): Boolean = dao.get()?.biometricEnabled ?: false

    // 5 erros livres; do 5º em diante cada erro trava por 30 s, 1 min, 2 min, 4 min, 8 min e
    // 15 min (teto). O contador só zera com um acerto.
    private fun lockDurationFor(attempts: Int): Long {
        if (attempts < FREE_ATTEMPTS) return 0L
        val step = (attempts - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_LOCK_MS shl step).coerceAtMost(MAX_LOCK_MS)
    }

    private fun remainingLock(config: SecurityEntity?, now: Long): Long {
        val until = config?.lockedUntil ?: return 0L
        // Nunca devolve mais que o bloqueio máximo: se o relógio do aparelho foi voltado pra
        // trás, o bloqueio gravado ficaria absurdamente longo.
        return (until - now).coerceIn(0L, MAX_LOCK_MS)
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun derive(password: String, salt: String, iterations: Int): String {
        if (iterations <= 0) return legacySha256(password, salt)
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return bytes.joinToString("") { "%02x".format(it) }
        } finally {
            spec.clearPassword()
        }
    }

    // Formato original (versões do banco até 4). Mantido só para validar senhas já gravadas.
    private fun legacySha256(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private companion object {
        const val CURRENT_ITERATIONS = 150_000
        const val KEY_BITS = 256
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 15 * 60_000L
    }
}
