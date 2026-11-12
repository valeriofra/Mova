# MOVA

Aplicativo para **reconhecer links, baixar e organizar mídia**, pensado
inicialmente para uso pessoal: **Android** nativo e **computador (Windows e
Linux)**. Interface dark e minimalista, sem anúncios, sem cadastro e sem
servidor: tudo roda no próprio aparelho.

> Status: **Android 0.3.1** (download das plataformas via yt-dlp, tema claro e 4 idiomas) e
> **computador 0.2.0** (Windows e Linux). Ver `docs/planejamento.md`, Atualizações 2 a 5.
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
| Tema | ✅ Escuro (padrão), claro ou seguir o sistema (Ajustes → Aparência) |
| Idiomas | ✅ Inglês, português, espanhol e francês. Automático: idioma do aparelho; se não houver tradução, o do país do usuário; por último, inglês. Escolha manual em Ajustes (Android 13+) |
| Exclusão automática | ✅ Desligada, 3, 15 ou 30 dias: apaga do aparelho os arquivos baixados há mais tempo e limpa o histórico |

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

## Computador (Windows e Linux)

App em **Compose Multiplatform** (`desktop/`), com as mesmas regras do Android (módulo `core/`) e a mesma identidade visual.
Telas: Início (colar ou **arrastar** o link, análise, formato e itens), Fila, Biblioteca (abrir, mostrar na pasta, excluir,
histórico) e Ajustes (tema, idioma, pasta, downloads simultâneos, mecanismo, exclusão automática). Avisa pela bandeja do
sistema quando um download termina.

| Sistema | Arquivo | Observação |
| --- | --- | --- |
| Windows 10/11 | `MOVA-Windows-…-instalador.exe` | Instalador em português (Inno Setup): só para o usuário (sem administrador), **por cima** das versões anteriores e com a opção "Abrir o MOVA" no fim. Sem certificado de assinatura ainda: o SmartScreen avisa — **Mais informações → Executar assim mesmo**. |
| Windows | `MOVA-Windows-…-portatil.zip` | Descompacte e abra `MOVA.exe`. |
| Linux (qualquer distro) | `MOVA-Linux-….flatpak` | `flatpak install --user MOVA-Linux-….flatpak` (o runtime vem do Flathub; no Ubuntu, ative antes: `flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo`). Depois abra pelo menu ou com `flatpak run io.github.valeriofra.Mova`. |
| Linux | `MOVA-Linux-…-portatil.tar.gz` | Descompacte e rode `MOVA/bin/MOVA`. |

Os arquivos são gerados pelo GitHub Actions (`.github/workflows/desktop.yml`) num Windows e num Linux reais e ficam nos
artefatos da execução; na branch principal viram uma versão em **Releases** (`desktop-build-N`).

**Mecanismo:** no primeiro download, o MOVA baixa das fontes oficiais no GitHub o yt-dlp (nightly), o FFmpeg (compilação do
projeto yt-dlp) e o QuickJS-ng (resolve os desafios do YouTube) — cerca de 115 MB no Windows. O yt-dlp e o FFmpeg são
conferidos pelo SHA-256 publicado pelos projetos. No Linux, se o FFmpeg já estiver instalado, ele é usado. O yt-dlp se
atualiza sozinho 1x por dia.

**Onde salvar:** em Ajustes, "Perguntar onde salvar a cada download" (padrão: abre a janela de pastas do sistema ao
clicar em Baixar) ou "Salvar sempre nesta pasta". O botão **Abrir pasta de destino** fica na Fila, na Biblioteca e em
Ajustes.

**Janela:** no Windows a barra de título é integrada ao app (JetBrains Runtime; botões no tema escuro/claro, com
encaixe de janelas). O tamanho se adapta ao monitor (em telas pequenas abre maximizada; em janelas estreitas a barra
lateral vira só ícones) e é lembrado ao fechar. Tela de abertura imediata e cache de inicialização (AppCDS).

**Pastas:** dados em `%LOCALAPPDATA%\MOVA` (Windows), `~/.local/share/mova` (Linux portátil) ou
`~/.var/app/io.github.valeriofra.Mova` (Flatpak); arquivos em `Downloads/MOVA` (pode ser trocada em Ajustes — no Flatpak,
dentro de Downloads, Vídeos ou Músicas, que são as pastas liberadas para o app).

**Flatpak:** manifesto em `desktop/packaging/flatpak/` (runtime freedesktop 25.08, com o app-image do jpackage e o Java
já incluso). Permissões: rede, X11, GPU e as pastas Downloads, Vídeos e Músicas.

```bash
./gradlew -Pmova.desktopOnly=true :desktop:run                 # abre o app (não precisa do Android SDK)
./gradlew -Pmova.desktopOnly=true :core:test :desktop:test     # testes e capturas (desktop/build/screenshots)
./gradlew -Pmova.desktopOnly=true :desktop:packageReleaseMsi   # instalador (no Windows)
./gradlew -Pmova.desktopOnly=true :desktop:createReleaseDistributable   # app-image (base do Flatpak e do tar.gz)
```

## Desenvolvimento (Android)

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
core/   Kotlin puro (sem Android) — compartilhado entre Android e computador
  link/       extração de links do texto, reconhecimento de plataforma e tipo de conteúdo
  provider/   capacidades de cada origem (o que é permitido e comprovado)
  analysis/   análise do link, opções contextuais e formatos (vídeo até 1080p / áudio)
  extractor/  contrato do mecanismo de extração + regras do yt-dlp (JSON, argumentos, progresso, erros)
  file/       nomes de arquivo seguros, tipos MIME
app/    Android (Kotlin + Jetpack Compose, MVVM)
  data/       Room (tarefas, biblioteca, histórico) e DataStore (preferências)
  download/   fila (WorkManager), HTTP com retomada, yt-dlp (YtDlpEngine), publicação no MediaStore/SAF, notificações
  ui/         design system, navegação e telas
desktop/ Windows e Linux (Kotlin + Compose Multiplatform)
  engine/     yt-dlp, FFmpeg e QuickJS: download verificado, execução por processo, atualização
  data/       fila (downloads simultâneos), histórico e ajustes em JSON, exclusão automática
  net/        HTTP com retomada (links diretos) e consulta de arquivos
  ui/         telas (mesma paleta do Android), barra lateral, arrastar e soltar links
tools/  gen_icon.py (vetores do símbolo) · gen_desktop_icon.py (mova.ico e mova.png) · i18n/ (traduções)
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

## Traduções

Os textos de referência ficam em português: `app/src/main/res/values-pt/strings.xml` (Android) e
`desktop/src/main/composeResources/values-pt/strings.xml` (computador). Ao mudar ou criar um texto, atualize as
traduções (`tools/i18n/{en,es,fr}.py` e `tools/i18n/desktop/{en,es,fr}.py`) e rode:

```bash
python3 tools/i18n/gen.py android
python3 tools/i18n/gen.py desktop
```

O script gera `values/` (inglês), `values-es/` e `values-fr/` e falha se faltar chave ou marcador. No computador,
textos idênticos aos do Android reaproveitam a tradução do Android.

## Próximos passos sugeridos

- Testar cada plataforma com links reais no aparelho e registrar o resultado.
- Login opcional com a conta do próprio usuário (Instagram).
- Reavaliar o plano comercial à luz dos riscos registrados no planejamento.
- Distribuir fora da Google Play (site + GitHub Releases); fazer a verificação de desenvolvedor do Android antes de 2027.
- Decidir licença do código (o yt-dlp é Unlicense; FFmpeg é LGPL/GPL — considerar ao distribuir).
- Computador: atualizador automático (exige um lugar público para as versões: repositório público só de releases ou
  site); certificado de assinatura do Windows (evita o aviso do SmartScreen); Instagram e Kwai sem login como no
  Android (navegador embutido); testes com mais plataformas no Windows e no Linux.
