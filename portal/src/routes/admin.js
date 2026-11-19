const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const multer = require('multer');
const rateLimit = require('express-rate-limit');
const AppInfoParser = require('app-info-parser');
const db = require('../db');
const config = require('../config');
const { checkPlaylistValidity } = require('../services/playlistChecker');
const { checkBasePlaylist } = require('../services/basePlaylistChecker');
const { getSettings, saveSettings, migrateAllClientsUrl, calculateDaysRemaining, runExpiryAutomations } = require('../services/automationService');
const { sendWhatsAppMessage } = require('../services/whatsappService');
const metadataService = require('../services/metadataService');

// Rate limiter contra ataques de força bruta no login (máx 5 tentativas por IP a cada 15 min)
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  skipSuccessfulRequests: true,
  message: {
    success: false,
    error: 'Muitas tentativas de login incorretas. Por segurança, tente novamente em 15 minutos.'
  },
  standardHeaders: true,
  legacyHeaders: false
});

function safeCompare(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  if (bufA.length !== bufB.length) {
    crypto.timingSafeEqual(bufA, bufA);
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

// Sessões ativas em memória e tokens assinados
const activeSessions = new Map();

function generateToken(user = { id: 0, username: 'admin', role: 'admin', name: 'Administrador' }) {
  const expiresAt = Date.now() + (30 * 24 * 60 * 60 * 1000); // 30 dias
  const uid = user.id !== undefined ? user.id : 0;
  const uname = user.username || 'admin';
  const urole = user.role || 'operator';
  const data = `${uid}:${uname}:${urole}:${expiresAt}`;
  const sig = crypto.createHmac('sha256', config.sessionSecret || 'ipvio-stream-secret-key').update(data).digest('hex');
  return `${Buffer.from(data).toString('base64url')}.${sig}`;
}

function verifyToken(token) {
  if (!token) return null;
  if (config.adminKey && token === config.adminKey) {
    return { id: 0, username: 'admin', role: 'admin', name: 'Administrador' };
  }

  // 1. Tenta validar token assinado
  try {
    const parts = token.split('.');
    if (parts.length === 2) {
      const [payloadB64, sig] = parts;
      const data = Buffer.from(payloadB64, 'base64url').toString('utf8');
      const expectedSig = crypto.createHmac('sha256', config.sessionSecret || 'ipvio-stream-secret-key').update(data).digest('hex');
      if (crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expectedSig))) {
        const [uidStr, username, role, expStr] = data.split(':');
        const expiresAt = parseInt(expStr, 10);
        if (expiresAt > Date.now()) {
          return { id: parseInt(uidStr, 10) || 0, username, role: role || 'operator', expiresAt };
        }
      }
    }
  } catch (_) {}

  // 2. Fallback para mapa em memória
  const session = activeSessions.get(token);
  if (session && session.expiresAt > Date.now()) {
    return session;
  }

  return null;
}

// Configuração do multer para upload de APKs
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    if (!fs.existsSync(config.apkDir)) {
      fs.mkdirSync(config.apkDir, { recursive: true });
    }
    cb(null, config.apkDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname) || '.apk';
    const base = path.basename(file.originalname, ext).replace(/[^a-zA-Z0-9_-]/g, '_');
    cb(null, `${base}_${Date.now()}${ext}`);
  }
});
const upload = multer({
  storage,
  limits: { fileSize: 150 * 1024 * 1024 }, // Até 150MB
  fileFilter: (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if (ext !== '.apk') {
      return cb(new Error('Apenas arquivos instaladores com extensão .apk são permitidos.'));
    }
    cb(null, true);
  }
});

// 1. Rota de Login (Suporta múltiplos usuários e admin padrão)
router.post('/login', loginLimiter, async (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ success: false, error: 'Usuário e senha são obrigatórios.' });
  }

  const cleanUser = String(username).trim();

  // 1.1 Tenta buscar usuário no banco
  try {
    const userRes = await db.query('SELECT * FROM portal_users WHERE LOWER(username) = LOWER(?)', [cleanUser]);
    let user = userRes.rows[0];

    // 1.2 Fallback para credenciais padrão do config se ainda não existir no banco
    if (!user && safeCompare(cleanUser, config.adminUser) && safeCompare(password, config.adminPass)) {
      user = { id: 0, username: config.adminUser, name: 'Administrador', role: 'admin' };
    } else if (user) {
      const validPass = db.verifyPassword(password, user.password_hash);
      if (!validPass) {
        return res.status(401).json({ success: false, error: 'Usuário ou senha incorretos.' });
      }
    } else {
      return res.status(401).json({ success: false, error: 'Usuário ou senha incorretos.' });
    }

    const token = generateToken(user);
    const expiresAt = Date.now() + (30 * 24 * 60 * 60 * 1000);
    activeSessions.set(token, { id: user.id, username: user.username, role: user.role, name: user.name, expiresAt });

    return res.json({
      success: true,
      token,
      user: {
        id: user.id,
        username: user.username,
        name: user.name || user.username,
        role: user.role || 'operator'
      },
      expiresAt
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'Erro no servidor: ' + err.message });
  }
});

// Middleware de autenticação
function authMiddleware(req, res, next) {
  const token = req.headers['x-admin-token'] || 
                req.headers['x-admin-key'] || 
                req.query.token || 
                (req.headers['authorization']?.replace(/^Bearer\s+/i, ''));

  if (!token) {
    return res.status(401).json({ success: false, error: 'Acesso não autorizado. Faça login.' });
  }

  const session = verifyToken(token);
  if (session) {
    req.user = session;
    return next();
  }

  return res.status(401).json({ success: false, error: 'Sessão expirada. Faça login novamente.' });
}

// Middleware para restringir apenas administradores
function requireAdmin(req, res, next) {
  if (req.user && req.user.role === 'admin') {
    return next();
  }
  return res.status(403).json({ success: false, error: 'Acesso restrito para administradores.' });
}

// Helper para verificar permissão do usuário sobre um dispositivo
async function canAccessDevice(user, deviceId) {
  if (!user) return false;
  if (user.role === 'admin') return true;
  const res = await db.query('SELECT owner_id FROM devices WHERE id = ?', [deviceId]);
  if (res.rows.length === 0) return false;
  const ownerId = res.rows[0].owner_id;
  // Operador só pode acessar se o aparelho estiver livre (sem dono) OU pertencer a ele
  return ownerId === null || ownerId === undefined || Number(ownerId) === Number(user.id);
}

router.use(authMiddleware);

// 1.1 Dados do usuário logado
router.get('/me', async (req, res) => {
  res.json({
    success: true,
    user: {
      id: req.user.id,
      username: req.user.username,
      name: req.user.name || req.user.username,
      role: req.user.role
    }
  });
});

// 1.2 Lista de operadores para seleção de responsável (Acessível a todos autenticados)
router.get('/operators', async (req, res) => {
  try {
    const result = await db.query('SELECT id, username, name, role FROM portal_users ORDER BY name ASC');
    res.json({ success: true, operators: result.rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 1.3 Gerenciamento de Usuários (Apenas Administrador)
router.get('/users', requireAdmin, async (req, res) => {
  try {
    const result = await db.query('SELECT id, username, name, role, created_at FROM portal_users ORDER BY id ASC');
    res.json({ success: true, users: result.rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/users', requireAdmin, async (req, res) => {
  try {
    const { username, password, name, role } = req.body;
    if (!username || !password || !name) {
      return res.status(400).json({ success: false, error: 'Nome, usuário e senha são obrigatórios.' });
    }
    const cleanUser = String(username).trim();
    const existing = await db.query('SELECT id FROM portal_users WHERE LOWER(username) = LOWER(?)', [cleanUser]);
    if (existing.rows.length > 0) {
      return res.status(400).json({ success: false, error: 'Este nome de usuário já está em uso.' });
    }

    const hash = db.hashPassword(password);
    const assignedRole = role === 'admin' ? 'admin' : 'operator';
    const now = Date.now();

    await db.query(
      'INSERT INTO portal_users (username, password_hash, name, role, created_at) VALUES (?, ?, ?, ?, ?)',
      [cleanUser, hash, name.trim(), assignedRole, now]
    );

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.put('/users/:id', requireAdmin, async (req, res) => {
  try {
    const { username, password, name, role } = req.body;
    const userId = parseInt(req.params.id, 10);
    const userRes = await db.query('SELECT * FROM portal_users WHERE id = ?', [userId]);
    if (userRes.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Usuário não encontrado.' });
    }

    if (username) {
      const cleanUser = String(username).trim();
      const duplicate = await db.query('SELECT id FROM portal_users WHERE LOWER(username) = LOWER(?) AND id != ?', [cleanUser, userId]);
      if (duplicate.rows.length > 0) {
        return res.status(400).json({ success: false, error: 'Este nome de usuário já pertence a outra pessoa.' });
      }
    }

    const assignedRole = role === 'admin' ? 'admin' : (role === 'operator' ? 'operator' : undefined);

    if (password && String(password).trim().length > 0) {
      const hash = db.hashPassword(String(password).trim());
      await db.query(
        `UPDATE portal_users SET 
          username = COALESCE(?, username), 
          password_hash = ?, 
          name = COALESCE(?, name), 
          role = COALESCE(?, role) 
         WHERE id = ?`,
        [username?.trim() || null, hash, name?.trim() || null, assignedRole || null, userId]
      );
    } else {
      await db.query(
        `UPDATE portal_users SET 
          username = COALESCE(?, username), 
          name = COALESCE(?, name), 
          role = COALESCE(?, role) 
         WHERE id = ?`,
        [username?.trim() || null, name?.trim() || null, assignedRole || null, userId]
      );
    }

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.delete('/users/:id', requireAdmin, async (req, res) => {
  try {
    const userId = parseInt(req.params.id, 10);
    if (req.user.id === userId) {
      return res.status(400).json({ success: false, error: 'Você não pode excluir sua própria conta.' });
    }

    // Reverte aparelhos desse usuário para sem dono
    await db.query('UPDATE devices SET owner_id = NULL WHERE owner_id = ?', [userId]);
    await db.query('DELETE FROM portal_users WHERE id = ?', [userId]);

    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 2. Listar dispositivos
router.get('/devices', async (req, res) => {
  try {
    let sql = `
      SELECT d.*, u.name AS owner_name, u.username AS owner_username 
      FROM devices d 
      LEFT JOIN portal_users u ON d.owner_id = u.id
    `;
    const params = [];

    // Se for operador, só vê aparelhos sem dono (livres) OU atribuídos a ele
    if (req.user.role !== 'admin') {
      sql += ' WHERE (d.owner_id IS NULL OR d.owner_id = ?) ';
      params.push(req.user.id);
    }

    sql += ' ORDER BY d.last_seen DESC ';

    const result = await db.query(sql, params);
    const now = Date.now();
    const devices = result.rows.map(d => {
      let code = d.pairing_code;
      if (!code) {
        const src = (d.mac || '').replace(/[^a-zA-Z0-9]/g, '') || (d.id || '').replace(/[^a-zA-Z0-9]/g, '');
        const hex = src.slice(-6).toUpperCase();
        code = hex.length === 6 ? `${hex.slice(0, 3)}-${hex.slice(3)}` : (hex || '-');
      }
      const isOnline = (now - Number(d.last_seen || 0)) < 3 * 60 * 1000;
      const isPlaying = Boolean(isOnline && d.now_playing && (now - Number(d.now_playing_at || 0)) < 6 * 60 * 1000);
      const daysLeft = calculateDaysRemaining(d.playlist_exp_date);

      return {
        ...d,
        pairing_code: code,
        isOnline,
        isPlaying,
        daysLeft,
        currentPlaying: isPlaying ? d.now_playing : null,
        currentPlayingType: isPlaying ? d.now_playing_type : null
      };
    });
    res.json({ success: true, devices });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 3. Detalhes de um dispositivo
router.get('/devices/:id', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }
    const result = await db.query(
      `SELECT d.*, u.name AS owner_name, u.username AS owner_username 
       FROM devices d 
       LEFT JOIN portal_users u ON d.owner_id = u.id 
       WHERE d.id = ?`,
      [req.params.id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Dispositivo não encontrado' });
    }
    res.json({ success: true, device: result.rows[0] });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4. Atualizar dados básicos, telefone ou status do dispositivo
router.put('/devices/:id', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const { name, status, clientPhone, clientName, notes, phone, ownerId, expDate } = req.body;
    const finalPhone = clientPhone !== undefined ? clientPhone : (phone !== undefined ? phone : null);
    const finalName = clientName !== undefined ? clientName : (name !== undefined ? name : null);

    let parsedOwner = undefined;
    if (ownerId !== undefined) {
      if (req.user.role === 'admin') {
        parsedOwner = ownerId === '' || ownerId === null ? null : parseInt(ownerId, 10);
      } else {
        parsedOwner = ownerId === '' || ownerId === null ? null : req.user.id;
      }
    }

    await db.query(
      `UPDATE devices SET 
        name = COALESCE(?, name), 
        status = COALESCE(?, status), 
        client_phone = COALESCE(?, client_phone),
        client_name = COALESCE(?, client_name),
        notes = COALESCE(?, notes),
        owner_id = CASE WHEN ? = 1 THEN ? ELSE owner_id END,
        playlist_exp_date = COALESCE(?, playlist_exp_date)
       WHERE id = ?`,
      [
        name !== undefined ? name : null,
        status !== undefined ? status : null,
        finalPhone,
        finalName,
        notes !== undefined ? notes : null,
        parsedOwner !== undefined ? 1 : 0,
        parsedOwner !== undefined ? parsedOwner : null,
        expDate !== undefined ? expDate : null,
        req.params.id
      ]
    );
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.1 Configurações globais e automações (Apenas Administrador)
router.get('/settings', requireAdmin, async (req, res) => {
  try {
    const settings = await getSettings();
    res.json({ success: true, settings });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/settings', requireAdmin, async (req, res) => {
  try {
    const updated = await saveSettings(req.body);
    res.json({ success: true, settings: updated });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.1.1 Checagem manual da Lista Base
router.post('/base-playlist/check', requireAdmin, async (req, res) => {
  try {
    const result = await checkBasePlaylist(true); // forçado manualmente pelo admin
    res.json(result);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.2 Migração em massa da URL/Host IPTV para TODOS os clientes conectados (Apenas Administrador)
router.post('/iptv/migrate-url', requireAdmin, async (req, res) => {
  try {
    const { newUrl } = req.body;
    if (!newUrl) {
      return res.status(400).json({ success: false, error: 'Nova URL do IPTV é obrigatória' });
    }
    const result = await migrateAllClientsUrl(newUrl);
    res.json(result);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.3 Teste de envio de WhatsApp oficial (Apenas Administrador)
router.post('/whatsapp/test', requireAdmin, async (req, res) => {
  try {
    const { apiUrl, apiToken, phone, message } = req.body;
    const settings = await getSettings();
    const finalUrl = apiUrl || settings.whatsapp_api_url;
    const finalToken = apiToken || settings.whatsapp_api_token;

    const result = await sendWhatsAppMessage({
      apiUrl: finalUrl,
      apiToken: finalToken,
      phone,
      message: message || '✅ Teste de conexão com a API do WhatsApp via Portal IPVIO Stream!'
    });
    res.json(result);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.4 Disparo manual/imediato das automações de vencimento (Apenas Administrador)
router.post('/automations/run-expiry', requireAdmin, async (req, res) => {
  try {
    const result = await runExpiryAutomations();
    res.json(result);
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4.5 Metadados (TMDB / OMDB): estatísticas de cache, teste de chaves e limpeza (Apenas Administrador)
router.get('/meta/stats', requireAdmin, async (req, res) => {
  try {
    const stats = await metadataService.getStats();
    res.json({ success: true, stats });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/meta/test', requireAdmin, async (req, res) => {
  try {
    const { tmdbKey, omdbKey } = req.body;
    const results = await metadataService.testKeys({ tmdbKey, omdbKey });
    res.json({ success: true, results });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/meta/clear-cache', requireAdmin, async (req, res) => {
  try {
    const { provider } = req.body;
    const stats = await metadataService.clearCache(provider);
    res.json({ success: true, message: 'Cache de metadados limpo com sucesso.', stats });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 5. Atribuir ou atualizar playlist remotamente (com checagem de validade e responsável)
router.post('/devices/link-by-code', async (req, res) => {
  try {
    const { code, type, url, host, user, pass, phone, name, notes, ownerId, expDate: manualExp } = req.body;
    if (!code) {
      return res.status(400).json({ success: false, error: 'Código de pareamento ou MAC é obrigatório.' });
    }

    const cleanInput = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');

    const result = await db.query(
      `SELECT * FROM devices 
       WHERE REPLACE(UPPER(COALESCE(pairing_code, '')), '-', '') = ?
          OR REPLACE(UPPER(COALESCE(mac, '')), ':', '') LIKE ?
          OR UPPER(id) LIKE ?
       ORDER BY last_seen DESC LIMIT 1`,
      [cleanInput, `%${cleanInput}`, `%${cleanInput}`]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ success: false, error: `Nenhum aparelho encontrado com o código "${code}". Certifique-se de que o aplicativo está aberto.` });
    }

    const dev = result.rows[0];
    const allowed = await canAccessDevice(req.user, dev.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Este aparelho já está vinculado a outro operador.' });
    }

    const now = Date.now();
    const settings = await getSettings();
    let resolvedType = type || 'none';
    if ((resolvedType === 'none' || !resolvedType) && (user || pass)) {
      resolvedType = 'xtream';
    } else if ((resolvedType === 'none' || !resolvedType) && url) {
      resolvedType = 'm3u';
    }

    const effectiveHost = (host && host.trim()) || (resolvedType === 'xtream' ? (settings.default_iptv_url || '') : '');

    let expDate = manualExp || null;
    let plStatus = 'Active';
    let maxCons = 1;

    if (resolvedType !== 'none') {
      const checkRes = await checkPlaylistValidity({ type: resolvedType, url, host: effectiveHost, user, pass });
      if (checkRes.success) {
        expDate = manualExp || checkRes.expDateFormatted;
        plStatus = checkRes.status;
        maxCons = checkRes.maxConnections || 1;
      } else if (!manualExp) {
        expDate = 'Erro ao verificar';
      }
    }

    let parsedOwner = undefined;
    if (ownerId !== undefined) {
      parsedOwner = (req.user.role === 'admin') ? (ownerId ? parseInt(ownerId, 10) : null) : (ownerId ? req.user.id : null);
    } else if (req.user.role !== 'admin' && dev.owner_id === null) {
      parsedOwner = req.user.id;
    }

    await db.query(
      `UPDATE devices SET 
        name = COALESCE(?, name),
        client_name = COALESCE(?, client_name),
        playlist_type = ?, 
        playlist_url = ?, 
        xtream_host = ?, 
        xtream_user = ?, 
        xtream_pass = ?, 
        playlist_updated_at = ?,
        playlist_exp_date = ?,
        playlist_status = ?,
        playlist_max_cons = ?,
        client_phone = COALESCE(?, client_phone),
        notes = COALESCE(?, notes),
        owner_id = CASE WHEN ? = 1 THEN ? ELSE owner_id END
       WHERE id = ?`,
      [
        name || null, name || null, resolvedType, url || '', effectiveHost || '', user || '', pass || '',
        now, expDate, plStatus, maxCons, phone || null, notes || null,
        parsedOwner !== undefined ? 1 : 0, parsedOwner !== undefined ? parsedOwner : null,
        dev.id
      ]
    );

    res.json({
      success: true,
      deviceId: dev.id,
      deviceName: dev.name,
      pairingCode: dev.pairing_code,
      updatedAt: now,
      validity: { expDate, status: plStatus, maxCons }
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

router.post('/devices/:id/playlist', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const { type, url, host, user, pass, name, clientName, clientPhone, phone, notes, ownerId, expDate: manualExp } = req.body;
    const now = Date.now();

    let resolvedType = type || 'none';
    if ((resolvedType === 'none' || !resolvedType) && (user || pass)) {
      resolvedType = 'xtream';
    } else if ((resolvedType === 'none' || !resolvedType) && url) {
      resolvedType = 'm3u';
    }

    const settings = await getSettings();
    const effectiveHost = (host && host.trim()) || (resolvedType === 'xtream' ? (settings.default_iptv_url || '') : '');

    let expDate = manualExp || null;
    let plStatus = 'Active';
    let maxCons = 1;

    // Consulta validade da lista no servidor do provedor
    if (resolvedType !== 'none') {
      const checkRes = await checkPlaylistValidity({ type: resolvedType, url, host: effectiveHost, user, pass });
      if (checkRes.success) {
        expDate = manualExp || checkRes.expDateFormatted;
        plStatus = checkRes.status;
        maxCons = checkRes.maxConnections || 1;
      } else if (!manualExp) {
        expDate = 'Erro ao verificar';
      }
    }

    const finalPhone = clientPhone !== undefined ? clientPhone : (phone !== undefined ? phone : null);
    const finalName = clientName !== undefined ? clientName : (name !== undefined ? name : null);

    let parsedOwner = undefined;
    if (ownerId !== undefined) {
      parsedOwner = (req.user.role === 'admin') ? (ownerId ? parseInt(ownerId, 10) : null) : (ownerId ? req.user.id : null);
    } else if (req.user.role !== 'admin') {
      const devRes = await db.query('SELECT owner_id FROM devices WHERE id = ?', [req.params.id]);
      if (devRes.rows[0]?.owner_id === null) {
        parsedOwner = req.user.id;
      }
    }

    await db.query(
      `UPDATE devices SET 
        name = COALESCE(?, name),
        client_name = COALESCE(?, client_name),
        client_phone = COALESCE(?, client_phone),
        notes = COALESCE(?, notes),
        playlist_type = ?, 
        playlist_url = ?, 
        xtream_host = ?, 
        xtream_user = ?, 
        xtream_pass = ?, 
        playlist_updated_at = ?,
        playlist_exp_date = ?,
        playlist_status = ?,
        playlist_max_cons = ?,
        owner_id = CASE WHEN ? = 1 THEN ? ELSE owner_id END
       WHERE id = ?`,
      [
        finalName,
        finalName,
        finalPhone,
        notes !== undefined ? notes : null,
        resolvedType,
        url || '',
        effectiveHost || '',
        user || '',
        pass || '',
        now,
        expDate,
        plStatus,
        maxCons,
        parsedOwner !== undefined ? 1 : 0,
        parsedOwner !== undefined ? parsedOwner : null,
        req.params.id
      ]
    );

    res.json({
      success: true,
      updatedAt: now,
      validity: {
        expDate,
        status: plStatus,
        maxCons
      }
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 6. Consultar / Atualizar validade da playlist sob demanda
router.post('/devices/:id/check-validity', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const devRes = await db.query('SELECT * FROM devices WHERE id = ?', [req.params.id]);
    if (devRes.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Dispositivo não encontrado' });
    }
    const dev = devRes.rows[0];
    let effectiveType = dev.playlist_type;
    if (!effectiveType || effectiveType === 'none') {
      if (dev.xtream_user || dev.xtream_pass) effectiveType = 'xtream';
      else if (dev.playlist_url) effectiveType = 'm3u';
      else return res.status(400).json({ success: false, error: 'Dispositivo não tem playlist atribuída' });
    }

    const settings = await getSettings();
    const effectiveHost = dev.xtream_host || (effectiveType === 'xtream' ? (settings.default_iptv_url || '') : '');

    const checkRes = await checkPlaylistValidity({
      type: effectiveType,
      url: dev.playlist_url,
      host: effectiveHost,
      user: dev.xtream_user,
      pass: dev.xtream_pass
    });

    if (checkRes.success) {
      await db.query(
        "UPDATE devices SET playlist_type = ?, xtream_host = COALESCE(NULLIF(xtream_host, ''), ?), playlist_exp_date = ?, playlist_status = ?, playlist_max_cons = ? WHERE id = ?",
        [effectiveType, effectiveHost, checkRes.expDateFormatted, checkRes.status, checkRes.maxConnections || 1, req.params.id]
      );
      return res.json({
        success: true,
        expDate: checkRes.expDateFormatted,
        daysRemaining: checkRes.daysRemaining,
        isExpired: checkRes.isExpired,
        status: checkRes.status,
        maxCons: checkRes.maxConnections
      });
    } else {
      return res.status(400).json({ success: false, error: checkRes.error });
    }
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 6.1 Forçar recarga imediata de lista para um dispositivo
router.post('/devices/:id/force-refresh', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const devRes = await db.query('SELECT * FROM devices WHERE id = ?', [req.params.id]);
    if (devRes.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Dispositivo não encontrado' });
    }
    const dev = devRes.rows[0];
    const now = Date.now();

    await db.query(
      'UPDATE devices SET playlist_updated_at = ? WHERE id = ?',
      [now, dev.id]
    );

    res.json({
      success: true,
      deviceId: dev.id,
      updatedAt: now,
      message: 'Comando de recarga enviado! O aplicativo recarregará a lista completa no próximo sinal.'
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 7. Enviar mensagem push para tela do dispositivo
router.post('/devices/:id/message', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const { message, type } = req.body;
    const msgId = 'msg_' + Date.now();
    await db.query(
      'UPDATE devices SET message = ?, message_id = ?, message_type = ? WHERE id = ?',
      [message || null, message ? msgId : null, type || 'info', req.params.id]
    );
    res.json({ success: true, messageId: msgId });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 7.1 Enviar alerta de vencimento sob demanda para a TV/WhatsApp do cliente
router.post('/devices/:id/expiry-reminder', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    const devRes = await db.query('SELECT * FROM devices WHERE id = ?', [req.params.id]);
    if (devRes.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Dispositivo não encontrado' });
    }
    const dev = devRes.rows[0];

    const cfg = await getSettings();
    const daysLeft = calculateDaysRemaining(dev.playlist_exp_date);
    const daysStr = daysLeft !== null ? String(daysLeft) : '?';
    const dateStr = dev.playlist_exp_date || 'em breve';

    const messageText = (cfg.auto_expiry_message || 'Olá {{nome}}! Sua assinatura vence em {{dias}} dia(s) (no dia {{data}}). Renove para continuar assistindo!')
      .replace(/\{\{nome\}\}/g, dev.client_name || dev.name || 'Cliente')
      .replace(/\{\{dias\}\}/g, daysStr)
      .replace(/\{\{data\}\}/g, dateStr)
      .replace(/\{\{aparelho\}\}/g, dev.model || 'sua TV');

    const msgId = 'exp_manual_' + Date.now();
    await db.query(
      'UPDATE devices SET message = ?, message_id = ?, message_type = ?, last_expiry_notified_at = ? WHERE id = ?',
      [messageText, msgId, 'warning', Date.now(), dev.id]
    );

    let sentWa = false;
    if (dev.client_phone && cfg.whatsapp_api_url && cfg.whatsapp_api_token) {
      try {
        await sendWhatsAppMessage({
          apiUrl: cfg.whatsapp_api_url,
          apiToken: cfg.whatsapp_api_token,
          phone: dev.client_phone,
          message: messageText
        });
        sentWa = true;
      } catch (e) {
        console.warn('[Manual Expiry WA Error]', e.message);
      }
    }

    res.json({ success: true, message: messageText, sentWa, daysLeft });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 8. Enviar mensagem em massa (Broadcast)
router.post('/devices/broadcast-message', async (req, res) => {
  try {
    const { message, type } = req.body;
    const msgId = 'broadcast_' + Date.now();

    if (req.user.role === 'admin') {
      await db.query(
        'UPDATE devices SET message = ?, message_id = ?, message_type = ? WHERE status = ?',
        [message, msgId, type || 'info', 'active']
      );
    } else {
      await db.query(
        'UPDATE devices SET message = ?, message_id = ?, message_type = ? WHERE status = ? AND owner_id = ?',
        [message, msgId, type || 'info', 'active', req.user.id]
      );
    }
    res.json({ success: true, messageId: msgId });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 9. Remover dispositivo
router.delete('/devices/:id', async (req, res) => {
  try {
    const allowed = await canAccessDevice(req.user, req.params.id);
    if (!allowed) {
      return res.status(403).json({ success: false, error: 'Acesso negado a este dispositivo.' });
    }

    await db.query('DELETE FROM devices WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 10. Obter configurações de OTA
router.get('/ota', async (req, res) => {
  try {
    const result = await db.query('SELECT * FROM ota_updates ORDER BY version_code DESC');
    res.json({ success: true, updates: result.rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 11. Registrar nova versão OTA manualmente
router.post('/ota', requireAdmin, async (req, res) => {
  try {
    const { versionName, versionCode, downloadUrl, changelog, mandatory } = req.body;
    if (!versionName || !versionCode || !downloadUrl) {
      return res.status(400).json({ success: false, error: 'versionName, versionCode e downloadUrl são obrigatórios' });
    }
    const now = Date.now();
    await db.query(
      `INSERT INTO ota_updates (version_name, version_code, download_url, changelog, mandatory, released_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [versionName, parseInt(versionCode, 10), downloadUrl, changelog || '', mandatory ? 1 : 0, now]
    );
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 12. Upload direto do APK para servir via OTA (com extração automática de versão)
router.post('/ota/upload', requireAdmin, upload.single('apk'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, error: 'Nenhum arquivo APK enviado.' });
    }
    const { changelog, mandatory } = req.body;
    let finalVersionName = (req.body.versionName || '').trim();
    let finalVersionCode = req.body.versionCode ? parseInt(req.body.versionCode, 10) : null;

    // Extrai automaticamente versionCode e versionName do binário do APK
    try {
      const parser = new AppInfoParser(req.file.path);
      const parsed = await parser.parse();
      if (parsed) {
        if (!finalVersionName && parsed.versionName) finalVersionName = String(parsed.versionName);
        if (!finalVersionCode && parsed.versionCode) finalVersionCode = parseInt(parsed.versionCode, 10);
      }
    } catch (parseErr) {
      console.warn('[APK Parser Auto-detect Warning]', parseErr.message);
    }

    finalVersionName = finalVersionName || '1.0.0';
    finalVersionCode = finalVersionCode || 1;

    // Constrói URL relativa para ser resolvida dinamicamente com o domínio público configurado
    const downloadUrl = `/apks/${req.file.filename}`;
    const now = Date.now();

    await db.query(
      `INSERT INTO ota_updates (version_name, version_code, download_url, changelog, mandatory, released_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [
        finalVersionName,
        finalVersionCode,
        downloadUrl,
        changelog || '',
        mandatory ? 1 : 0,
        now
      ]
    );

    res.json({
      success: true,
      downloadUrl,
      filename: req.file.filename,
      versionName: finalVersionName,
      versionCode: finalVersionCode
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 13. Excluir versão OTA e arquivo físico se existir
router.delete('/ota/:id', requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const findRes = await db.query('SELECT * FROM ota_updates WHERE id = ?', [id]);
    if (findRes.rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Versão OTA não encontrada.' });
    }
    const update = findRes.rows[0];

    // Se o downloadUrl for um APK local dentro de /apks/, remove o arquivo físico
    if (update.download_url) {
      try {
        const urlMatch = update.download_url.match(/\/apks\/([^/?#]+)/);
        if (urlMatch && urlMatch[1]) {
          const filePath = path.join(config.apkDir, urlMatch[1]);
          if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
          }
        }
      } catch (fErr) {
        console.warn('[Delete OTA File Warning]', fErr.message);
      }
    }

    await db.query('DELETE FROM ota_updates WHERE id = ?', [id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 14. Listar solicitações de teste geradas
router.get('/tests', requireAdmin, async (req, res) => {
  try {
    const result = await db.query('SELECT * FROM test_requests ORDER BY created_at DESC LIMIT 150');
    res.json({ success: true, tests: result.rows });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 15. Excluir registro de teste
router.delete('/tests/:id', requireAdmin, async (req, res) => {
  try {
    await db.query('DELETE FROM test_requests WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
