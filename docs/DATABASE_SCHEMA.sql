-- ==============================================================================
-- Korp TV Web & Smart TV - Database Schema (PostgreSQL & SQLite Compatible)
-- Suporte completo para Clientes, Listas (Xtream/M3U), Canais, VOD, EPG e Configurações
-- ==============================================================================

-- 1. TABELA DE CLIENTES / USUÁRIOS
CREATE TABLE IF NOT EXISTS clients (
    id VARCHAR(64) PRIMARY KEY,                    -- UUID ou código do cliente
    name VARCHAR(150) NOT NULL,
    username VARCHAR(80) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    whatsapp VARCHAR(30),
    max_connections INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'active',          -- 'active', 'expired', 'blocked', 'trial'
    expires_at BIGINT NOT NULL,                    -- Timestamp epoch em milissegundos
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);
CREATE INDEX IF NOT EXISTS idx_clients_username ON clients(username);

-- 2. LISTAS IPTV (XTREAM CODES / M3U / STALKER) VINCULADAS AO CLIENTE
CREATE TABLE IF NOT EXISTS client_playlists (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    name VARCHAR(100) DEFAULT 'Lista Principal',
    type VARCHAR(20) NOT NULL DEFAULT 'xtream',    -- 'xtream', 'm3u_url', 'stalker'
    server_url TEXT NOT NULL,                     -- http://provedor.net:8080
    username VARCHAR(100),
    password VARCHAR(100),
    backup_urls TEXT,                             -- JSON Array de servidores de backup para failover automático
    m3u_url TEXT,                                 -- Se tipo m3u_url
    status VARCHAR(20) DEFAULT 'online',          -- 'online', 'offline', 'error'
    last_synced_at BIGINT DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_playlists_client ON client_playlists(client_id);

-- 3. CATEGORIAS DE CANAIS E VOD
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(64) PRIMARY KEY,
    playlist_id VARCHAR(64) NOT NULL REFERENCES client_playlists(id) ON DELETE CASCADE,
    category_id VARCHAR(50) NOT NULL,             -- ID vindo do Xtream
    name VARCHAR(150) NOT NULL,
    type VARCHAR(20) NOT NULL,                    -- 'live', 'movie', 'series'
    sort_order INTEGER DEFAULT 999,
    is_hidden INTEGER DEFAULT 0,                  -- 1 = ocultar da navegação
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_categories_playlist_type ON categories(playlist_id, type);

-- 4. CANAIS AO VIVO E CONTEÚDO VOD (FILMES E SÉRIES)
CREATE TABLE IF NOT EXISTS stream_items (
    id VARCHAR(64) PRIMARY KEY,
    playlist_id VARCHAR(64) NOT NULL REFERENCES client_playlists(id) ON DELETE CASCADE,
    category_id VARCHAR(50),
    stream_id VARCHAR(50) NOT NULL,               -- ID de stream do provedor
    name VARCHAR(255) NOT NULL,
    stream_type VARCHAR(20) NOT NULL,             -- 'live', 'movie', 'series'
    stream_icon TEXT,
    epg_channel_id VARCHAR(100),
    direct_source TEXT,
    container_extension VARCHAR(10) DEFAULT 'mp4',-- 'ts', 'm3u8', 'mp4', 'mkv'
    resolution VARCHAR(10) DEFAULT 'SD',          -- 'SD', 'HD', 'FHD', '4K'
    rating NUMERIC(3,1) DEFAULT 0.0,              -- Nota TMDB / IMDb (ex: 8.5)
    release_date VARCHAR(30),
    plot TEXT,
    sort_order INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stream_items_cat ON stream_items(category_id);
CREATE INDEX IF NOT EXISTS idx_stream_items_type ON stream_items(stream_type);

-- 5. TEMPORADAS E EPISÓDIOS DE SÉRIES
CREATE TABLE IF NOT EXISTS series_episodes (
    id VARCHAR(64) PRIMARY KEY,
    series_item_id VARCHAR(64) NOT NULL REFERENCES stream_items(id) ON DELETE CASCADE,
    season_num INTEGER NOT NULL,
    episode_num INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    container_extension VARCHAR(10) DEFAULT 'mp4',
    direct_source TEXT,
    duration_min INTEGER DEFAULT 0,
    plot TEXT,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_episodes_series ON series_episodes(series_item_id, season_num);

-- 6. CACHE DO GUIA DE PROGRAMAÇÃO (EPG XMLTV)
CREATE TABLE IF NOT EXISTS epg_programmes (
    id VARCHAR(64) PRIMARY KEY,
    channel_id VARCHAR(100) NOT NULL,             -- epg_channel_id
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_time BIGINT NOT NULL,                   -- Epoch timestamp
    end_time BIGINT NOT NULL,
    category VARCHAR(80),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_epg_channel_time ON epg_programmes(channel_id, start_time, end_time);

-- 7. DISPOSITIVOS E SESSÕES CONECTADAS (TV SAMSUNG, LG, BROWSER PC)
CREATE TABLE IF NOT EXISTS connected_devices (
    id VARCHAR(64) PRIMARY KEY,                    -- UUID do dispositivo ou MAC hash
    client_id VARCHAR(64) NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    device_type VARCHAR(30) NOT NULL,             -- 'samsung_tizen', 'lg_webos', 'pc_browser'
    device_name VARCHAR(150),
    model_name VARCHAR(100),
    ip_address VARCHAR(50),
    app_version VARCHAR(30),
    quick_pair_code VARCHAR(6),                   -- Código temporário de 6 dígitos para login na TV
    quick_pair_expires BIGINT DEFAULT 0,
    last_active_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_client ON connected_devices(client_id);
CREATE INDEX IF NOT EXISTS idx_devices_pair ON connected_devices(quick_pair_code);

-- 8. PREFERÊNCIAS E CONFIGURAÇÕES DO USUÁRIO NA TV/PC
CREATE TABLE IF NOT EXISTS user_settings (
    client_id VARCHAR(64) PRIMARY KEY REFERENCES clients(id) ON DELETE CASCADE,
    default_resolution VARCHAR(10) DEFAULT 'auto',-- 'auto', 'sd', 'hd', 'fhd', '4k'
    auto_fallback INTEGER DEFAULT 1,              -- 1 = Fallback de resolução habilitado
    preferred_audio VARCHAR(30) DEFAULT 'por',
    subtitles_enabled INTEGER DEFAULT 0,
    parental_pin VARCHAR(64),                     -- Hash SHA-256 do PIN
    adult_content_locked INTEGER DEFAULT 1,
    theme VARCHAR(20) DEFAULT 'dark',
    updated_at BIGINT NOT NULL
);

-- 9. HISTÓRICO DE REPRODUÇÃO & FAVORITOS DO CLIENTE
CREATE TABLE IF NOT EXISTS user_library (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    stream_item_id VARCHAR(64) NOT NULL REFERENCES stream_items(id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL,               -- 'live', 'movie', 'series'
    is_favorite INTEGER DEFAULT 0,
    favorite_order INTEGER DEFAULT 999,
    progress_seconds INTEGER DEFAULT 0,
    total_duration_seconds INTEGER DEFAULT 0,
    is_completed INTEGER DEFAULT 0,
    last_watched_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_library_unique ON user_library(client_id, stream_item_id);
CREATE INDEX IF NOT EXISTS idx_library_fav ON user_library(client_id, is_favorite);
CREATE INDEX IF NOT EXISTS idx_library_history ON user_library(client_id, last_watched_at);
