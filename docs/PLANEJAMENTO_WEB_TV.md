# Planejamento Arquitetural: Korp TV Cross-Platform (PC Web, Samsung Tizen & LG webOS)

Este documento define o plano técnico completo para rodar a aplicação **Korp TV** em navegadores de PC e nas Smart TVs da Samsung (Tizen OS) e LG (webOS), com backend centralizado, banco de dados escalável e suporte a controle remoto D-Pad nativo.

---

## 1. Visão Geral da Arquitetura

```
                      ┌────────────────────────────────────────┐
                      │          Backend / API Gateway         │
                      │  (Node.js Fastify / Express ou Go)    │
                      └──────────────────┬─────────────────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        │                                │                                │
 ┌──────▼──────┐                  ┌──────▼──────┐                  ┌──────▼──────┐
 │  Navegador  │                  │ Samsung TV  │                  │    LG TV    │
 │   PC Web    │                  │  Tizen OS   │                  │  webOS TV   │
 └─────────────┘                  └─────────────┘                  └─────────────┘
  - Chrome/Edge/Firefox            - App Hosted (.wgt)              - App Hosted (.ipk)
  - Transmux MPEG-TS (Hls.js)      - AVPlay Hardware                - HTML5 MSE Hardware
  - Teclado / Mouse                - D-Pad + Teclas TV              - Magic Remote + D-Pad
```

O aplicativo funciona no formato **Hosted Web App**:
- O código HTML5, CSS e JS fica hospedado no servidor central.
- As Smart TVs instalam um pacote minúsculo (menor que 50 KB) que apenas abre a URL da aplicação em tela cheia com permissões nativas de controle remoto.
- **Vantagem:** Qualquer melhoria, correção de canal ou atualização visual feita no servidor reflete na hora no PC e em todas as TVs, sem necessidade de atualizar o aplicativo na loja ou via pendrive.

---

## 2. Banco de Dados e Gerenciamento de Listas

O banco de dados foi estruturado para suportar múltiplos clientes, listas Xtream/M3U, canais com cache local, EPG e preferências individuais de cada dispositivo.

O schema SQL completo está versionado em [`docs/DATABASE_SCHEMA.sql`](./DATABASE_SCHEMA.sql).

### Principais Entidades:
1. **`clients`**: Cadastro dos clientes, status da assinatura (`active`, `expired`, `trial`), número de conexões simultâneas e expiração.
2. **`client_playlists`**: Credenciais Xtream (host, user, pass) ou M3U do cliente, com suporte a **servidores de backup (failover automático)**.
3. **`categories` & `stream_items`**: Categorias e catálogo de canais ao vivo, filmes e séries sincronizados localmente no banco para carregamento instantâneo na TV.
4. **`series_episodes`**: Episódios e temporadas estruturados para navegação sob demanda.
5. **`epg_programmes`**: Cache do guia de programação (EPG) indexado por canal e faixa de horário.
6. **`connected_devices`**: Registro das TVs e navegadores ativos, incluindo o código de pareamento rápido de 6 dígitos (`quick_pair_code`).
7. **`user_settings` & `user_library`**: Resolução padrão (`SD`, `HD`, `FHD`, `4K`), favoritos com ordenação personalizada e histórico de progresso ("Continuar assistindo").

---

## 3. Estratégia de Execução por Plataforma

### A. Execução no PC (Navegadores Modernos: Chrome, Edge, Firefox, Brave)
- **Desafio do MPEG-TS:** O elemento `<video>` nativo dos navegadores de computador não toca arquivos `.ts` comuns de canais ao vivo IPTV.
- **Solução:**
  - O frontend carrega a biblioteca **Hls.js** via CDN.
  - Para fluxos que vêm em MPEG-TS direto, o backend disponibiliza um endpoint de proxy de streaming (`/api/stream/:id`) que encapsula o stream em chunks HLS para reprodução suave no browser do computador.
- **Desafio de CORS & Mixed Content:**
  - Se a página web rodar em `https://...`, o navegador bloqueia chamadas HTTP diretas para servidores Xtream não seguros.
  - **Solução:** Todas as chamadas para a API do provedor e streams passam pelo backend (`proxy`), eliminando qualquer bloqueio de CORS ou Mixed Content no PC.

---

### B. Execução na Samsung Smart TV (Tizen OS 4.0 a 8.0+)
- **Formato:** Pacote Hosted Web Application (`.wgt`) contendo o `config.xml`.
- **Motor de Vídeo:**
  - Utiliza a API nativa de decodificação por hardware **`webapis.avplay`**.
  - Suporta fluxos `MPEG-TS`, `HLS`, `MP4`, `MKV` e `4K UHD` com aceleração de chip da TV e baixíssimo consumo de CPU.
- **Controle Remoto:**
  - Registro obrigatório de teclas via `tizen.tvinputdevice.registerKey(...)`.
  - Tratamento da tecla **Return/Voltar (`10009`)** para navegação hierárquica (Player -> Canais -> Categorias -> Menu Lateral -> Sair do App com `tizen.application.getCurrentApplication().exit()`).
  - Teclas de Cores nativas: 🔴 Favoritos (`403`), 🟢 Qualidade (`404`), 🟡 Áudio (`405`), 🔵 Guia TV (`406`).

---

### C. Execução na LG Smart TV (webOS TV 3.0 a 24+)
- **Formato:** Pacote Hosted Web Application (`.ipk`) contendo o `appinfo.json`.
- **Motor de Vídeo:**
  - Tag HTML5 `<video>` nativa do webOS com aceleração de hardware e Media Source Extensions (MSE).
- **Controle Remoto & Magic Remote:**
  - Configuração obrigatória no manifesto: `"disableBackHistoryAPI": true`.
  - Captura da tecla **Voltar do webOS (`461`)**.
  - Suporte híbrido ao cursor do **Magic Remote** (ponteiro na tela via `mousemove`) e retorno instantâneo ao modo foco D-Pad ao pressionar qualquer seta direcional.
- **Ciclo de Vida (App Lifecycle):**
  - Evento `visibilitychange`: ao alternar de aplicativo ou desligar a tela da TV, o player suspende o stream para evitar estouro de memória RAM e encerramento forçado pelo sistema da LG.

---

## 4. Fluxo de Autenticação na TV: Pareamento Rápido (Quick Connect)

Digitar usuário e senha longos usando o controle remoto da TV é uma das piores experiências para o cliente. O Korp TV adota o fluxo de **Pareamento por Código de 6 Dígitos**:

```
 1. A TV é ligada pela primeira vez.
 2. A tela exibe um código simples de 6 dígitos e um QR Code:
    "Acesse portal.kabaweb.com e digite o código: 849-215"
 3. O cliente ou o administrador abre o celular/computador e digita o código.
 4. O backend vincula a TV à conta e lista do cliente.
 5. A TV atualiza automaticamente via WebSocket/SSE e entra direto no catálogo!
```

---

## 5. Cronograma de Implementação (Roadmap)

### Fase 1: Backend & Banco de Dados (Dias 1 a 3)
- [x] Criação do schema SQL unificado (`clients`, `playlists`, `streams`, `devices`, `library`).
- [ ] Implementação das rotas da API no backend (Fastify / Express):
  - `/api/auth/pair`: Geração e validação do código de 6 dígitos.
  - `/api/xtream/sync`: Sincronização em background das categorias e canais da lista.
  - `/api/stream/proxy`: Proxy de stream inteligente para contornar Mixed Content no PC.

### Fase 2: Integração do Frontend Web (Dias 4 a 5)
- [x] Protótipo 10-foot UI com Bootstrap 5.3, Billboard e Shelves de Filmes/Séries (`web-tv-demo.html`).
- [x] Motor D-Pad com suporte a Tizen (`10009`) e webOS (`461`).
- [ ] Conectar os componentes visuais do HTML aos endpoints da API REST / WebSocket.

### Fase 3: Empacotamento para Smart TVs (Dias 6 a 7)
- [x] Gerar `smart-tv/samsung-tizen/config.xml` com privilégios AVPlay e InputDevice.
- [x] Gerar `smart-tv/lg-webos/appinfo.json` com `disableBackHistoryAPI`.
- [ ] Build do pacote Samsung `.wgt` usando `agente-samsung-tv` (Tizen CLI).
- [ ] Build do pacote LG `.ipk` usando `agente-lg-tv` (webOS Ares CLI).

### Fase 4: Deploy & Testes em Produção (Dia 8)
- [ ] Configuração do serviço no Docker Swarm / Portainer (stack web + Postgres).
- [ ] Teste de carga e validação em TVs reais Samsung e LG.
