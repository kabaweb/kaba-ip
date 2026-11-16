const db = require('../db');
const { getSettings } = require('./automationService');

/**
 * Serviço de Metadados com Cache em Banco de Dados (SQLite / PostgreSQL)
 * Reduz consumo de cotas das APIs do TMDB e OMDB armazenando resultados globalmente.
 */

async function getStats() {
  try {
    const resTotal = await db.query('SELECT count(*) as c FROM metadata_cache');
    const total = parseInt(resTotal.rows[0]?.c || '0', 10);

    const resTmdb = await db.query("SELECT count(*) as c FROM metadata_cache WHERE provider = 'tmdb'");
    const tmdb = parseInt(resTmdb.rows[0]?.c || '0', 10);

    const resOmdb = await db.query("SELECT count(*) as c FROM metadata_cache WHERE provider = 'omdb'");
    const omdb = parseInt(resOmdb.rows[0]?.c || '0', 10);

    return { total, tmdb, omdb };
  } catch (err) {
    return { total: 0, tmdb: 0, omdb: 0, error: err.message };
  }
}

async function clearCache(provider = null) {
  if (provider) {
    await db.query('DELETE FROM metadata_cache WHERE provider = ?', [provider]);
  } else {
    await db.query('DELETE FROM metadata_cache');
  }
  return getStats();
}

async function testKeys({ tmdbKey, omdbKey }) {
  const settings = await getSettings();
  const finalTmdb = tmdbKey !== undefined ? tmdbKey.trim() : (settings.tmdb_api_key || '');
  const finalOmdb = omdbKey !== undefined ? omdbKey.trim() : (settings.omdb_api_key || '');

  const results = {
    tmdb: { configured: Boolean(finalTmdb), ok: false, message: 'Não configurada' },
    omdb: { configured: Boolean(finalOmdb), ok: false, message: 'Não configurada' }
  };

  if (finalTmdb) {
    try {
      const res = await fetch(`https://api.themoviedb.org/3/configuration?api_key=${encodeURIComponent(finalTmdb)}`);
      if (res.ok) {
        results.tmdb = { configured: true, ok: true, message: 'Chave TMDB conectada com sucesso!' };
      } else {
        const body = await res.json().catch(() => ({}));
        results.tmdb = { configured: true, ok: false, message: body.status_message || `HTTP ${res.status}` };
      }
    } catch (err) {
      results.tmdb = { configured: true, ok: false, message: err.message };
    }
  }

  if (finalOmdb) {
    try {
      const res = await fetch(`https://www.omdbapi.com/?apikey=${encodeURIComponent(finalOmdb)}&i=tt0111161`);
      const body = await res.json().catch(() => ({}));
      if (body.Response === 'True') {
        results.omdb = { configured: true, ok: true, message: 'Chave OMDB conectada com sucesso (Shawshank Redemption OK)!' };
      } else {
        results.omdb = { configured: true, ok: false, message: body.Error || 'Chave OMDB rejeitada ou inválida.' };
      }
    } catch (err) {
      results.omdb = { configured: true, ok: false, message: err.message };
    }
  }

  return results;
}

// TTL padrão de cache: 30 dias (em milissegundos)
const CACHE_TTL = 30 * 24 * 60 * 60 * 1000;

async function getCached(cacheKey) {
  try {
    const res = await db.query('SELECT data_json, updated_at FROM metadata_cache WHERE cache_key = ?', [cacheKey]);
    if (res.rows.length === 0) return null;
    const row = res.rows[0];
    const age = Date.now() - Number(row.updated_at || 0);
    if (age > CACHE_TTL) {
      // Expirou, apaga em background
      db.query('DELETE FROM metadata_cache WHERE cache_key = ?', [cacheKey]).catch(() => {});
      return null;
    }
    return JSON.parse(row.data_json);
  } catch (_) {
    return null;
  }
}

async function setCached(cacheKey, provider, data) {
  try {
    const jsonStr = JSON.stringify(data);
    const now = Date.now();
    const isPg = db.query.toString().includes('paramIndex'); // ou checa pelo driver
    // Inserção com upsert
    const exists = (await db.query('SELECT cache_key FROM metadata_cache WHERE cache_key = ?', [cacheKey])).rows.length > 0;
    if (exists) {
      await db.query('UPDATE metadata_cache SET data_json = ?, updated_at = ? WHERE cache_key = ?', [jsonStr, now, cacheKey]);
    } else {
      await db.query('INSERT INTO metadata_cache (cache_key, provider, data_json, updated_at) VALUES (?, ?, ?, ?)', [cacheKey, provider, jsonStr, now]);
    }
  } catch (err) {
    console.error('[MetaCache Error]', err.message);
  }
}

/**
 * Consulta TMDB através do cache do portal
 */
async function fetchTmdb(subpath, queryObj = {}) {
  const settings = await getSettings();
  const apiKey = settings.tmdb_api_key;
  if (!apiKey) throw new Error('Chave TMDB não configurada no portal.');

  const cleanSubpath = subpath.replace(/^\/+/, '');
  const params = new URLSearchParams(queryObj);
  params.delete('api_key'); // Normaliza

  const cacheKey = `tmdb:${cleanSubpath}:${params.toString()}`;
  const hit = await getCached(cacheKey);
  if (hit) return { data: hit, cached: true };

  params.set('api_key', apiKey);
  const url = `https://api.themoviedb.org/3/${cleanSubpath}?${params.toString()}`;
  const res = await fetch(url);
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({}));
    throw new Error(errBody.status_message || `TMDB HTTP ${res.status}`);
  }
  const data = await res.json();
  await setCached(cacheKey, 'tmdb', data);
  return { data, cached: false };
}

/**
 * Consulta OMDB através do cache do portal
 */
async function fetchOmdb(queryObj = {}) {
  const settings = await getSettings();
  const apiKey = settings.omdb_api_key;
  if (!apiKey) throw new Error('Chave OMDB não configurada no portal.');

  const params = new URLSearchParams(queryObj);
  params.delete('apikey');

  const cacheKey = `omdb:${params.toString()}`;
  const hit = await getCached(cacheKey);
  if (hit) return { data: hit, cached: true };

  params.set('apikey', apiKey);
  const url = `https://www.omdbapi.com/?${params.toString()}`;
  const res = await fetch(url);
  const data = await res.json();
  if (data.Response === 'True') {
    await setCached(cacheKey, 'omdb', data);
  }
  return { data, cached: false };
}

module.exports = {
  getStats,
  clearCache,
  testKeys,
  fetchTmdb,
  fetchOmdb
};
