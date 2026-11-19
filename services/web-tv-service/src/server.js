import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';
import { db, initDatabase } from './db.js';
import { PlaylistSyncService } from './syncService.js';

initDatabase();

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3333;
const HOST = process.env.HOST || '0.0.0.0';

// Caminho do protótipo HTML da TV
const HTML_PATH = join(__dirname, '..', '..', '..', 'web-tv-demo.html');

/**
 * Utilitários de resposta HTTP
 */
function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

function derivePairingCode(mac, deviceId) {
  const source = (mac || '').replace(/[^a-zA-Z0-9]/g, '') || (deviceId || '').replace(/[^a-zA-Z0-9]/g, '');
  const hex = source.slice(-6).toUpperCase();
  if (hex.length === 6) {
    return `${hex.slice(0, 3)}-${hex.slice(3)}`;
  }
  return hex || '000-000';
}

function parseBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        resolve({});
      }
    });
  });
}

/**
 * Servidor HTTP do Microserviço Korp TV
 */
const server = createServer(async (req, res) => {
  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = urlObj.pathname;
  const method = req.method;

  // Preflight CORS
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    res.end();
    return;
  }

  try {
    // 1. Rota Raiz: Serve a interface Web para Smart TVs e PC
    if (pathname === '/' || pathname === '/index.html' || pathname === '/web-tv-demo.html') {
      if (existsSync(HTML_PATH)) {
        const html = readFileSync(HTML_PATH, 'utf-8');
        res.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Access-Control-Allow-Origin': '*'
        });
        res.end(html);
        return;
      }
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('HTML da TV não encontrado');
      return;
    }

    // 2. Healthcheck & Estatísticas
    if (pathname === '/health' && method === 'GET') {
      const clientsCount = db.prepare('SELECT count(*) as c FROM clients').get().c;
      const streamsCount = db.prepare('SELECT count(*) as c FROM stream_items').get().c;
      const categoriesCount = db.prepare('SELECT count(*) as c FROM categories').get().c;

      sendJson(res, 200, {
        status: 'online',
        service: 'korp-web-tv-microservice',
        uptime_seconds: Math.floor(process.uptime()),
        timestamp: Date.now(),
        database: {
          type: 'sqlite',
          clients: clientsCount,
          streams: streamsCount,
          categories: categoriesCount
        }
      });
      return;
    }

    // 3. API: Listagem de Categorias
    if (pathname === '/api/categories' && method === 'GET') {
      const type = urlObj.searchParams.get('type');
      let query = 'SELECT * FROM categories WHERE is_hidden = 0';
      const params = [];
      if (type) {
        query += ' AND type = ?';
        params.push(type);
      }
      query += ' ORDER BY sort_order ASC, name ASC';
      const rows = db.prepare(query).all(...params);
      sendJson(res, 200, { success: true, count: rows.length, categories: rows });
      return;
    }

    // 4. API: Listagem de Canais / VOD
    if (pathname === '/api/streams' && method === 'GET') {
      const type = urlObj.searchParams.get('type'); // 'live', 'movie', 'series'
      const catId = urlObj.searchParams.get('cat') || urlObj.searchParams.get('category_id');
      const limit = parseInt(urlObj.searchParams.get('limit') || '100', 10);
      const offset = parseInt(urlObj.searchParams.get('offset') || '0', 10);

      let query = 'SELECT * FROM stream_items WHERE is_active = 1';
      const params = [];
      if (type) {
        query += ' AND stream_type = ?';
        params.push(type);
      }
      if (catId) {
        query += ' AND category_id = ?';
        params.push(catId);
      }
      query += ' ORDER BY sort_order ASC, id ASC LIMIT ? OFFSET ?';
      params.push(limit, offset);

      const rows = db.prepare(query).all(...params);
      sendJson(res, 200, { success: true, count: rows.length, streams: rows });
      return;
    }

    // 5. API: Guia EPG de Canal
    if (pathname.startsWith('/api/epg/') && method === 'GET') {
      const channelId = pathname.replace('/api/epg/', '');
      const rows = db.prepare(`
        SELECT * FROM epg_programmes 
        WHERE channel_id = ? OR channel_id = ? 
        ORDER BY start_time ASC LIMIT 5
      `).all(channelId, `epg-${channelId}`);
      sendJson(res, 200, { success: true, channelId, programmes: rows });
      return;
    }

    // 6. API: Pareamento Rápido na Smart TV (Código de 6 dígitos)
    if (pathname === '/api/auth/pair' && method === 'POST') {
      const body = await parseBody(req);
      const deviceType = body.device_type || 'smart_tv';
      const deviceName = body.device_name || 'TV Sala';

      // Gera código de 6 dígitos aleatório (ex: 839214)
      const pairCode = Math.floor(100000 + Math.random() * 900000).toString();
      const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutos de validade
      const deviceId = randomUUID();

      db.prepare(`
        INSERT INTO connected_devices (id, device_type, device_name, quick_pair_code, quick_pair_expires, last_active_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).run(deviceId, deviceType, deviceName, pairCode, expiresAt, Date.now(), Date.now());

      sendJson(res, 201, {
        success: true,
        device_id: deviceId,
        pair_code: pairCode,
        expires_in_seconds: 300,
        instructions: `Acesse o portal e digite o código: ${pairCode}`
      });
      return;
    }

    // 7. API: Validação do Código de Pareamento pelo Celular/Portal
    if (pathname === '/api/auth/verify' && method === 'POST') {
      const body = await parseBody(req);
      const { pair_code, client_id } = body;

      if (!pair_code || !client_id) {
        sendJson(res, 400, { success: false, error: 'pair_code e client_id são obrigatórios' });
        return;
      }

      const device = db.prepare(`
        SELECT * FROM connected_devices 
        WHERE quick_pair_code = ? AND quick_pair_expires > ?
      `).get(pair_code, Date.now());

      if (!device) {
        sendJson(res, 404, { success: false, error: 'Código de pareamento inválido ou expirado' });
        return;
      }

      db.prepare(`
        UPDATE connected_devices 
        SET client_id = ?, quick_pair_code = NULL, last_active_at = ?
        WHERE id = ?
      `).run(client_id, Date.now(), device.id);

      sendJson(res, 200, { success: true, message: 'Dispositivo pareado com sucesso à conta', device_id: device.id });
      return;
    }

    // 8. API: Sincronização Direta de Playlist (Xtream Codes ou M3U)
    if (pathname === '/api/playlist/sync' && method === 'POST') {
      const body = await parseBody(req);
      const { type, host, username, password, m3u_url, client_id } = body;
      const cId = client_id || 'client-demo-1';

      // Cria ou busca playlist
      let playlist = db.prepare('SELECT * FROM client_playlists WHERE client_id = ?').get(cId);
      const pId = playlist ? playlist.id : randomUUID();

      if (type === 'm3u') {
        if (!m3u_url) {
          sendJson(res, 400, { success: false, error: 'm3u_url é obrigatório' });
          return;
        }
        db.prepare(`
          INSERT OR REPLACE INTO client_playlists (id, client_id, name, type, server_url, m3u_url, status, created_at)
          VALUES (?, ?, ?, 'm3u', ?, ?, 'syncing', ?)
        `).run(pId, cId, 'Lista M3U', m3u_url, m3u_url, Date.now());

        const result = await PlaylistSyncService.syncM3U(pId, m3u_url);
        sendJson(res, 200, { success: true, message: 'Lista M3U sincronizada com sucesso', ...result });
        return;
      }

      // Xtream Codes
      if (!host || !username || !password) {
        sendJson(res, 400, { success: false, error: 'host, username e password são obrigatórios para Xtream' });
        return;
      }

      db.prepare(`
        INSERT OR REPLACE INTO client_playlists (id, client_id, name, type, server_url, username, password, status, created_at)
        VALUES (?, ?, ?, 'xtream', ?, ?, ?, 'syncing', ?)
      `).run(pId, cId, 'Lista Xtream', host, username, password, Date.now());

      const result = await PlaylistSyncService.syncXtream(pId, host, username, password);
      sendJson(res, 200, { success: true, message: 'Lista Xtream sincronizada com sucesso', ...result });
      return;
    }

    // 9. API: Conexão e Sincronização com o Portal Korp TV Oficial
    if (pathname === '/api/portal/connect' && method === 'POST') {
      const body = await parseBody(req);
      const DEFAULT_PORTAL_URL = process.env.PORTAL_URL || 'https://korp.kabaweb.in';
      const portalUrl = (body.portal_url || DEFAULT_PORTAL_URL).replace(/\/+$/, '');
      const deviceId = body.device_id || 'web-tv-' + randomBytes(4).toString('hex');
      const model = body.model || 'Smart TV Web Edition';

      console.log(`[Portal Oficial] Registrando/Verificando no portal ${portalUrl} como ${deviceId}...`);

      const portalRes = await fetch(`${portalUrl}/api/device/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          deviceId,
          mac: body.mac || '',
          pairingCode: body.pairing_code || undefined,
          model,
          appVersion: '1.6.4-web',
          versionCode: 31
        })
      });

      if (!portalRes.ok) {
        throw new Error(`Portal oficial retornou status HTTP ${portalRes.status}`);
      }

      const portalData = await portalRes.json();
      const p = portalData.playlist;
      console.log(`[Portal Oficial] Resposta da playlist para ${deviceId}:`, JSON.stringify(p));

      // Extrai credenciais Xtream caso seja Xtream direto OU uma URL M3U com parâmetros Xtream embutidos
      let xtreamCreds = null;
      if (p && p.host && p.user && p.pass) {
        xtreamCreds = { host: p.host, user: p.user, pass: p.pass };
      } else if (p && p.url) {
        try {
          const u = new URL(p.url);
          const user = u.searchParams.get('username') || u.searchParams.get('user') || p.user;
          const pass = u.searchParams.get('password') || u.searchParams.get('pass') || p.pass;
          if (user && pass) {
            xtreamCreds = { host: `${u.protocol}//${u.host}`, user, pass };
          }
        } catch (_) {}
      }

      let syncedCount = 0;
      let syncError = null;
      const pId = 'playlist-portal-' + deviceId;

      if (xtreamCreds) {
        try {
          console.log(`[Portal] Sincronizando Xtream (${xtreamCreds.host}, usuário: ${xtreamCreds.user})...`);
          db.prepare(`
            INSERT OR REPLACE INTO client_playlists (id, client_id, name, type, server_url, username, password, status, created_at)
            VALUES (?, 'client-demo-1', 'Lista Oficial do Portal', 'xtream', ?, ?, ?, 'syncing', ?)
          `).run(pId, xtreamCreds.host, xtreamCreds.user, xtreamCreds.pass, Date.now());

          // Limpa canais anteriores antes de sincronizar nova versão
          db.prepare("DELETE FROM stream_items WHERE playlist_id = ?").run(pId);
          db.prepare("DELETE FROM categories WHERE playlist_id = ?").run(pId);

          const syncRes = await PlaylistSyncService.syncXtream(pId, xtreamCreds.host, xtreamCreds.user, xtreamCreds.pass);
          syncedCount = syncRes.channelsCount || 0;
          console.log(`[Portal] Xtream sincronizado: ${syncedCount} canais.`);
        } catch (err) {
          console.error(`[Portal] Erro ao sincronizar Xtream: ${err.message}`);
          syncError = err.message;
        }
      } else if (p && p.url) {
        try {
          console.log(`[Portal] Sincronizando M3U direto: ${p.url}...`);
          db.prepare(`
            INSERT OR REPLACE INTO client_playlists (id, client_id, name, type, server_url, username, password, status, created_at)
            VALUES (?, 'client-demo-1', 'Lista Oficial do Portal', 'm3u', ?, '', '', 'syncing', ?)
          `).run(pId, p.url, Date.now());

          db.prepare("DELETE FROM stream_items WHERE playlist_id = ?").run(pId);
          db.prepare("DELETE FROM categories WHERE playlist_id = ?").run(pId);

          const syncRes = await PlaylistSyncService.syncM3U(pId, p.url);
          syncedCount = syncRes.channelsCount || 0;
          console.log(`[Portal] M3U sincronizado: ${syncedCount} canais.`);
        } catch (err) {
          console.error(`[Portal] Erro ao sincronizar M3U: ${err.message}`);
          syncError = err.message;
        }
      }

      const hasPlaylist = Boolean(xtreamCreds || (p && p.url));

      sendJson(res, 200, {
        success: true,
        portalUrl,
        portalConnected: true,
        deviceStatus: portalData.status,
        hasPlaylist,
        playlist: portalData.playlist,
        channelsSynced: syncedCount,
        syncError,
        pairingCode: portalData.pairingCode || derivePairingCode('', deviceId)
      });
      return;
    }

    // 10. API: Limpar Playlist / Desconectar
    if (pathname === '/api/playlist/clear' && method === 'POST') {
      db.prepare("DELETE FROM stream_items").run();
      db.prepare("DELETE FROM categories").run();
      db.prepare("DELETE FROM client_playlists").run();
      sendJson(res, 200, { success: true, message: 'Dados de canais e lista limpos com sucesso' });
      return;
    }

    // 11. API: Status Geral do Dispositivo e Playlist
    if (pathname === '/api/device/status' && method === 'GET') {
      const totalChannels = db.prepare("SELECT count(*) as c FROM stream_items WHERE stream_type = 'live'").get().c;
      const totalMovies = db.prepare("SELECT count(*) as c FROM stream_items WHERE stream_type = 'movie'").get().c;
      const totalCategories = db.prepare("SELECT count(*) as c FROM categories").get().c;
      const activePlaylist = db.prepare("SELECT * FROM client_playlists ORDER BY last_synced_at DESC LIMIT 1").get();

      sendJson(res, 200, {
        success: true,
        channels: totalChannels,
        movies: totalMovies,
        categories: totalCategories,
        playlist: activePlaylist || null,
        isReady: totalChannels > 0
      });
      return;
    }

    // 11. API: Configurações de Reprodução do Cliente
    if (pathname.startsWith('/api/settings/') && method === 'GET') {
      const clientId = pathname.replace('/api/settings/', '');
      const settings = db.prepare('SELECT * FROM user_settings WHERE client_id = ?').get(clientId);
      sendJson(res, 200, { success: true, settings: settings || { default_resolution: 'sd', auto_fallback: 1 } });
      return;
    }

    if (pathname.startsWith('/api/settings/') && method === 'POST') {
      const clientId = pathname.replace('/api/settings/', '');
      const body = await parseBody(req);
      const defRes = body.default_resolution || 'sd';
      const autoFallback = body.auto_fallback !== undefined ? body.auto_fallback : 1;

      db.prepare(`
        INSERT OR REPLACE INTO user_settings (client_id, default_resolution, auto_fallback, updated_at)
        VALUES (?, ?, ?, ?)
      `).run(clientId, defRes, autoFallback, Date.now());

      sendJson(res, 200, { success: true, message: 'Configurações atualizadas' });
      return;
    }

    // 9. API: Streaming Proxy (Resolve CORS e Mixed Content no PC Web)
    if (pathname === '/api/stream/proxy' && method === 'GET') {
      const targetUrl = urlObj.searchParams.get('url');
      if (!targetUrl) {
        sendJson(res, 400, { success: false, error: 'Parâmetro url é obrigatório' });
        return;
      }

      try {
        const streamResponse = await fetch(targetUrl);
        const headers = {
          'Access-Control-Allow-Origin': '*',
          'Content-Type': streamResponse.headers.get('content-type') || 'video/mp4'
        };
        res.writeHead(streamResponse.status, headers);
        const reader = streamResponse.body.getReader();

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          res.write(value);
        }
        res.end();
      } catch (proxyErr) {
        sendJson(res, 502, { success: false, error: 'Erro ao conectar ao stream remoto', details: proxyErr.message });
      }
      return;
    }

    // 404 Padrão
    sendJson(res, 404, { success: false, error: 'Endpoint não encontrado' });
  } catch (err) {
    console.error('[Server Error]:', err);
    sendJson(res, 500, { success: false, error: 'Erro interno no microserviço', details: err.message });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`=======================================================`);
  console.log(`🚀 Korp TV Microservice rodando com sucesso!`);
  console.log(`📡 URL Principal: http://localhost:${PORT}/`);
  console.log(`📺 Interface TV:  http://localhost:${PORT}/web-tv-demo.html`);
  console.log(`🩺 Healthcheck:   http://localhost:${PORT}/health`);
  console.log(`=======================================================`);
});
