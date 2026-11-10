# MOVA

Aplicativo Android nativo para **reconhecer links, baixar e organizar mídia**,
pensado inicialmente para uso pessoal. Interface dark e minimalista, sem
anúncios, sem cadastro e sem servidor: tudo roda no aparelho.

> Status: **versão 0.1 (Etapa 1 + núcleo da Etapa 2 do planejamento)**.
> Código proprietário. Todos os direitos reservados (a licença ainda será definida).

## O que já funciona

| Área | Situação |
| --- | --- |
| Colar link ou receber pelo menu **Compartilhar** do Android | ✅ Preenche o campo, sem iniciar o download sozinho |
| Reconhecimento automático da origem (YouTube, Facebook, Instagram, TikTok, Kwai, Pinterest, Vimeo) | ✅ Identifica plataforma e tipo (vídeo, publicação, playlist, Stories, perfil, link curto...) |
| Download dessas plataformas | ⏳ **Não disponível ainda**: só será liberado por meios oficiais/autorizados, validados um a um. O app explica isso e oferece abrir o conteúdo no app original |
| Download de **links diretos** de arquivos (ex.: `https://site.com/video.mp4`) | ✅ Com análise prévia (tipo, tamanho, nome) |
| Vários links de uma vez | ✅ Tela de seleção de itens (um, alguns ou todos) |
| Fila de downloads | ✅ Progresso, velocidade, pausar, retomar (quando o servidor permite), cancelar, tentar novamente |
| Downloads em segundo plano | ✅ Serviço em primeiro plano com notificação; retoma após queda de rede ou reinício |
| Preferências | ✅ Somente Wi-Fi, downloads simultâneos (1–3), pasta de destino, limpeza de temporários |
| Biblioteca e histórico | ✅ Filtros por tipo, abrir, compartilhar, excluir; histórico de concluídos/cancelados/com erro |
| Conversão de áudio | ⏳ Etapa futura |

Os arquivos são salvos em **Download/MOVA** (ou na pasta que você escolher em Ajustes).

## Instalar no celular

Cada versão é compilada pelo GitHub Actions e assinada **sempre com a mesma
chave**, então a nova versão **instala por cima** da anterior, mantendo seus
dados.

1. Abra a aba **Releases** do repositório no celular.
2. Baixe o arquivo `MOVA-x.y.z-buildN.apk` mais recente.
3. Toque no arquivo e permita "instalar apps desconhecidos" para o navegador, se pedido.

Requisitos: Android 10 ou superior.

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
  analysis/   análise do link, opções contextuais de download
  file/       nomes de arquivo seguros, tipos MIME
app/    Android (Kotlin + Jetpack Compose, MVVM)
  data/       Room (tarefas, biblioteca, histórico) e DataStore (preferências)
  download/   fila (WorkManager), transferência HTTP com retomada, publicação no MediaStore/SAF, notificações
  ui/         design system, navegação e telas
tools/  gen_icon.py — gera os vetores do símbolo MOVA (colorido, monocromático, notificação)
```

Princípio central: **cada provider declara o que consegue fazer de forma
legítima e comprovada** (`ProviderRegistry`). A interface só mostra opções que
estejam declaradas ali — reconhecer uma URL não significa poder baixá-la.

O MOVA não contorna DRM, não acessa conteúdo privado sem autorização e não burla
autenticação ou restrições de download.

### Versões principais

AGP 9.4 · Gradle 9.8 · Kotlin 2.4 · Compose BOM 2026.09 · compileSdk 37 · targetSdk 36 · minSdk 29.

## Próximos passos sugeridos

- Integrar, uma a uma, as plataformas que tiverem meio oficial/autorizado (começando pelo que for validado).
- Metadados oficiais (ex.: oEmbed) para mostrar título e miniatura na análise.
- Conversão de áudio local (avaliar FFmpeg e licenças).
- Decidir licença do código e política de distribuição (pendências do planejamento).
