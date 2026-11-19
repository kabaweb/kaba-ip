const crypto = require('crypto');
const db = require('../db');
const { getSettings, saveSettings } = require('./automationService');

/**
 * Serviço de Verificação Automática da Lista Base
 * Executa periodicamente (por padrão às 01:00 no fuso de Brasília UTC-3)
 * para detectar se o provedor IPTV atualizou conteúdos ou canais.
 */

async function checkBasePlaylist(force = false) {
  const settings = await getSettings();
  const playlistUrl = (settings.base_playlist_url || '').trim();

  if (!playlistUrl) {
    return {
      success: false,
      error: 'URL da Lista Base não configurada no portal.'
    };
  }

  const now = new Date();
  const nowIso = now.toISOString();
  const brTimeStr = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    dateStyle: 'short',
    timeStyle: 'medium'
  }).format(now);

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000); // 20s timeout

    let changed = false;
    let newIdentifier = '';
    let details = '';

    // 1. Tenta HEAD request primeiro para ser o mais leve possível
    let headOk = false;
    try {
      const headRes = await fetch(playlistUrl, {
        method: 'HEAD',
        signal: controller.signal,
        headers: { 'User-Agent': 'KorpTV-Portal-Checker/1.0' }
      });

      if (headRes.ok) {
        headOk = true;
        const etag = headRes.headers.get('etag') || '';
        const lastMod = headRes.headers.get('last-modified') || '';
        const len = headRes.headers.get('content-length') || '';

        if (etag || lastMod || len) {
          newIdentifier = `etag:${etag}|mod:${lastMod}|len:${len}`;
          details = `Header HEAD detectado (Tamanho: ${len || 'N/A'}, Modificado: ${lastMod || etag || 'Sim'})`;
        }
      }
    } catch (_) {
      // Servidor pode recusar HEAD, fallback para GET
    }

    // 2. Se HEAD não forneceu dados conclusivos, faz GET amostral / checksum
    if (!newIdentifier) {
      const getRes = await fetch(playlistUrl, {
        method: 'GET',
        signal: controller.signal,
        headers: {
          'User-Agent': 'KorpTV-Portal-Checker/1.0',
          'Range': 'bytes=0-1048576' // Amostra de 1MB para comparar assinatura
        }
      });

      if (!getRes.ok) {
        clearTimeout(timeout);
        const errMsg = `Falha ao conectar no provedor. Status HTTP: ${getRes.status}`;
        await saveSettings({
          base_playlist_last_check: `${brTimeStr} (Erro: ${errMsg})`,
          base_playlist_last_status: 'error'
        });
        return { success: false, error: errMsg };
      }

      const etag = getRes.headers.get('etag') || '';
      const lastMod = getRes.headers.get('last-modified') || '';
      const content = await getRes.arrayBuffer();
      const hash = crypto.createHash('md5').update(Buffer.from(content)).digest('hex');

      newIdentifier = `hash:${hash}|etag:${etag}|mod:${lastMod}|size:${content.byteLength}`;
      details = `Amostra de dados analisada (Hash MD5: ${hash.substring(0, 8)}...)`;
    }

    clearTimeout(timeout);

    const oldIdentifier = settings.base_playlist_etag || '';

    if (force || !oldIdentifier || oldIdentifier !== newIdentifier) {
      changed = true;
    }

    const timestampNow = Date.now();
    let updatedDevicesCount = 0;

    if (changed) {
      // Se a lista mudou e auto_sync está ativo, notifica os dispositivos via updatedAt
      if (settings.base_playlist_auto_sync !== false) {
        // Atualiza a data da playlist para que o app sincronize silenciosamente
        const resUpdate = await db.query(
          `UPDATE devices 
           SET playlist_updated_at = ? 
           WHERE (playlist_url = ? OR playlist_url = '' OR playlist_url IS NULL)
             AND status != 'blocked'`,
          [timestampNow, playlistUrl]
        );
        updatedDevicesCount = resUpdate.changes || 0;
      }
    }

    const statusMsg = changed
      ? `Atualização detectada em ${brTimeStr}! (${details}). ${updatedDevicesCount > 0 ? `${updatedDevicesCount} dispositivos notificados.` : ''}`
      : `Verificado em ${brTimeStr}: Nenhuma alteração no provedor. (${details})`;

    await saveSettings({
      base_playlist_etag: newIdentifier,
      base_playlist_last_check: brTimeStr,
      base_playlist_last_status: changed ? 'updated' : 'unchanged',
      base_playlist_last_msg: statusMsg
    });

    return {
      success: true,
      changed,
      timestamp: timestampNow,
      checkTime: brTimeStr,
      details,
      updatedDevicesCount,
      message: statusMsg
    };
  } catch (err) {
    const errorMsg = `Erro na verificação: ${err.message || 'Falha de conexão com a lista base'}`;
    await saveSettings({
      base_playlist_last_check: `${brTimeStr} (Falha)`,
      base_playlist_last_status: 'error',
      base_playlist_last_msg: errorMsg
    });
    return {
      success: false,
      error: errorMsg
    };
  }
}

/**
 * Verifica se a hora atual no fuso horário America/Sao_Paulo (UTC-3)
 * corresponde à hora agendada (ex: 01:00 da manhã).
 */
function isScheduledHour(targetHour = 1) {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    hour: 'numeric',
    minute: 'numeric',
    hour12: false
  });
  const parts = formatter.formatToParts(now);
  const hour = parseInt(parts.find(p => p.type === 'hour')?.value || '0', 10);
  const minute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);

  return hour === targetHour && minute === 0;
}

module.exports = {
  checkBasePlaylist,
  isScheduledHour
};
