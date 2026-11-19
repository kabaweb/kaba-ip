import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';
import { db, initDatabase } from './db.js';

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
      const catId = urlObj.searchParams.get('cat');
      const limit = parseInt(urlObj.searchParams.get('limit') || '50', 10);
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

    // 8. API: Configurações de Reprodução do Cliente
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
