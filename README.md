# MOVA

Aplicativo Android nativo para **reconhecer links, baixar e organizar mídia**,
pensado inicialmente para uso pessoal. Interface dark e minimalista, sem
anúncios, sem cadastro e sem servidor: tudo roda no aparelho.

> Status: **versão 0.2**: download das plataformas via yt-dlp (ver `docs/planejamento.md`, Atualização 2).
> Código proprietário. Todos os direitos reservados (a licença ainda será definida).

## O que já funciona

| Área | Situação |
| --- | --- |
| Colar link ou receber pelo menu **Compartilhar** do Android | ✅ Preenche o campo, sem iniciar o download sozinho |
| Reconhecimento automático da origem (YouTube, Facebook, Instagram, TikTok, Kwai, Pinterest, Vimeo) | ✅ Identifica plataforma e tipo (vídeo, publicação, playlist, Stories, perfil, link curto...) |
| Download dessas plataformas | ✅ Conteúdo público, para uso pessoal, pelo **yt-dlp executado no próprio aparelho**: vídeo até 1080p (MP4) ou somente áudio (MP3/M4A), playlists e publicações com vários itens. Instagram: parte do conteúdo exige login (opção futura); Kwai: experimental |
| Conteúdo privado, com DRM ou que exige login | 🚫 Não é contornado; o app explica o motivo e oferece abrir no app original |
| Download de **links diretos** de arquivos (ex.: `https://site.com/video.mp4`) | ✅ Com análise prévia (tipo, tamanho, nome) |
| Vários links de uma vez | ✅ Tela de seleção de itens (um, alguns ou todos) |
| Fila de downloads | ✅ Progresso, velocidade, pausar, retomar (quando o servidor permite), cancelar, tentar novamente |
| Downloads em segundo plano | ✅ Serviço em primeiro plano com notificação; retoma após queda de rede ou reinício |
| Preferências | ✅ Somente Wi-Fi, downloads simultâneos (1–3), pasta de destino, limpeza de temporários |
| Biblioteca e histórico | ✅ Filtros por tipo, abrir, compartilhar, excluir; histórico de concluídos/cancelados/com erro |
| Extração de áudio | ✅ MP3 ou M4A a partir do vídeo (FFmpeg) |
| Mecanismo sempre atualizado | ✅ O yt-dlp se atualiza sozinho 1x por dia (ou em Ajustes → Atualizar) |

Os arquivos são salvos em **Download/MOVA** (ou na pasta que você escolher em Ajustes).

## Instalar no celular

Cada versão é compilada pelo GitHub Actions e assinada **sempre com a mesma
chave**, então a nova versão **instala por cima** da anterior, mantendo seus
dados.

1. Abra a aba **Releases** do repositório no celular.
2. Baixe o arquivo `MOVA-x.y.z-buildN.apk` mais recente.
3. Toque no arquivo e permita "instalar apps desconhecidos" para o navegador, se pedido.

Requisitos: Android 10 ou superior, processador ARM 64 bits (praticamente todos os celulares atuais). O APK tem ~58 MB porque inclui o mecanismo de download (Python + FFmpeg).

### Configurar a assinatura fixa (uma única vez)

O APK de release só é gerado quando estes segredos existem em
**Settings → Secrets and variables → Actions → New repository secret**:

| Segredo | Conteúdo |
| --- | --- |
| `MOVA_KEYSTORE_BASE64` | o arquivo `mova-release.jks` convertido em base64 |
| `MOVA_KEYSTORE_PASSWORD` | senha do keystore |
| `MOVA_KEY_ALIAS` | `mova` |
| `MOVA_KEY_PASSWORD` | senha da chave (a mesma do keystore) |

O workflow confere a impressão digital do certificado
(`SHA-256 663cc93b…9e84`) e **falha** se o APK sair assinado com outra chave.
Assim, nunca é publicada uma versão que não instala por cima.

> ⚠️ **Guarde o `mova-release.jks` e as senhas em local seguro** (gerenciador de
> senhas e uma cópia fora do celular). Se a chave for perdida, novas versões não
> poderão ser instaladas por cima: seria preciso desinstalar o app (e perder o
> histórico) para instalar uma versão assinada com uma chave nova.
> A chave **nunca** deve ser enviada ao repositório (o `.gitignore` já bloqueia `*.jks`).

O `versionCode` vem do número da execução do workflow e sempre aumenta.

## Desenvolvimento

Requisitos: JDK 17+ e Android SDK (platform 37). O Gradle Wrapper baixa o resto.

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug   # testes, capturas de tela (app/build/outputs/roborazzi) e lint
./gradlew :app:assembleDebug                  # APK de desenvolvimento
./gradlew :app:assembleRelease                # APK assinado (precisa de keystore.properties ou das variáveis MOVA_*)
```

Para assinar localmente, crie `keystore.properties` na raiz (ignorado pelo git):

```properties
storeFile=/caminho/para/mova-release.jks
storePassword=...
keyAlias=mova
keyPassword=...
```

### Arquitetura

```
core/   Kotlin puro (sem Android) — pronto para ser compartilhado com a versão Windows
  link/       extração de links do texto, reconhecimento de plataforma e tipo de conteúdo
  provider/   capacidades de cada origem (o que é permitido e comprovado)
  analysis/   análise do link, opções contextuais e formatos (vídeo até 1080p / áudio)
  extractor/  contrato do mecanismo de extração + regras do yt-dlp (JSON, argumentos, progresso, erros)
  file/       nomes de arquivo seguros, tipos MIME
app/    Android (Kotlin + Jetpack Compose, MVVM)
  data/       Room (tarefas, biblioteca, histórico) e DataStore (preferências)
  download/   fila (WorkManager), HTTP com retomada, yt-dlp (YtDlpEngine), publicação no MediaStore/SAF, notificações
  ui/         design system, navegação e telas
tools/  gen_icon.py — gera os vetores do símbolo MOVA (colorido, monocromático, notificação)
```

Princípio central: **cada origem declara o que oferece** (`ProviderRegistry`) e a
interface só mostra opções que a análise confirmou (formatos e itens realmente
disponíveis).

O MOVA baixa conteúdo público para uso pessoal. Não contorna DRM, não acessa
conteúdo privado de terceiros e não burla paywalls ou autenticação. Os termos de
uso de várias plataformas não permitem downloads por ferramentas não oficiais;
os riscos aceitos estão registrados no planejamento.

### Versões principais

AGP 9.4 · Gradle 9.8 · Kotlin 2.4 · Compose BOM 2026.09 · compileSdk 37 · targetSdk 36 · minSdk 29 ·
youtubedl-android 0.18.1 (yt-dlp + Python + FFmpeg + QuickJS).

## Próximos passos sugeridos

- Testar cada plataforma com links reais no aparelho e registrar o resultado.
- Login opcional com a conta do próprio usuário (Instagram).
- Reavaliar o plano comercial à luz dos riscos registrados no planejamento.
- Decidir licença do código (o yt-dlp é Unlicense; FFmpeg é LGPL/GPL — considerar ao distribuir).
