# Korp TV Microservice (Cross-Platform Backend)

Microserviço dedicado de alta performance para alimentar a versão Web e Smart TVs (Samsung Tizen e LG webOS) do Korp TV.

Construído sob a filosofia **Ponytail / Zero-Bloat**:
- **Runtime:** Node.js moderno (v20+) com módulos 100% nativos (`node:http`, `node:sqlite`, `node:crypto`, `fetch` global).
- **Sem dependências externas:** Inicializa em menos de 50 milissegundos e consome menos de 30 MB de RAM.
- **Banco de Dados:** SQLite nativo de alta velocidade com modo WAL (`node:sqlite`).

---

## 🚀 Como Iniciar

### 1. Inicializar e Popular Banco de Dados
```bash
node src/seed.js
```

### 2. Iniciar o Servidor
```bash
node src/server.js
```
Acesse a interface e a API em:
- **Interface Web / Smart TV:** `http://localhost:3333/` ou `http://localhost:3333/web-tv-demo.html`
- **Healthcheck & Status:** `http://localhost:3333/health`

### 3. Rodar Testes Automatizados
```bash
node --test test/*.test.js
```

---

## 📡 Endpoints da API REST

| Método | Rota | Descrição |
| :--- | :--- | :--- |
| `GET` | `/health` | Status do serviço, uptime e contagem de registros do banco. |
| `GET` | `/api/categories?type=live` | Listagem de categorias (ordem oficial Xtream). |
| `GET` | `/api/streams?type=live&cat=1` | Listagem de canais ao vivo ou catálogo VOD. |
| `GET` | `/api/epg/:channelId` | Programação atual e próxima (EPG) do canal. |
| `POST` | `/api/auth/pair` | Gera código de 6 dígitos para pareamento rápido da TV. |
| `POST` | `/api/auth/verify` | Valida código e associa a TV à conta do cliente. |
| `GET` | `/api/settings/:clientId` | Configurações de reprodução (resolução padrão, fallback). |
| `POST` | `/api/settings/:clientId` | Atualiza configurações de reprodução do cliente. |
| `GET` | `/api/stream/proxy?url=...` | Proxy de streaming que elimina bloqueios de CORS e Mixed Content no PC. |
