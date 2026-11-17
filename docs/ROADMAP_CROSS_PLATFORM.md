# Roadmap de Arquitetura: Expansão Cross-Platform (Web & Windows)

Este documento registra a análise de viabilidade técnica, estratégias e passos para levar o ecossistema **Korp TV** além do Android (Web e Windows Desktop).

---

## 1. Diagnóstico do App Atual

O app móvel/TV (`app/`) foi desenvolvido em **Kotlin nativo para Android**:
- **Interface**: Android XML Views, Jetpack Compose e componentes dependentes de `android.content.Context`.
- **Reprodução**: ExoPlayer (`androidx.media3`), dependente de codecs e decodificadores de hardware específicos do Android.
- **Armazenamento**: `SharedPreferences` (`ipvio_settings`, `ipvio_lib`), `Room`/arquivos locais gzippados.

Por depender profundamente do Android SDK (`android.*`), o código Kotlin do app **não compila diretamente para Web ou Windows** sem uma estratégia de abstração ou empacotamento.

---

## 2. Abordagens Analisadas

### Abordagem A: Web-TV + Tauri / Electron (Recomendada / Menor Esforço)

O repositório já possui uma versão funcional em **`web-tv/`** (Node.js/Express, HTML5, CSS3, JavaScript puro, HLS.js e proxy de stream com reescrita de cabeçalhos e suporte a MPEG-TS):

1. **Para Web (Navegadores, Samsung Tizen e LG webOS)**:
   - **Status**: Já existente e rodando no stack Docker (`korp-webtv`).
   - Acesso direto via navegador ou empacotado como Web App nos sistemas de Smart TV (Tizen Studio e webOS CLI).
   - Suporte a navegação por controle remoto (teclas D-pad / teclado).

2. **Para Windows Desktop (`.exe` nativo)**:
   - **Com Tauri (Melhor performance)**:
     - Cria um wrapper desktop nativo usando Rust e o motor **WebView2** (já instalado nativamente no Windows 10 e 11).
     - Executável final de **~5 MB**, inicialização instantânea e consumo de RAM de apenas ~40 MB.
     - Comunica-se com o portal central Korp TV para pareamento e validação de listas.
   - **Com Electron**:
     - Empacota o Chromium embutido. Instalador de ~80 MB, maior consumo de RAM, porém garante 100% de consistência de codecs em qualquer versão do Windows.

#### Passos para implementação quando for iniciado:
1. Adicionar configuração do Tauri (`src-tauri`) apontando para os assets do `web-tv/public`.
2. Integrar atalhos de teclado (setas, Enter, Esc, Barra de espaço) para controle total sem mouse.
3. Compilar instalador `.exe` / `.msi` via GitHub Actions ou localmente (`cargo tauri build`).

---

### Abordagem B: Migração para Kotlin Multiplatform (KMP + Compose Desktop)

Caso a decisão futura seja manter **uma única base de código em Kotlin** para Android, Desktop e Web:

1. **Requisitos de Reescrita**:
   - **Camada de UI**: Migrar todas as telas e listas para **Compose Multiplatform** (JetBrains Compose).
   - **Engine de Vídeo**:
     - Android: ExoPlayer / Media3.
     - Windows Desktop: Integração com **libVLC** via `vlcj` ou wrapper Compose Desktop VLC.
     - Web: Elemento HTML5 `<video>` com HLS.js interop via Kotlin/Wasm.
   - **Storage / Settings**: Substituir `SharedPreferences` por `multiplatform-settings` ou `SQLDelight`.
   - **Network**: Usar **Ktor Client** em vez de chamadas diretas de I/O Java.
2. **Avaliação**: Alto custo de engenharia e refatoração significativa de código já estável.

---

## 3. Matriz de Decisão

| Critério | Abordagem A (Web-TV + Tauri) | Abordagem B (Kotlin Multiplatform) |
|---|---|---|
| **Tempo estimado** | 1 a 2 dias | 3 a 6 semanas |
| **Tamanho do binário Windows** | ~5 MB | ~45-60 MB (JVM embutida) |
| **Consumo de memória** | Baixo (~40-80 MB) | Médio (~150-250 MB) |
| **Compatibilidade Smart TV (LG/Samsung)** | Pronta (HTML5/webOS/Tizen) | Inviável |
| **Reaproveitamento de código** | Utiliza o `web-tv/` existente | Compartilha regras em Kotlin |

---

## 4. Status

- **Decisão**: A ideia foi registrada e aprovada para priorização futura via **Abordagem A (Tauri / Web-TV)**.
- **Fase Atual**: Salvo no roadmap arquitetural. O foco do momento é a estabilidade do portal e do app Android Korp TV.
