import { db } from './db.js';
import { randomUUID } from 'node:crypto';

function runInTransaction(fn) {
  db.exec('BEGIN');
  try {
    fn();
    db.exec('COMMIT');
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}

/**
 * Serviço de sincronização e ingestão de listas Xtream Codes e M3U
 */
export class PlaylistSyncService {
  /**
   * Sincroniza uma lista Xtream Codes completa com o banco de dados
   */
  static async syncXtream(playlistId, host, username, password) {
    let cleanHost = host.trim().replace(/\/+$/, '');
    if (!cleanHost.startsWith('http://') && !cleanHost.startsWith('https://')) {
      cleanHost = 'http://' + cleanHost;
    }

    const baseApi = `${cleanHost}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
    console.log(`[Sync] Conectando ao Xtream em ${cleanHost} (usuário: ${username})...`);

    // 1. Testa autenticação
    const authRes = await fetch(baseApi);
    if (!authRes.ok) {
      throw new Error(`Falha ao conectar ao servidor Xtream (HTTP ${authRes.status})`);
    }
    const authData = await authRes.json();
    if (!authData.user_info || authData.user_info.auth === 0 || authData.user_info.status === 'Disabled') {
      throw new Error(`Usuário ou senha inválidos no servidor Xtream`);
    }

    const now = Date.now();

    // 2. Busca categorias de Live, VOD e Séries em paralelo
    console.log('[Sync] Baixando categorias...');
    const [liveCatsRes, vodCatsRes, seriesCatsRes] = await Promise.all([
      fetch(`${baseApi}&action=get_live_categories`).then(r => r.json()).catch(() => []),
      fetch(`${baseApi}&action=get_vod_categories`).then(r => r.json()).catch(() => []),
      fetch(`${baseApi}&action=get_series_categories`).then(r => r.json()).catch(() => [])
    ]);

    const insertCat = db.prepare(`
      INSERT OR REPLACE INTO categories (id, playlist_id, category_id, name, type, sort_order, is_hidden, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    runInTransaction(() => {
      // Insere categorias Live
      if (Array.isArray(liveCatsRes)) {
        liveCatsRes.forEach((c, idx) => {
          const catName = c.category_name || c.name || 'Sem Categoria';
          let order = idx + 10;
          const upper = catName.toUpperCase();
          if (upper.includes('JOGO') || upper.includes('FUTEBOL AO VIVO')) order = 1;
          if (upper.includes('ABERTO') || upper.includes('GLOBO') || upper.includes('SBT')) order = 2;
          if (upper.includes('ESPORTE') || upper.includes('SPORT')) order = 3;
          if (upper.includes('XXX') || upper.includes('ADULTO')) order = 9999;

          insertCat.run(`cat-live-${c.category_id}`, playlistId, String(c.category_id), catName, 'live', order, 0, now);
        });
      }

      // Insere categorias VOD
      if (Array.isArray(vodCatsRes)) {
        vodCatsRes.forEach((c, idx) => {
          const catName = c.category_name || c.name || 'Filmes';
          insertCat.run(`cat-vod-${c.category_id}`, playlistId, String(c.category_id), catName, 'movie', idx + 10, 0, now);
        });
      }

      // Insere categorias Séries
      if (Array.isArray(seriesCatsRes)) {
        seriesCatsRes.forEach((c, idx) => {
          const catName = c.category_name || c.name || 'Séries';
          insertCat.run(`cat-ser-${c.category_id}`, playlistId, String(c.category_id), catName, 'series', idx + 10, 0, now);
        });
      }
    });

    // 3. Busca Canais Ao Vivo
    console.log('[Sync] Baixando canais ao vivo...');
    const liveStreamsRes = await fetch(`${baseApi}&action=get_live_streams`, {
      headers: { 'User-Agent': 'IPTVSmartersPlayer/1.6.4' },
      signal: AbortSignal.timeout(30000)
    }).then(r => r.json()).catch(() => []);
    
    const insertStream = db.prepare(`
      INSERT OR REPLACE INTO stream_items (
        id, playlist_id, category_id, stream_id, name, stream_type,
        stream_icon, epg_channel_id, direct_source, resolution, rating,
        release_date, plot, sort_order, is_active, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    if (Array.isArray(liveStreamsRes)) {
      runInTransaction(() => {
        liveStreamsRes.forEach((s, idx) => {
          const streamId = String(s.stream_id);
          const name = s.name || `Canal ${streamId}`;
          const streamUrl = `${cleanHost}/live/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.ts`;

          let res = 'SD';
          const upper = name.toUpperCase();
          if (upper.includes('4K') || upper.includes('UHD')) res = '4K';
          else if (upper.includes('FHD') || upper.includes('1080')) res = 'FHD';
          else if (upper.includes('HD') || upper.includes('720')) res = 'HD';

          insertStream.run(
            `stream-${streamId}`,
            playlistId,
            String(s.category_id || '0'),
            streamId,
            name,
            'live',
            s.stream_icon || '',
            s.epg_channel_id || '',
            streamUrl,
            res,
            0.0,
            '',
            '',
            idx,
            1,
            now
          );
        });
      });
    }

    // 4. Busca Filmes VOD (Limitado para agilidade)
    console.log('[Sync] Baixando catálogo VOD...');
    const vodStreamsRes = await fetch(`${baseApi}&action=get_vod_streams`, {
      headers: { 'User-Agent': 'IPTVSmartersPlayer/1.6.4' },
      signal: AbortSignal.timeout(20000)
    }).then(r => r.json()).catch(() => []);

    if (Array.isArray(vodStreamsRes)) {
      runInTransaction(() => {
        vodStreamsRes.slice(0, 500).forEach((v, idx) => {
          const streamId = String(v.stream_id);
          const ext = v.container_extension || 'mp4';
          const streamUrl = `${cleanHost}/movie/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.${ext}`;
          const rating = parseFloat(v.rating || '0') || 0.0;

          insertStream.run(
            `vod-${streamId}`,
            playlistId,
            String(v.category_id || '0'),
            streamId,
            v.name || 'Filme VOD',
            'movie',
            v.stream_icon || '',
            '',
            streamUrl,
            'HD',
            rating,
            v.release_date || '',
            v.plot || '',
            idx,
            1,
            now
          );
        });
      });
    }

    // Atualiza status da lista no banco
    db.prepare(`
      UPDATE client_playlists 
      SET status = 'online', last_synced_at = ? 
      WHERE id = ?
    `).run(now, playlistId);

    console.log(`[Sync] Sincronização concluída! Canais: ${liveStreamsRes.length}, Filmes: ${Math.min(vodStreamsRes.length, 500)}`);

    return {
      success: true,
      channelsCount: liveStreamsRes.length,
      moviesCount: Math.min(vodStreamsRes.length, 500),
      userInfo: authData.user_info
    };
  }

  /**
   * Sincroniza lista M3U (parse de arquivo de texto)
   */
  static async syncM3U(playlistId, m3uContentOrUrl) {
    let content = m3uContentOrUrl;
    if (m3uContentOrUrl.startsWith('http://') || m3uContentOrUrl.startsWith('https://')) {
      console.log(`[Sync M3U] Baixando lista M3U de: ${m3uContentOrUrl}...`);
      const res = await fetch(m3uContentOrUrl, {
        headers: { 'User-Agent': 'IPTVSmartersPlayer/1.6.4 (SmartTV; webOS/Tizen)' },
        signal: AbortSignal.timeout(45000)
      });
      if (!res.ok) throw new Error(`Falha ao baixar M3U (HTTP ${res.status})`);
      content = await res.text();
    }

    const lines = content.split(/\r?\n/);
    const now = Date.now();

    const insertCat = db.prepare(`
      INSERT OR REPLACE INTO categories (id, playlist_id, category_id, name, type, sort_order, is_hidden, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertStream = db.prepare(`
      INSERT OR REPLACE INTO stream_items (
        id, playlist_id, category_id, stream_id, name, stream_type,
        stream_icon, epg_channel_id, direct_source, resolution, rating,
        release_date, plot, sort_order, is_active, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    let currentItem = null;
    let channelIdx = 0;
    const catMap = new Map();

    runInTransaction(() => {
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.startsWith('#EXTINF:')) {
          const groupMatch = line.match(/group-title="([^"]+)"/i);
          const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
          const epgMatch = line.match(/tvg-id="([^"]+)"/i);
          const nameParts = line.split(',');
          const name = nameParts.length > 1 ? nameParts.slice(1).join(',').trim() : 'Canal';

          const groupName = groupMatch ? groupMatch[1].trim() : 'Geral';
          let catId = catMap.get(groupName);
          if (!catId) {
            catId = `cat-m3u-${catMap.size + 1}`;
            catMap.set(groupName, catId);
            insertCat.run(catId, playlistId, catId, groupName, 'live', catMap.size, 0, now);
          }

          currentItem = {
            name,
            logo: logoMatch ? logoMatch[1] : '',
            epgId: epgMatch ? epgMatch[1] : '',
            catId
          };
        } else if (line.startsWith('http://') || line.startsWith('https://')) {
          if (currentItem) {
            channelIdx++;
            let res = 'SD';
            const upper = currentItem.name.toUpperCase();
            if (upper.includes('4K') || upper.includes('UHD')) res = '4K';
            else if (upper.includes('FHD') || upper.includes('1080')) res = 'FHD';
            else if (upper.includes('HD') || upper.includes('720')) res = 'HD';

            insertStream.run(
              `m3u-str-${channelIdx}`,
              playlistId,
              currentItem.catId,
              String(channelIdx),
              currentItem.name,
              'live',
              currentItem.logo,
              currentItem.epgId,
              line,
              res,
              0,
              '',
              '',
              channelIdx,
              1,
              now
            );
            currentItem = null;
          }
        }
      }
    });

    db.prepare(`
      UPDATE client_playlists 
      SET status = 'online', last_synced_at = ? 
      WHERE id = ?
    `).run(now, playlistId);

    console.log(`[Sync M3U] Ingestão concluída com sucesso: ${channelIdx} canais.`);
    return { success: true, channelsCount: channelIdx };
  }
}
