package com.galeria.ui.screens.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galeria.R

// --------------------------------------------------------------------------------------------
// Preencha com as informações reais antes de publicar. Deixei tudo reunido aqui em cima pra
// não precisar caçar espalhado pelo código.
// --------------------------------------------------------------------------------------------
private const val DEVELOPER_NAME = "Seu nome aqui"
private const val SUPPORT_EMAIL = "seuemail@exemplo.com"
// Troque pelo link real, por exemplo uma página do GitHub Pages do repositório do app.
private const val PRIVACY_POLICY_URL = "https://github.com/SEU_USUARIO/Galeria"
private val TELEGRAM_URL: String? = null // ex.: "https://t.me/seu_usuario" -- deixe null se não usar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        } catch (_: Exception) {
            "-"
        }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sobre") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.res.colorResource(R.color.ic_launcher_background)),
                        contentAlignment = Alignment.Center
                    ) {
                        // R.mipmap.ic_launcher é um adaptive-icon XML (camadas de fundo +
                        // frente + monocromático) -- painterResource/Image não conseguem
                        // carregar esse formato diretamente e travavam o app ao abrir essa
                        // tela. O PNG de frente sozinho é uma imagem comum, segura de exibir.
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(84.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Versão $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Desenvolvido por $DEVELOPER_NAME",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Divider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Política de privacidade") },
                    supportingContent = { Text("O app não coleta nem envia suas fotos para a internet") },
                    leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
                    modifier = Modifier.clickable { openUrl(PRIVACY_POLICY_URL) }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Contato") },
                    supportingContent = { Text(SUPPORT_EMAIL) },
                    leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$SUPPORT_EMAIL")
                            putExtra(Intent.EXTRA_SUBJECT, "$appName — contato")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    }
                )
            }

            val telegramUrl = TELEGRAM_URL
            if (telegramUrl != null) {
                item {
                    ListItem(
                        headlineContent = { Text("Telegram") },
                        supportingContent = { Text("Reporte bugs ou envie sugestões") },
                        leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                        modifier = Modifier.clickable { openUrl(telegramUrl) }
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Avaliar o aplicativo") },
                    leadingContent = { Icon(Icons.Filled.RateReview, contentDescription = null) },
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            )
                        } catch (_: ActivityNotFoundException) {
                            openUrl("https://play.google.com/store/apps/details?id=${context.packageName}")
                        }
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Compartilhar aplicativo") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Experimenta o $appName: https://play.google.com/store/apps/details?id=${context.packageName}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar $appName"))
                    }
                )
            }
        }
    }
}
