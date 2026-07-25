# Galeria

App de galeria de fotos para Android, feito com Kotlin + Jetpack Compose.

## Funcionalidades
- Cores dinâmicas (Material You), seguindo o tema do sistema
- Fotos organizadas por mês, como o Google Fotos
- Álbuns próprios do app (não mexem nos arquivos originais nem na sincronização do Google Fotos)
- Álbum secreto protegido por senha e/ou biometria
- Favoritos com estrela
- Visualizador em tela cheia com zoom e swipe
- Editor de fotos: cortar, girar, espelhar, ajustes de brilho/contraste/saturação, filtros, desenho livre e texto
- Tela de informações da foto (nome, data, resolução, tamanho, formato, pasta)
- Compartilhar e excluir fotos

## Como gerar o APK
Este repositório tem um workflow do GitHub Actions (`.github/workflows/build.yml`) que compila
automaticamente um APK de debug a cada push para `main`/`master`, ou manualmente pela aba **Actions › Build APK › Run workflow**.
O APK fica disponível como artefato do workflow (`galeria-debug-apk`).
