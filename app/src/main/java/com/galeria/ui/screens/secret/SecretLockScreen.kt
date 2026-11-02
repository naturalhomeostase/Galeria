package com.galeria.ui.screens.secret

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.galeria.data.repository.UnlockResult
import com.galeria.ui.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SecretLockScreen(
    viewModel: GalleryViewModel,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricAvailable by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Bloqueio por excesso de tentativas erradas. lockedUntil é um prazo (relógio do aparelho);
    // `now` é atualizado a cada meio segundo só enquanto o bloqueio está valendo, pra mostrar a
    // contagem regressiva e reabilitar o botão sozinho quando acabar.
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        val remaining = viewModel.secretLockRemainingMs()
        if (remaining > 0) lockedUntil = System.currentTimeMillis() + remaining
    }
    LaunchedEffect(lockedUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= lockedUntil) break
            delay(500)
        }
    }
    val remainingSeconds = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    val isLocked = remainingSeconds > 0

    LaunchedEffect(Unit) {
        val enabled = viewModel.isBiometricEnabled()
        val manager = BiometricManager.from(context)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        biometricAvailable = enabled && canAuth
        if (biometricAvailable && activity != null) {
            showBiometricPrompt(activity, onSuccess = onUnlocked)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Álbum secreto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLocked) {
            Text(
                "Muitas tentativas erradas. Tente de novo em ${formatCountdown(remainingSeconds)}.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            enabled = !isLocked && !checking && password.isNotEmpty(),
            onClick = {
                scope.launch {
                    checking = true
                    when (val result = viewModel.verifySecretPassword(password)) {
                        is UnlockResult.Success -> onUnlocked()
                        is UnlockResult.Failed -> {
                            password = ""
                            if (result.lockedForMs > 0) {
                                lockedUntil = System.currentTimeMillis() + result.lockedForMs
                                error = null
                            } else {
                                error = if (result.attemptsLeft > 0) {
                                    "Senha incorreta. Restam ${result.attemptsLeft} tentativa(s) antes do bloqueio."
                                } else {
                                    "Senha incorreta"
                                }
                            }
                        }
                        is UnlockResult.Locked -> lockedUntil = System.currentTimeMillis() + result.remainingMs
                        is UnlockResult.NotConfigured -> error = "Nenhuma senha cadastrada"
                    }
                    checking = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Entrar") }

        if (biometricAvailable && activity != null) {
            OutlinedButton(
                onClick = { showBiometricPrompt(activity, onSuccess = onUnlocked) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Text("  Usar biometria")
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Cancelar")
        }
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}min ${"%02d".format(seconds)}s" else "${seconds}s"
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear álbum secreto")
        .setSubtitle("Use sua digital ou rosto cadastrado")
        .setNegativeButtonText("Usar senha")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}
