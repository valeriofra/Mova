# MOVA --- Documento de visão e planejamento do projeto

**Versão:** 0.3\
**Data:** 11 de novembro de 2026\
**Status:** Em desenvolvimento --- app Android 0.3 (download das
plataformas, tema claro, 4 idiomas); distribuição fora da Play Store
(ver Atualizações 2 e 3)

------------------------------------------------------------------------

## 1. Visão geral

O **MOVA** é um aplicativo de gerenciamento e download de mídia, pensado
inicialmente para **uso pessoal**. A primeira plataforma será Android,
com desenvolvimento nativo. Depois de validado no Android, o projeto
poderá ganhar uma versão para Windows.

A proposta é oferecer uma experiência simples: o usuário cola ou
compartilha um link, o aplicativo identifica automaticamente a origem e
apresenta apenas as ações que forem realmente possíveis para aquele
conteúdo.

O projeto poderá futuramente ser disponibilizado a outras pessoas
mediante uma **licença vitalícia, com pagamento único**, sem assinatura
mensal. A comercialização é secundária: a prioridade é construir uma
ferramenta que atenda bem ao uso pessoal do desenvolvedor.

## 2. Decisões já definidas

  ---------------------------------------------------------------------
  Tema                               Decisão
  ---------------------------------- ----------------------------------
  Nome provisório                    **MOVA**

  Finalidade inicial                 Uso pessoal

  Primeira plataforma                Android nativo

  Plataforma futura                  Windows

  Aparência                          Dark

  Direção visual                     Limpa, intuitiva, moderna e
                                     minimalista

  Identidade                         Nome e ícone abstratos, simples e
                                     fáceis de reconhecer

  Cor de destaque sugerida           Azul elétrico, com detalhes em
                                     ciano

  Nome anterior                      MediaDown --- substituído por MOVA
                                     como nome provisório

  Cadastro                           Não previsto na primeira versão

  Anúncios                           Não previstos

  Servidor de mídia                  Não previsto; processamento
                                     prioritariamente local

  Distribuição inicial               Projeto/site no GitHub, usando
                                     endereço gratuito

  Site                               GitHub Pages, inicialmente

  Monetização futura                 Licença vitalícia, pagamento
                                     único, opcional para terceiros

  Assinatura mensal                  Não

  Qualidade-alvo de vídeo            Até 1080p, quando disponível e
                                     permitido

  Contas das redes sociais           Não exigir login; o login da
                                     própria conta do usuário poderá
                                     ser oferecido como opção quando a
                                     plataforma exigir (ex.: Instagram)

  Download das plataformas           **Sim**, para uso pessoal, por
                                     extração local no aparelho
                                     (yt-dlp), dentro dos limites da
                                     seção 4 --- decisão da
                                     Atualização 2
  ---------------------------------------------------------------------

As definições comerciais e de distribuição ainda são uma intenção de
planejamento. Não foi decidido que o código-fonte será open source, nem
que ficará público. Essa escolha deverá ser feita antes de publicar o
repositório.

## 3. Experiência principal do usuário

O fluxo desejado é:

1.  O usuário cola um link no aplicativo ou compartilha o link a partir
    de outro aplicativo Android.
2.  O MOVA reconhece automaticamente a plataforma, sem pedir que o
    usuário escolha a rede social.
3.  O aplicativo analisa o conteúdo e identifica as informações e ações
    disponíveis.
4.  O usuário toca em **Download**.
5.  Só então o aplicativo apresenta as opções compatíveis com aquele
    conteúdo.
6.  O usuário escolhe o que deseja obter e acompanha a tarefa na fila de
    downloads.
7.  O arquivo concluído aparece na biblioteca local.

Se o link não for reconhecido ou não houver uma forma autorizada de
obter o conteúdo, o MOVA deverá explicar isso de maneira clara e
oferecer, quando possível, a opção de abrir a página original.

### 3.1 Opções contextuais

A tela de opções deverá mostrar somente o que for aplicável:

-   Vídeo, com qualidades disponíveis até 1080p.
-   Áudio separado, por exemplo MP3 ou M4A, quando houver uma fonte e
    autorização compatíveis.
-   Seleção de itens individuais.
-   Download de vários itens ou de uma coleção completa, quando a
    plataforma e as permissões permitirem.

Não se deve presumir que todo link represente uma playlist ou que toda
plataforma permita download em lote.

### 3.2 Conteúdos com vários itens

O aplicativo deverá distinguir, quando conseguir identificar:

-   Um vídeo individual.
-   Uma publicação com vários vídeos.
-   Uma playlist ou coleção.
-   Uma sequência de Stories ou conteúdo temporário.

Quando houver vários itens acessíveis, a experiência ideal é permitir
selecionar um, alguns ou todos. Se a origem não disponibilizar a lista
de itens, o MOVA não deverá inventar essa possibilidade nem prometer que
conseguirá baixar a sequência.

**Instagram Stories:** a identificação e obtenção de Stories não é
garantida por um simples link. A disponibilidade depende do acesso
permitido, da natureza pública ou privada do conteúdo, da validade do
link e dos meios oficiais disponíveis. Conteúdo privado, expirado ou sem
autorização não será contornado.

## 4. Plataformas pretendidas

As plataformas que se deseja reconhecer na arquitetura são:

1.  YouTube
2.  Facebook
3.  Instagram
4.  TikTok
5.  Kwai
6.  Pinterest
7.  Vimeo

A presença de uma plataforma na lista significa que o MOVA deverá
reconhecer e classificar seus links **e tentar baixar o conteúdo
público** pelo mecanismo de extração local (ver Atualização 2). Ainda
assim, não há garantia de que todo link funcione: as plataformas mudam
com frequência e algumas exigem login para parte do conteúdo.

O MOVA não deverá: - Contornar DRM ou mecanismos de proteção (ex.:
filmes e séries de serviços pagos). - Acessar conteúdo privado sem
autorização. - Burlar paywalls, autenticação ou controles de acesso
(usar apenas a conta do próprio usuário, quando ele mesmo fizer
login). - Prometer suporte universal a playlists, Stories ou downloads
em lote.

> **Alterado na Atualização 2:** a regra anterior de "não extrair áudio
> ou vídeo quando isso contrariar os termos do serviço" foi substituída
> pela decisão de baixar conteúdo público para uso pessoal. Os termos de
> uso de várias plataformas não permitem esse tipo de download; o
> desenvolvedor assume esse risco para uso pessoal (ver riscos na
> Atualização 2).

Quando o download não for possível (conteúdo privado, removido,
protegido por DRM ou não suportado), o aplicativo deverá explicar o
motivo e oferecer abrir o conteúdo no serviço original.

## 5. Funcionalidades planejadas para o Android

### 5.1 Tela inicial

-   Campo central para colar link.
-   Reconhecimento automático da plataforma.
-   Ação principal para analisar o link.
-   Downloads recentes.
-   Recebimento de links pelo menu Compartilhar do Android.

### 5.2 Preparação do download

-   Título e miniatura, quando disponíveis.
-   Identificação do tipo de conteúdo.
-   Opções de formato e qualidade realmente disponíveis.
-   Seleção de um ou vários itens, quando suportada.
-   Explicação clara quando não houver download autorizado.

### 5.3 Fila de downloads

-   Tarefas em andamento, pendentes e concluídas.
-   Progresso por item.
-   Pausar, retomar quando tecnicamente suportado e cancelar.
-   Controle de downloads simultâneos.
-   Tratamento de falhas e tentativas.

### 5.4 Biblioteca

-   Organização de vídeos e áudios.
-   Histórico de downloads.
-   Informações básicas dos arquivos.
-   Abertura e compartilhamento dos arquivos.
-   Organização local, sem necessidade de conta.

### 5.5 Conversão de áudio

-   Conversão local de arquivos que o usuário tenha direito de
    processar.
-   Formatos possíveis a avaliar: MP3, M4A e WAV.
-   A conversão não deve ser confundida com autorização para extrair
    áudio de qualquer plataforma.

### 5.6 Ajustes

-   Qualidade preferida.
-   Pasta ou local de armazenamento.
-   Preferência por Wi-Fi.
-   Downloads simultâneos.
-   Limpeza de arquivos temporários.
-   Preferências de conversão.

## 6. Navegação e direção visual

A proposta inicial é uma navegação inferior com quatro áreas:

1.  **Baixar:** campo de link e downloads recentes.
2.  **Fila:** tarefas pendentes e em andamento.
3.  **Biblioteca:** arquivos salvos.
4.  **Ajustes:** preferências do aplicativo.

A análise do link e a seleção das opções poderão abrir telas próprias,
sem sobrecarregar a tela inicial.

### Princípios de interface

-   Uma ação principal por tela.
-   Poucos elementos e linguagem simples.
-   Não exigir que o usuário selecione a plataforma.
-   Mostrar opções somente depois da análise e da ação de download.
-   Evitar configurações avançadas antes de serem necessárias.
-   Informar limitações e erros com clareza.
-   Não incluir anúncios nem telas de cadastro na primeira versão.

### Identidade visual

-   Tema escuro.
-   Fundo grafite ou quase preto.
-   Superfícies em cinza escuro.
-   Azul elétrico como destaque e ciano como cor secundária.
-   Tipografia legível e ícones consistentes.
-   Símbolo abstrato e geométrico, evitando depender de uma seta de
    download genérica.

O nome **MOVA** é provisório. Antes de adotá-lo comercialmente, será
necessário verificar disponibilidade de marca, domínio e nomes nas lojas
de aplicativos.

## 7. Arquitetura técnica --- direção inicial

A primeira versão será Android nativa. A recomendação técnica inicial é:

  ---------------------------------------------------------------------
  Área                               Tecnologia ou abordagem proposta
  ---------------------------------- ----------------------------------
  Linguagem                          Kotlin

  Interface                          Jetpack Compose

  Arquitetura de apresentação        MVVM

  Organização                        Separação entre apresentação,
                                     domínio e dados

  Concorrência                       Kotlin Coroutines

  Tarefas persistentes               WorkManager, quando adequado ao
                                     tipo de tarefa

  Rede                               Cliente HTTP, por exemplo OkHttp,
                                     para fontes autorizadas

  Banco local                        Room, se o histórico e as tarefas
                                     exigirem persistência estruturada

  Preferências                       DataStore

  Armazenamento                      MediaStore e Storage Access
                                     Framework, conforme o caso

  Conversão                          FFmpeg ou alternativa compatível,
                                     após avaliar licenças e manutenção

  Injeção de dependência             A decidir quando a complexidade
                                     justificar
  ---------------------------------------------------------------------

As versões exatas de Android Gradle Plugin, Gradle, Kotlin, Compose e
SDK deverão ser escolhidas em conjunto e verificadas na documentação
oficial no momento de iniciar a implementação. Este documento não fixa
números de versão.

### 7.1 Módulos conceituais

Evitar criar muitos módulos vazios logo no início. A separação pode
começar simples e crescer conforme a implementação exigir:

-   **app-android:** ponto de entrada, integração com o Android e
    navegação.
-   **presentation:** telas, componentes e estados da interface.
-   **domain:** regras de negócio, modelos e casos de uso.
-   **data:** repositórios e persistência local.
-   **download:** fila, tarefas, progresso e transferência de arquivos.
-   **providers:** reconhecimento de plataformas e capacidades de cada
    origem.
-   **media:** inspeção de arquivos e conversão local.
-   **design-system:** cores, tipografia, ícones e componentes visuais.

Cada provider deverá informar o que consegue fazer de forma legítima e
tecnicamente comprovada. O restante do aplicativo não deverá depender de
detalhes específicos de uma rede social.

### 7.2 Preparação para Windows

A versão Windows virá depois que o Android estiver funcional e validado.
A lógica de negócio deverá ser mantida tão independente quanto razoável
das APIs Android.

Kotlin Multiplatform poderá ser avaliado para compartilhar modelos e
regras de negócio. A interface, armazenamento e integração com o sistema
operacional serão específicos de cada plataforma. A decisão final sobre
tecnologia desktop ficará para uma etapa posterior.

## 8. Escopo recomendado do MVP

O MVP deve priorizar uma base confiável, em vez de tentar entregar todos
os adaptadores de redes sociais ao mesmo tempo.

### Entregas prioritárias

-   Aplicativo Android nativo com tema dark.
-   Navegação principal e telas essenciais.
-   Colar e compartilhar links.
-   Reconhecimento das sete plataformas, quando possível.
-   Mensagens claras para links não reconhecidos ou sem ações
    disponíveis.
-   Motor de download para arquivos e URLs diretas cuja obtenção seja
    autorizada.
-   Fila, progresso, cancelamento e armazenamento local.
-   Biblioteca e histórico.
-   Testes com arquivos de exemplo e fontes autorizadas.

### Entregas graduais

-   Integração específica com cada plataforma, após análise de
    permissões e termos.
-   Download de playlists ou coleções.
-   Seleção de vários itens.
-   Conversão de áudio.
-   Melhorias de retomada e downloads em segundo plano.

A implementação de um provider não deve ser considerada concluída apenas
porque reconhece uma URL: é necessário comprovar quais metadados e ações
são realmente acessíveis.

## 9. Etapas de desenvolvimento

### Etapa 0 --- Planejamento e decisões

-   Consolidar este documento.
-   Definir identidade visual e nome final.
-   Decidir política de código-fonte e licença de distribuição.
-   Confirmar requisitos técnicos e restrições de cada plataforma.

### Etapa 1 --- Fundação Android

-   Criar o projeto nativo.
-   Configurar Gradle e dependências.
-   Definir tema, cores, tipografia e componentes.
-   Construir navegação e telas principais.
-   Implementar estados vazios, carregamento e mensagens de erro.
-   Não iniciar integração de download de redes sociais antes de a base
    estar definida.

### Etapa 2 --- Núcleo de downloads

-   Implementar transferência de arquivos por fontes autorizadas.
-   Criar fila e persistência de tarefas.
-   Gerenciar progresso, cancelamento e erros.
-   Salvar arquivos usando as APIs adequadas do Android.

### Etapa 3 --- Reconhecimento e providers

-   Criar reconhecimento de URLs.
-   Adicionar os providers individualmente.
-   Documentar capacidades, limitações e permissões de cada plataforma.
-   Testar com conteúdo próprio ou expressamente autorizado.

### Etapa 4 --- Biblioteca e mídia

-   Histórico e organização local.
-   Inspeção de arquivos.
-   Conversão de arquivos locais, se necessária.
-   Seleção de vários itens quando suportada.

### Etapa 5 --- Validação e distribuição pessoal

-   Testar em diferentes versões e aparelhos Android.
-   Corrigir falhas.
-   Gerar APK de teste e documentar instalação.
-   Validar consumo de bateria, armazenamento e execução em segundo
    plano.

### Etapa 6 --- Site e distribuição a terceiros

-   Criar o site estático no GitHub Pages.
-   Publicar documentação e versões oficiais.
-   Configurar domínio próprio somente se desejado.
-   Integrar pagamento e emissão de licenças apenas quando houver
    decisão de comercializar.

### Etapa 7 --- Windows

-   Reavaliar a arquitetura compartilhável.
-   Implementar interface e integrações próprias do Windows.
-   Testar a licença multiplataforma, caso esse modelo seja adotado.

## 10. Site oficial e GitHub

A intenção é começar com um endereço gratuito do GitHub Pages, por
exemplo:

`https://<usuario>.github.io/<repositorio>/`

O endereço definitivo dependerá do nome de usuário e do repositório
escolhidos.

O site poderá conter: - Apresentação do MOVA. - Funcionalidades e
limitações. - Plataformas e sistemas compatíveis. - Capturas de tela. -
Perguntas frequentes. - Links para versões oficiais. - Informações de
licença e suporte.

O GitHub Pages hospeda conteúdo estático. Não processa pagamentos nem
emite chaves por conta própria. Se a venda for implementada, será
necessário integrar um provedor de pagamento e um serviço seguro de
emissão ou validação de licenças.

SEO poderá incluir títulos e descrições adequados, conteúdo útil,
sitemap e metadados. A indexação e a posição no Google não são
garantidas.

## 11. Modelo de distribuição e licença --- proposta futura

A intenção comercial atual é:

-   O desenvolvedor utiliza o MOVA gratuitamente para seu próprio uso.
-   Outras pessoas poderão adquirir uma licença vitalícia por pagamento
    único.
-   Não haverá assinatura mensal.
-   Uma licença poderá, se essa decisão for mantida, cobrir Android e
    Windows.
-   O número de dispositivos e as regras de transferência da licença
    ainda precisam ser definidos.
-   A comercialização não é requisito para o MVP pessoal.

### Distinção importante: código público e licença

-   **Repositório público:** qualquer pessoa pode visualizar o código.
-   **Código open source:** o código é distribuído sob uma licença que
    concede direitos de uso, modificação e redistribuição conforme seus
    termos.
-   **Código proprietário:** os direitos de uso, cópia e redistribuição
    são limitados pela licença do titular.
-   **Aplicativo compilado:** APK ou instalador que o usuário instala.

Se a intenção for cobrar pelo direito de usar o aplicativo, mantendo
controle sobre cópia e redistribuição, uma licença proprietária é mais
coerente. O código pode permanecer privado ou ser publicado sob termos
proprietários específicos, mas disponibilizar o código publicamente não
impede que terceiros o copiem; os termos e a proteção jurídica precisam
ser considerados.

A decisão entre repositório privado, código público proprietário ou open
source permanece **em aberto**. Não publicar o código antes de escolher
e incluir a licença adequada.

## 12. Licenciamento técnico --- pontos a avaliar

Se a comercialização avançar: - Emitir chaves únicas após confirmação do
pagamento. - Assinar licenças digitalmente para permitir validação
offline. - Evitar incluir segredos privados no aplicativo. - Definir
procedimento de recuperação e transferência de licença. - Definir limite
de dispositivos. - Considerar validação online periódica apenas se
realmente necessária. - Prever uso gratuito do desenvolvedor por uma
autorização separada.

Uma chave assinada pode permitir validação local sem manter um servidor
de mídia. Ainda assim, emissão, pagamentos, suporte e eventual revogação
podem exigir um pequeno serviço externo.

## 13. Critérios de qualidade

A primeira versão deverá ser testada para confirmar:

-   Reconhecimento correto de URLs conhecidas.
-   Mensagem adequada para URL desconhecida.
-   Recebimento de links pelo compartilhamento Android.
-   Download bem-sucedido de arquivos autorizados.
-   Progresso e cancelamento confiáveis.
-   Retomada quando a origem e o mecanismo permitirem.
-   Tratamento de falta de espaço.
-   Histórico preservado após fechar e reabrir o app.
-   Arquivos salvos em local acessível ao usuário.
-   Nenhuma promessa de download para conteúdo sem permissão ou suporte.

## 14. Decisões ainda pendentes

1.  Nome MOVA definitivo e verificação de marca.
2.  Desenho final do ícone e identidade visual.
3.  Repositório público ou privado.
4.  Licença proprietária ou open source.
5.  Política de distribuição dos APKs e instaladores.
6.  Limite de dispositivos por licença, se houver venda.
7.  Provedor de pagamento e emissão de licença.
8.  ~~Quais plataformas terão integrações efetivas e por quais meios
    autorizados.~~ Decidido na Atualização 2: todas, via yt-dlp, para
    conteúdo público.
9.  Tecnologia final para a versão Windows.
10. Formatos de áudio e detalhes de conversão.

## 15. Princípios que orientam o projeto

1.  **Uso pessoal primeiro:** construir algo útil para o próprio
    desenvolvedor antes de investir em comercialização.
2.  **Simplicidade:** a experiência deve ser limpa e intuitiva.
3.  **Transparência:** explicar o que é possível e o que não é.
4.  **Limites claros:** baixar conteúdo público para uso pessoal, sem
    contornar DRM, privacidade, paywalls ou restrições de acesso.
5.  **Processamento local:** evitar servidores e coleta de dados
    desnecessários.
6.  **Arquitetura evolutiva:** preparar a base para Windows sem
    complicar o MVP.
7.  **Custo controlado:** adiar domínio, pagamentos e infraestrutura
    comercial até que sejam necessários.

------------------------------------------------------------------------

**Resumo:** o MOVA será desenvolvido primeiro como uma ferramenta
Android nativa para uso pessoal, com interface dark minimalista,
reconhecimento automático de links, opções contextuais e gerenciamento
local de downloads autorizados. O Windows, o site público e a licença
vitalícia para terceiros são evoluções planejadas, não pré-requisitos
para a primeira versão.

------------------------------------------------------------------------

# Atualização 1 --- Identidade visual e mockups

**Data:** 10 de novembro de 2026\
**Status:** Direção visual definida; detalhes sujeitos a refinamento.

## 16. Logo e ícone do MOVA

A direção escolhida é **abstrata e minimalista**. O mesmo símbolo será
utilizado como ícone do Android, ícone do Windows, marca no site e
elemento de identificação nas telas.

O conceito visual combina movimento, fluxo e conteúdo digital por meio
de uma forma geométrica integrada. Deve ser reconhecível sem o nome
MOVA, funcionar em tamanhos pequenos e também ter versões
monocromáticas.

### Direção de cores

-   Fundo principal: grafite muito escuro, próximo de `#0B0F1A`.
-   Azul principal: próximo de `#2563EB`.
-   Ciano de apoio: próximo de `#06B6D4`.
-   Texto principal: branco suave.
-   Texto secundário: cinza frio.

Esses códigos são referências iniciais, não uma paleta técnica final. O
Design System deverá centralizá-los e garantir contraste e legibilidade.

### Regras para o logo

-   Evitar detalhes finos demais.
-   Não depender de sombras ou brilhos para reconhecer o símbolo.
-   Manter boa leitura em ícone adaptativo Android.
-   Criar versões colorida, monocromática clara e monocromática escura.
-   Não inserir o nome dentro do ícone.
-   Evitar uma seta tradicional de download como símbolo principal.

O nome MOVA permanece provisório até a verificação de disponibilidade de
marca e domínio.

## 17. Mockups e estrutura das telas

A proposta visual inicial contempla cinco telas principais no fluxo de
download e uma versão desktop conceitual.

### 17.1 Tela inicial --- Baixar

**Objetivo:** permitir começar rapidamente, sem escolher a plataforma.

Elementos: - Logo e nome MOVA no cabeçalho. - Acesso discreto às
configurações. - Campo central para colar um link. - Ação principal
**Download** ou **Analisar link**, conforme o estado. - Atalhos para
Downloads, Histórico, Configurações e Licença, se o módulo de licença
vier a ser implementado. - Lista curta de downloads recentes, quando
existirem.

A plataforma é reconhecida automaticamente. Não mostrar uma grade de
redes sociais para o usuário selecionar. O compartilhamento Android
deverá preencher o campo, sem iniciar download automaticamente.

### 17.2 Tela de análise

**Objetivo:** informar que o aplicativo está verificando o link.

Elementos: - Indicador visual de análise. - Mensagem curta, como
"Analisando o link...". - Texto auxiliar informando que a origem e os
conteúdos disponíveis estão sendo identificados.

Estados: analisando, origem reconhecida, origem não reconhecida,
conteúdo indisponível, acesso não permitido e erro temporário.

Reconhecer uma plataforma não significa que o download será possível.

### 17.3 Tela de opções de download

**Objetivo:** apresentar ações disponíveis somente depois da análise e
da intenção de baixar.

Elementos possíveis: - Miniatura e título, quando disponíveis. - Tipo de
conteúdo e duração, quando disponíveis. - Vídeo com seletor de qualidade
até 1080p, se disponível. - Somente áudio, com formatos compatíveis. -
Playlist/Coleção, quando suportada. - Selecionar itens, quando houver
vários conteúdos identificados. - Botão principal para confirmar o
download.

As opções devem ser contextuais. Não mostrar opções indisponíveis ou
incompatíveis com a origem.

### 17.4 Tela de seleção de itens

**Objetivo:** permitir escolher exatamente o que será baixado de uma
publicação ou coleção com vários itens.

Elementos: - Lista de itens identificados. - Miniatura por item, quando
disponível. - Nome, duração e tamanho estimado, quando disponíveis. -
Seleção individual. - Ação "Selecionar todos". - Botão "Baixar
selecionados", com a quantidade selecionada.

O aplicativo deve distinguir vídeo individual, publicação com vários
vídeos, playlist/coleção e sequência de conteúdo temporário. Não deve
presumir que uma sequência de Stories possa ser listada ou baixada por
meio de um link comum.

### 17.5 Tela de downloads

**Objetivo:** acompanhar tarefas e acessar arquivos concluídos.

Elementos: - Seção Em andamento. - Progresso individual. - Nome e
miniatura do arquivo, quando disponíveis. - Ações de pausar, retomar
quando suportado e cancelar. - Seção Concluídos. - Acesso ao arquivo
salvo.

Estados: aguardando, em andamento, pausado, concluído ou com erro.

### 17.6 Biblioteca

A biblioteca organizará vídeos, áudios e outros arquivos compatíveis,
com histórico e filtros quando úteis.

Evitar duplicar funções: - Downloads: tarefas e progresso. - Biblioteca:
arquivos já salvos. - Histórico: registro das atividades anteriores.

### 17.7 Configurações

Configurações essenciais: - Qualidade preferida. - Preferência de rede
Wi-Fi. - Local de armazenamento. - Downloads simultâneos. - Preferências
de áudio. - Limpeza de temporários. - Informações e versão do
aplicativo.

Configurações avançadas devem ficar agrupadas e não ocupar a tela
inicial.

## 18. Navegação recomendada

A navegação principal terá quatro destinos: 1. **Baixar** --- tela
inicial e entrada de links. 2. **Fila** --- downloads em andamento e
pendentes. 3. **Biblioteca** --- arquivos concluídos. 4. **Ajustes** ---
configurações e informações.

A análise, as opções e a seleção de itens são etapas do fluxo e não
precisam ocupar espaço permanente na barra inferior.

## 19. Regras de experiência visual

-   Uma ação principal por tela.
-   Pouco texto e linguagem cotidiana.
-   Não solicitar seleção manual da plataforma.
-   Não apresentar opções antes de saber o que está disponível.
-   Usar ícones acompanhados de rótulos quando a ação não for óbvia.
-   Manter estados vazios úteis e explicativos.
-   Dar retorno claro para carregamento, sucesso e erro.
-   Respeitar tamanhos de fonte e contraste do Android.
-   Não depender apenas de cor para comunicar estados.
-   Manter consistência entre Android, Windows e site, adaptando cada
    interface ao seu contexto.

## 20. Referência visual

Foi criada uma prancha conceitual com símbolo e nome MOVA, direção de
cores, tela inicial, análise, opções de download, seleção de itens,
progresso e um esboço Windows.

A prancha é uma referência de direção visual, não uma especificação
pixel a pixel nem uma interface implementada. Textos pequenos e detalhes
gerados em imagens conceituais devem ser revisados durante o desenho
final.

## 21. Próximo passo de design

Antes da implementação, refinar cada tela individualmente e validar
hierarquia visual, dimensões, espaçamentos, estados vazios e de erro,
comportamento em diferentes tamanhos de Android, acessibilidade e
contraste.

A identidade e os mockups são aprovados como **direção visual inicial**,
mantendo liberdade para ajustes de usabilidade.

------------------------------------------------------------------------

# Atualização 2 --- Download nas plataformas

**Data:** 11 de novembro de 2026\
**Status:** Decidido; em implementação.

## 22. Decisão

O MOVA **deve baixar os vídeos** das plataformas da seção 4 (YouTube,
Facebook, Instagram, TikTok, Kwai, Pinterest e Vimeo), e não apenas
reconhecer os links. A finalidade continua sendo o **uso pessoal**.

Motivo: no teste da versão 0.1, um link de Reel do Instagram foi
reconhecido, mas não baixado, o que não atende à necessidade principal do
aplicativo.

## 23. Como funciona

-   Mecanismo: **yt-dlp** (projeto de código aberto), executado **dentro
    do próprio aparelho**, junto com Python, FFmpeg (unir vídeo e áudio
    em até 1080p e extrair áudio) e QuickJS (exigido pelo YouTube). Sem
    servidor externo, mantendo o princípio de processamento local.
-   O aplicativo atualiza o yt-dlp pela internet (no primeiro uso e uma
    vez por dia, pelo canal "nightly", que recebe primeiro as correções),
    porque as plataformas mudam com frequência e versões antigas deixam
    de funcionar.
-   Instagram: a versão atual do yt-dlp só acessa posts sem login quando
    consegue se passar por um navegador de computador, o que não é
    possível no Android. Por isso o MOVA abre a página pública do post
    num navegador invisível do próprio aparelho, sem conta, senha ou
    servidor, e lê o vídeo que a página recebe. Sem rodízio de IPs nem
    contas de terceiros: se o Instagram limitar o acesso sem conta, o app
    informa.
-   Fluxo: colar ou compartilhar o link → análise (título, miniatura,
    duração, qualidades disponíveis) → **Download** → escolher **vídeo
    (até 1080p)** ou **somente áudio (MP3/M4A)** → fila → biblioteca.
-   Playlists, coleções e publicações com vários itens: listar os itens
    e permitir baixar todos ou selecionar.
-   Páginas de outros sites também passam pelo mecanismo: se houver um
    vídeo incorporado reconhecível, ele pode ser baixado.

## 24. Limites que continuam valendo

-   Não contornar DRM (serviços pagos de filmes, séries e música
    protegidos).
-   Não acessar conteúdo privado de terceiros nem burlar paywalls.
-   Conteúdo que exige login (comum no Instagram) só poderá ser baixado
    com a **conta do próprio usuário**, se essa opção for implementada.
    O MOVA nunca usará contas ou credenciais de terceiros.
-   Explicar claramente quando um link não puder ser baixado (privado,
    removido, exige login, DRM, não suportado).

## 25. Riscos aceitos e consequências

-   **Termos de uso:** YouTube, Instagram, TikTok e outras plataformas
    proíbem, em seus termos, o download por ferramentas não oficiais.
    Para uso pessoal, o risco prático é baixo, mas existe a
    possibilidade de bloqueio temporário por excesso de acessos.
-   **Direitos autorais:** baixar não dá direito de redistribuir. O uso
    deve se limitar a cópias pessoais de conteúdo que o usuário possa
    guardar.
-   **Funcionamento instável:** as plataformas mudam com frequência. Um
    link que funciona hoje pode falhar amanhã até o yt-dlp ser
    atualizado.
-   **Tamanho do aplicativo:** o mecanismo (Python + FFmpeg) aumenta o
    APK para algumas dezenas de MB. A primeira análise demora alguns
    segundos a mais enquanto o mecanismo é preparado.
-   **Impacto na comercialização (seção 11):** a Google Play não aceita
    aplicativos que baixam vídeos do YouTube, e vender licenças de uma
    ferramenta que contraria os termos dessas plataformas traz risco
    jurídico. **Antes de qualquer venda a terceiros, a estratégia
    comercial deve ser reavaliada** (por exemplo: manter o MOVA apenas
    pessoal, ou comercializar somente recursos que não dependam da
    extração das plataformas).
-   O repositório deve permanecer **privado**.

## 26. Suporte esperado por plataforma

  ---------------------------------------------------------------------
  Plataforma        Expectativa
  ----------------- ---------------------------------------------------
  YouTube           Vídeos, Shorts, playlists e áudio, até 1080p

  Vimeo             Vídeos públicos

  TikTok            Vídeos públicos

  Facebook          Vídeos e Reels públicos

  Pinterest         Pins com vídeo

  Instagram         Reels e publicações públicas **sem login**: o app
                    abre a página pública do post num navegador
                    invisível (como um visitante sem conta) e usa o
                    vídeo que a página recebe; se falhar, tenta o
                    yt-dlp. Stories e posts privados exigem login

  Kwai              Sem garantia: depende de o yt-dlp reconhecer a
                    página
  ---------------------------------------------------------------------

Esta tabela é uma expectativa. O suporte real deve ser confirmado em
testes no aparelho e documentado.

### 26.1 Testes no aparelho

  -----------------------------------------------------------------------
  Data          Versão    Plataforma    Resultado
  ------------- --------- ------------- ---------------------------------
  11/11/2026    0.2.3     Instagram     ✅ Funciona (Reels públicos, sem
                                        login)

  11/11/2026    0.2.3     Pinterest     ✅ Funciona

  11/11/2026    0.2.3     Facebook      ✅ Funciona

  11/11/2026    0.2.3     YouTube       ✅ Funciona (vídeo e somente
                                        áudio)

  11/11/2026    0.2.3     TikTok        ✅ Funciona

  11/11/2026    0.2.3     DTube         ✅ Funciona (fora da lista
                                        original)

  11/11/2026    0.2.3     COS.TV        ⚠️ Só oferecia áudio (fora da
                                        lista). Corrigido na 0.2.4: a
                                        testar

  11/11/2026    0.2.3     Kwai          ❌ "Unsupported URL" (o yt-dlp
                                        não suporta). Na 0.2.4 o app
                                        procura o vídeo na própria
                                        página: a testar

  ---           ---       Vimeo         ⏳ A testar

  ---           ---       Rumble        ⏳ A testar (o yt-dlp tem
                                        suporte próprio)
  -----------------------------------------------------------------------

Sites fora da lista original também podem funcionar: o yt-dlp reconhece
milhares de sites, e para os que ele não reconhece o MOVA procura o vídeo
na própria página (a partir da 0.2.4).

## 27. Próximos passos

1.  Integrar o yt-dlp no app Android (análise, formatos, fila,
    progresso, cancelamento e retomada).
2.  Testar cada plataforma com links reais no aparelho e registrar o
    resultado.
3.  Avaliar o login opcional com a conta do próprio usuário para o
    Instagram.
4.  Reavaliar o plano comercial (seção 11) à luz dos riscos acima.

------------------------------------------------------------------------

# Atualização 3 --- Aparência, idiomas e distribuição

Data: 11 de novembro de 2026 · App 0.3.0

## 28. Mudanças de interface (0.3.0)

-   **Tema claro**, além do escuro (padrão). Em Ajustes → Aparência:
    Escuro, Claro ou Sistema. As barras do Android acompanham o tema.
-   **Início limpo:** a lista de recentes saiu da tela inicial, que agora
    mostra só o logo, o campo do link e o botão Baixar. Os recentes
    continuam na Fila e na Biblioteca.
-   **Card Sobre** reorganizado (versão, aviso de uso pessoal e de que o
    app não contorna DRM, privacidade ou restrições de acesso).

## 29. Idiomas

O app passa a ter quatro idiomas: **inglês**, **português**, **espanhol**
e **francês**. No modo **Automático** (padrão), a ordem é:

1.  o idioma do aparelho, se tiver tradução;
2.  senão, o **idioma do país do usuário** (pelo chip, pela rede móvel
    ou pela região do aparelho), por exemplo: aparelho em alemão no
    Brasil abre em português; em catalão na Espanha, em espanhol; em
    árabe no Marrocos, em francês (a partir da 0.3.1);
3.  por último, o inglês.

Quem deixou o aparelho em inglês continua vendo o app em inglês. No
Android 13+ dá para escolher outro idioma só para o MOVA (Ajustes →
Idioma). O APK leva apenas esses quatro idiomas, para
que nenhuma tela misture textos de bibliotecas em outras línguas.

Os textos de referência são os em português (`values-pt`). As traduções
ficam em `tools/i18n/` e são geradas por `tools/i18n/gen.py`, que confere
se todas as chaves e marcadores (`%1$s`, `%d`...) batem com o português.

### 29.1 Onde apps assim são mais usados

Apps de download de vídeo têm mais público onde o Android domina e os
dados móveis são caros ou limitados (o usuário baixa no Wi-Fi para ver
depois): **Índia, Indonésia, Brasil, México e América Latina, Oriente
Médio e Norte da África, Vietnã, Filipinas, Turquia, Paquistão,
Bangladesh e Nigéria**. Os mais conhecidos da categoria (Snaptube,
VidMate, TubeMate) cresceram nesses mercados.

Esses mercados têm muito volume, mas pagam pouco por app; a receita da
categoria costuma vir de anúncios. Os mercados que mais pagam por app
(EUA, Japão, Coreia, Alemanha, Reino Unido) são também os de fiscalização
mais rígida sobre direitos autorais.

Próximos idiomas sugeridos, se o foco for volume: **indonésio, árabe
(exige revisar o layout da direita para a esquerda), hindi e turco**.
Se o foco for receita: alemão e japonês. Os números por país das fontes
públicas são fracos; antes de investir, conferir em uma ferramenta de
mercado (Sensor Tower, Appfigures).

## 30. Google Play e distribuição

**Conclusão: na forma atual, o MOVA muito provavelmente seria recusado
ou removido da Google Play.**

1.  **Download do YouTube.** A Play recusa apps que baixam vídeos do
    YouTube (Termos do YouTube; políticas de Abuso de dispositivo e
    rede e de Propriedade intelectual). Por isso Snaptube, VidMate,
    TubeMate e Seal (que também usa o yt-dlp) são distribuídos fora da
    Play.
2.  **Atualização do yt-dlp pelo próprio app.** A Play proíbe baixar
    código executável fora dela. Há exceção para código interpretado sem
    acesso direto às APIs do Android (o yt-dlp é Python), mas é zona
    cinzenta e não deve ser a base de uma publicação.
3.  **Termos das outras plataformas** (Instagram, Facebook, TikTok)
    também vedam ferramentas não oficiais; um pedido de remoção basta
    para derrubar o app.

Uma "versão Play" sem YouTube e sem atualização automática perderia boa
parte do valor e continuaria arriscada.

**Caminho recomendado:** manter a distribuição fora da Play:

-   **Site próprio + GitHub Releases**, com a assinatura fixa (já pronta)
    para instalar por cima. Próximo passo: o app avisar quando houver
    versão nova.
-   **Lojas alternativas** (Uptodown, APKPure, Aptoide). O **F-Droid**
    exige código aberto.

### 30.1 Verificação de desenvolvedor do Android

Desde 30/09/2026, no Brasil, Indonésia, Singapura e Tailândia, o Android
certificado só instala, **pelas lojas participantes** (Play, Galaxy
Store, lojas da Xiaomi, OPPO, vivo, HONOR e Transsion), apps de
desenvolvedores verificados. A instalação direta do APK ainda não é
afetada, e existe um fluxo avançado para apps não verificados. A
expansão global está prevista para 2027 e deve alcançar também o APK
instalado direto.

**Ação:** antes de distribuir a terceiros, fazer a verificação de
desenvolvedor no Android Developer Console e registrar o pacote
`io.github.valeriofra.mova` com a chave de assinatura atual (SHA-256
`663cc93b...0739e84`). A verificação não depende de publicar na Play.

## 31. Próximos passos (atualizado)

1.  Testar no aparelho: COS.TV, Kwai, Rumble e Vimeo (seção 26.1) e
    revisar as telas em tema claro e nos outros idiomas.
2.  Aviso de versão nova dentro do app (para a distribuição fora da
    Play).
3.  Verificação de desenvolvedor do Android antes de 2027.
4.  Decidir o modelo comercial (seção 11): o volume está em mercados que
    pagam pouco, e a Play não é opção.

## 32. Exclusão automática (0.3.1)

Em Ajustes → Armazenamento: interruptor **Exclusão automática** e
períodos de **3, 15 ou 30 dias** (desligada por padrão; ao ligar, começa
em 30 dias).

-   Apaga do aparelho os arquivos baixados há mais tempo que o período e
    tira do app os registros desse período (concluídos, com erro ou
    cancelados).
-   Downloads na fila, em andamento ou pausados não são tocados.
-   Se um arquivo não puder ser apagado (ex.: a pasta deixou de estar
    autorizada), o registro fica e a exclusão tenta de novo depois.
-   Roda ao abrir o app e uma vez por dia em segundo plano (WorkManager).
-   Ao ligar ou ao encurtar o período, o app pede confirmação; a
    Biblioteca mostra um aviso enquanto a opção estiver ligada.
