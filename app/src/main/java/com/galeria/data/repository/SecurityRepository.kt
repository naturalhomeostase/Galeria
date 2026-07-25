package com.galeria.data.repository

import com.galeria.data.db.SecurityDao
import com.galeria.data.model.SecurityEntity
import java.security.MessageDigest
import java.security.SecureRandom

class SecurityRepository(private val dao: SecurityDao) {

    suspend fun getConfig(): SecurityEntity? = dao.get()

    suspend fun hasPassword(): Boolean = dao.get()?.passwordHash != null

    suspend fun setPassword(password: String) {
        val salt = generateSalt()
        val hash = hash(password, salt)
        dao.set(SecurityEntity(passwordHash = hash, salt = salt, biometricEnabled = dao.get()?.biometricEnabled ?: false))
    }

    suspend fun verifyPassword(password: String): Boolean {
        val config = dao.get() ?: return false
        val salt = config.salt ?: return false
        val expected = config.passwordHash ?: return false
        return hash(password, salt) == expected
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        val current = dao.get() ?: SecurityEntity()
        dao.set(current.copy(biometricEnabled = enabled))
    }

    suspend fun isBiometricEnabled(): Boolean = dao.get()?.biometricEnabled ?: false

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
