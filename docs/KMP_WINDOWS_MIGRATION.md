# Migração KMP - Korp TV / IPVIO Stream (Branch `feat/kmp-migration`)

Este documento registra todas as alterações, arquitetura, correções e instruções para execução ou continuidade da versão unificada **Kotlin Multiplatform (Compose Desktop)** na branch `feat/kmp-migration`.

---

## 1. Visão Geral da Arquitetura

O projeto foi reestruturado no padrão **Kotlin Multiplatform (KMP)**:

```
kaba-ip/
├── shared/                 # Código unificado (Interface, Lógica, Parser, Modelos)
│   ├── commonMain/         # 95% do código: UI Compose, Portal, EPG, Cache, TMDB
│   ├── desktopMain/        # Player LibVLC (vlcj), bindings Swing e teclado AWT
│   └── androidMain/        # Suporte Android (Pronto para conexão com ExoPlayer)
│
├── desktopApp/             # Executável Windows Desktop
│   └── src/main/kotlin/    # Main.kt (Janela, atalhos de teclado e ciclo de vida)
│
└── app/                    # Módulo Android original (mantido intacto na base nativa)
```

---

## 2. Recursos Implementados na Versão Desktop (Windows)

### A. Player de Vídeo com Controles Completos
- **Tecnologia:** LibVLC nativo através de `vlcj` e `SwingPanel`.
- **Controles em Tela:**
  - ⏹ **Parar:** Encerra a reprodução e retorna para a tela anterior.
  - ⏮ **Voltar 10s:** Retrocede 10 segundos no vídeo.
  - ⏭ **Avançar 10s:** Avança 10 segundos no vídeo.
  - ▶ / ⏸ **Play / Pause:** Alterna reprodução com estado sincronizado.
  - **Barra de Linha do Tempo (Seek):** Tempo atual e duração total formatada (`00:15:30 / 01:45:00`).
  - **Banner de EPG (TV Ao Vivo):** Programa atual, horário, barra de progresso e próximo programa ("A Seguir").
  - **Gaveta Lateral de Canais:** Zapping rápido entre canais sem sair do player.
- **Atalhos Globais do Teclado (mesmo com foco dentro do vídeo):**
  - `ESC`: Voltar / Fechar Player / Fechar Telas.
  - `F11`: Alternar Tela Cheia (corrigido sem travamentos).
  - `Espaço`: Play / Pause.
  - `Seta Esquerda`: Voltar 10 segundos.
  - `Seta Direita`: Avançar 10 segundos.
  - `S`: Parar reprodução.

### B. Cache Local em Disco (Inicialização Instantânea)
- A playlist baixada é salva localmente em `~/.korptv/playlist_cache.m3u`.
- Ao abrir o aplicativo, a lista de canais, filmes e séries é lida **em menos de 1 segundo** sem esperar resposta da rede.
- As credenciais Xtream (`host`, `user`, `pass`) são salvas de forma persistente nas preferências.
- A sincronização não sobrescreve o cache indevidamente. O botão **"🔄 Atualizar Lista"** está disponível em **Configurações** para quando o usuário desejar sincronizar manualmente.

### C. Agrupamento e Limpeza de Séries
- Sanitização de títulos idêntica ao Android TV (remoção de tags de qualidade `4K`, `FHD`, `DUBLADO`, `LEG`, `[BR]`, anos, etc.).
- Suporte a múltiplos padrões de temporada/episódio: `S01E01`, `1x01`, `T01E01`, `Temporada 1 Episodio 1`, `Ep 01`.
- Ordenação rigorosa por temporada e episódio (`season ASC, num ASC`).
- Exibição de quantidade de temporadas e episódios nos cards (ex: `2 Temp • 24 Eps`).
- Pôsteres de séries são carregados automaticamente via TMDB mesmo quando a playlist não inclui `tvg-logo`.

### D. Ordenação de Categorias de TV Ao Vivo
- Implementado `SharedCatOrder` com suporte à ordem original do provedor e ordem alfabética (A–Z).
- Opção para fixar categorias favoritas no topo com ícone de estrela (★).

### E. Resolução de EPG Xtream
- Suporte a múltiplos formatos de stream Xtream (`.ts`, `.m3u8`, com ou sem `/live/`, portas diversas).
- Fallback automático para credenciais salvas do portal caso a URL do canal não traga usuário e senha explícitos.
- Suporte a timestamps numéricos Unix e strings formatadas (`yyyy-MM-dd HH:mm:ss`), além de respeito ao status `now_playing` do servidor.
- Carregamento de EPG assíncrono para os canais visíveis na lista.

### F. Franquias / Coleções TMDB Isoladas
- As coleções (ex: Franquia "Rio", "Harry Potter", etc.) agora filtram estritamente filmes (`Kind.MOVIE`), impedindo que canais de TV ao vivo com o mesmo nome apareçam como partes da franquia.
- Normalização de títulos por correspondência alfanumérica e tolerância de ano.

### G. Aba e Botão de Busca
- Adicionada a aba **Pesquisar** (ícone de Lupa) na barra lateral de navegação.
- Adicionado botão **"🔍 Buscar Títulos"** no banner superior da tela inicial.
- Filtros em tempo real por "Todos", "Filmes", "Séries" e "Ao Vivo".

---

## 3. Como Compilar e Executar no Windows

### Pré-requisitos
- JDK 17 configurado.
- VLC Media Player 64-bit instalado no Windows (`C:\Program Files\VideoLAN\VLC`).

### Comandos de Compilação
```powershell
# Gerar o executável Windows standalone:
./gradlew :desktopApp:createDistributable

# Caminho do executável gerado:
# desktopApp/build/compose/binaries/main/app/KorpTV/KorpTV.exe
```

---

## 4. Próximos Passos (Caso decida ativar no Android ou Web)

1. **Para Android:**
   - Implementar `SharedVideoPlayer.android.kt` conectando com `androidx.media3:media3-exoplayer`.
   - Ajustar a persistência do cache no `SharedPortalManager` para usar `context.filesDir` no Android.
   - Alterar `MainActivity.kt` no módulo `:app` para chamar `setContent { SharedApp() }`.

2. **Para Web (Kotlin/Wasm):**
   - Implementar player HTML5 com `hls.js` em `SharedVideoPlayer.wasmJs.kt`.
   - Utilizar o servidor backend Node.js (porta 3002) como proxy de streaming para contornar restrições de CORS do navegador.
   - Utilizar `IndexedDB` para o cache local da playlist.
