import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const dataDir = join(__dirname, '..', 'data');

if (!existsSync(dataDir)) {
  mkdirSync(dataDir, { recursive: true });
}

const dbPath = process.env.DATABASE_PATH || join(dataDir, 'korp_tv.db');
const db = new DatabaseSync(dbPath);

// Otimizações de desempenho para Smart TV e concorrência
db.exec(`
  PRAGMA journal_mode = WAL;
  PRAGMA synchronous = NORMAL;
  PRAGMA foreign_keys = ON;
`);

/**
 * Criação de tabelas do banco de dados dedicado
 */
export function initDatabase() {
  db.exec(`
    -- 1. Clientes
    CREATE TABLE IF NOT EXISTS clients (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      whatsapp TEXT,
      max_connections INTEGER DEFAULT 1,
      status TEXT DEFAULT 'active',
      expires_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    -- 2. Listas do Cliente (Xtream / M3U)
    CREATE TABLE IF NOT EXISTS client_playlists (
      id TEXT PRIMARY KEY,
      client_id TEXT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
      name TEXT DEFAULT 'Lista Principal',
      type TEXT NOT NULL DEFAULT 'xtream',
      server_url TEXT NOT NULL,
      username TEXT,
      password TEXT,
      backup_urls TEXT,
      m3u_url TEXT,
      status TEXT DEFAULT 'online',
      last_synced_at INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL
    );

    -- 3. Categorias
    CREATE TABLE IF NOT EXISTS categories (
      id TEXT PRIMARY KEY,
      playlist_id TEXT NOT NULL REFERENCES client_playlists(id) ON DELETE CASCADE,
      category_id TEXT NOT NULL,
      name TEXT NOT NULL,
      type TEXT NOT NULL, -- 'live', 'movie', 'series'
      sort_order INTEGER DEFAULT 999,
      is_hidden INTEGER DEFAULT 0,
      created_at INTEGER NOT NULL
    );

    -- 4. Canais e Catálogo VOD
    CREATE TABLE IF NOT EXISTS stream_items (
      id TEXT PRIMARY KEY,
      playlist_id TEXT NOT NULL REFERENCES client_playlists(id) ON DELETE CASCADE,
      category_id TEXT,
      stream_id TEXT NOT NULL,
      name TEXT NOT NULL,
      stream_type TEXT NOT NULL, -- 'live', 'movie', 'series'
      stream_icon TEXT,
      epg_channel_id TEXT,
      direct_source TEXT,
      container_extension TEXT DEFAULT 'mp4',
      resolution TEXT DEFAULT 'SD',
      rating REAL DEFAULT 0.0,
      release_date TEXT,
      plot TEXT,
      sort_order INTEGER DEFAULT 0,
      is_active INTEGER DEFAULT 1,
      created_at INTEGER NOT NULL
    );

    -- 5. Séries - Episódios
    CREATE TABLE IF NOT EXISTS series_episodes (
      id TEXT PRIMARY KEY,
      series_item_id TEXT NOT NULL REFERENCES stream_items(id) ON DELETE CASCADE,
      season_num INTEGER NOT NULL,
      episode_num INTEGER NOT NULL,
      title TEXT NOT NULL,
      container_extension TEXT DEFAULT 'mp4',
      direct_source TEXT,
      duration_min INTEGER DEFAULT 0,
      plot TEXT,
      created_at INTEGER NOT NULL
    );

    -- 6. Guia EPG
    CREATE TABLE IF NOT EXISTS epg_programmes (
      id TEXT PRIMARY KEY,
      channel_id TEXT NOT NULL,
      title TEXT NOT NULL,
      description TEXT,
      start_time INTEGER NOT NULL,
      end_time INTEGER NOT NULL,
      category TEXT,
      created_at INTEGER NOT NULL
    );

    -- 7. Dispositivos Pareados (Smart TVs Samsung, LG, PC)
    CREATE TABLE IF NOT EXISTS connected_devices (
      id TEXT PRIMARY KEY,
      client_id TEXT REFERENCES clients(id) ON DELETE CASCADE,
      device_type TEXT NOT NULL,
      device_name TEXT,
      model_name TEXT,
      ip_address TEXT,
      app_version TEXT,
      quick_pair_code TEXT,
      quick_pair_expires INTEGER DEFAULT 0,
      last_active_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );

    -- 8. Configurações de Reprodução do Cliente
    CREATE TABLE IF NOT EXISTS user_settings (
      client_id TEXT PRIMARY KEY REFERENCES clients(id) ON DELETE CASCADE,
      default_resolution TEXT DEFAULT 'auto',
      auto_fallback INTEGER DEFAULT 1,
      preferred_audio TEXT DEFAULT 'por',
      subtitles_enabled INTEGER DEFAULT 0,
      parental_pin TEXT,
      adult_content_locked INTEGER DEFAULT 1,
      theme TEXT DEFAULT 'dark',
      updated_at INTEGER NOT NULL
    );

    -- 9. Biblioteca do Cliente (Favoritos & Histórico)
    CREATE TABLE IF NOT EXISTS user_library (
      id TEXT PRIMARY KEY,
      client_id TEXT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
      stream_item_id TEXT NOT NULL REFERENCES stream_items(id) ON DELETE CASCADE,
      item_type TEXT NOT NULL,
      is_favorite INTEGER DEFAULT 0,
      favorite_order INTEGER DEFAULT 999,
      progress_seconds INTEGER DEFAULT 0,
      total_duration_seconds INTEGER DEFAULT 0,
      is_completed INTEGER DEFAULT 0,
      last_watched_at INTEGER NOT NULL
    );

    -- Índices para buscas ultrarrápidas
    CREATE INDEX IF NOT EXISTS idx_stream_items_type ON stream_items(stream_type);
    CREATE INDEX IF NOT EXISTS idx_stream_items_cat ON stream_items(category_id);
    CREATE INDEX IF NOT EXISTS idx_categories_type ON categories(type);
    CREATE INDEX IF NOT EXISTS idx_devices_pair ON connected_devices(quick_pair_code);
    CREATE INDEX IF NOT EXISTS idx_library_client ON user_library(client_id, is_favorite);
  `);
  console.log('[DB] Banco de dados Korp TV inicializado com sucesso em:', dbPath);
}

export { db };
