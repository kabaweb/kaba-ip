import { db, initDatabase } from './db.js';
import { randomUUID } from 'node:crypto';

initDatabase();

console.log('[Seed] Populando dados iniciais no banco de dados...');

const clientId = 'client-demo-1';
const playlistId = 'playlist-demo-1';
const now = Date.now();
const oneYearLater = now + 365 * 24 * 60 * 60 * 1000;

// 1. Cliente Padrão
const insertClient = db.prepare(`
  INSERT OR REPLACE INTO clients (id, name, username, password_hash, whatsapp, max_connections, status, expires_at, created_at, updated_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
insertClient.run(clientId, 'Cliente Demonstração TV', 'demo', 'hash_demo_123', '5511999999999', 3, 'active', oneYearLater, now, now);

// 2. Playlist Xtream Principal
const insertPlaylist = db.prepare(`
  INSERT OR REPLACE INTO client_playlists (id, client_id, name, type, server_url, username, password, backup_urls, status, last_synced_at, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
insertPlaylist.run(playlistId, clientId, 'Lista Xtream Premium', 'xtream', 'http://stream.korptv.local:8080', 'demo_user', 'demo_pass', '["http://backup1.korptv.local:8080","http://backup2.korptv.local:8080"]', 'online', now, now);

// 3. Configurações do Cliente
const insertSettings = db.prepare(`
  INSERT OR REPLACE INTO user_settings (client_id, default_resolution, auto_fallback, preferred_audio, subtitles_enabled, adult_content_locked, theme, updated_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
`);
insertSettings.run(clientId, 'sd', 1, 'por', 0, 1, 'dark', now);

// 4. Categorias (Na ordem oficial Xtream: Jogos primeiro, Adultos por último)
const categories = [
  { id: 'cat-jogos', catId: '1', name: '⚽ JOGOS DO DIA', type: 'live', order: 1 },
  { id: 'cat-abertos', catId: '2', name: '📺 CANAIS ABERTOS', type: 'live', order: 2 },
  { id: 'cat-esportes', catId: '3', name: '🏆 ESPORTES', type: 'live', order: 3 },
  { id: 'cat-filmes-tv', catId: '4', name: '🎬 FILMES E SÉRIES', type: 'live', order: 4 },
  { id: 'cat-noticias', catId: '5', name: '📰 NOTÍCIAS', type: 'live', order: 5 },
  { id: 'cat-infantil', catId: '6', name: '🎨 INFANTIL', type: 'live', order: 6 },
  { id: 'cat-docs', catId: '7', name: '🌍 DOCUMENTÁRIOS', type: 'live', order: 7 },
  { id: 'cat-adultos', catId: '8', name: '🔒 [XXX] ADULTOS', type: 'live', order: 999 },
  { id: 'cat-vod-lancamentos', catId: '10', name: '🔥 Lançamentos 2024 / 2025', type: 'movie', order: 1 },
  { id: 'cat-vod-acao', catId: '11', name: '💥 Ação e Aventura', type: 'movie', order: 2 },
  { id: 'cat-vod-ficcao', catId: '12', name: '🚀 Ficção Científica', type: 'movie', order: 3 },
  { id: 'cat-series-populares', catId: '20', name: '📺 Séries Populares', type: 'series', order: 1 }
];

const insertCategory = db.prepare(`
  INSERT OR REPLACE INTO categories (id, playlist_id, category_id, name, type, sort_order, is_hidden, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
`);
categories.forEach(c => insertCategory.run(c.id, playlistId, c.catId, c.name, c.type, c.order, 0, now));

// 5. Streams (Canais Ao Vivo, Filmes e Séries)
const streams = [
  // Canais Ao Vivo
  { id: 'str-1', catId: '1', streamId: '101', name: 'Premiere Clubes SD', type: 'live', icon: 'P1', res: 'SD', rating: 0, rel: '', plot: 'Brasileirão Série A: Flamengo x Palmeiras (Ao Vivo)' },
  { id: 'str-2', catId: '1', streamId: '102', name: 'SporTV SD', type: 'live', icon: 'STV', res: 'SD', rating: 0, rel: '', plot: 'Tá na Área - Pré Jogo' },
  { id: 'str-3', catId: '1', streamId: '103', name: 'ESPN Brasil SD', type: 'live', icon: 'ESPN', res: 'SD', rating: 0, rel: '', plot: 'Premier League: Arsenal x Chelsea' },
  { id: 'str-4', catId: '1', streamId: '104', name: 'CazéTV HD', type: 'live', icon: 'CZ', res: 'HD', rating: 0, rel: '', plot: 'Transmissão Especial Esportiva' },
  { id: 'str-5', catId: '1', streamId: '105', name: 'TNT Sports SD', type: 'live', icon: 'TNT', res: 'SD', rating: 0, rel: '', plot: 'Champions League Highlights' },
  // Filmes VOD
  { id: 'mov-1', catId: '10', streamId: '201', name: 'Duna: Parte 2', type: 'movie', icon: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=600', res: '4K', rating: 8.6, rel: '2024', plot: 'Paul Atreides se une a Chani e aos Fremen em busca de vingança contra os conspiradores que destruíram sua família.' },
  { id: 'mov-2', catId: '10', streamId: '202', name: 'Oppenheimer', type: 'movie', icon: 'https://images.unsplash.com/photo-1440404653325-ab127d49abc1?q=80&w=600', res: '4K', rating: 8.9, rel: '2023', plot: 'A história de J. Robert Oppenheimer e o Projeto Manhattan durante a Segunda Guerra Mundial.' },
  { id: 'mov-3', catId: '10', streamId: '203', name: 'Interestelar', type: 'movie', icon: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600', res: '4K', rating: 8.7, rel: '2014', plot: 'Um grupo de exploradores espaciais viaja através de um buraco de minhoca em busca da sobrevivência da Terra.' },
  { id: 'mov-4', catId: '10', streamId: '204', name: 'Gladiador II', type: 'movie', icon: 'https://images.unsplash.com/photo-1461360370896-922624d12aa1?q=80&w=600', res: '4K', rating: 7.7, rel: '2024', plot: 'Anos após presenciar a morte de Máximus, Lucius entra no Coliseu para lutar pela liberdade.' },
  // Séries
  { id: 'ser-1', catId: '20', streamId: '301', name: 'The Last of Us', type: 'series', icon: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=600', res: '4K', rating: 8.8, rel: '2023', plot: 'Joel é contratado para contrabandear a jovem Ellie para fora de uma zona opressiva de quarentena.' },
  { id: 'ser-2', catId: '20', streamId: '302', name: 'Stranger Things', type: 'series', icon: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=600', res: '4K', rating: 8.7, rel: '2022', plot: 'Um grupo de amigos investiga eventos sobrenaturais e segredos do governo na cidade de Hawkins.' }
];

const insertStream = db.prepare(`
  INSERT OR REPLACE INTO stream_items (id, playlist_id, category_id, stream_id, name, stream_type, stream_icon, epg_channel_id, direct_source, resolution, rating, release_date, plot, sort_order, is_active, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
`);
streams.forEach((s, idx) => {
  insertStream.run(s.id, playlistId, s.catId, s.streamId, s.name, s.type, s.icon, `epg-${s.streamId}`, 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', s.res, s.rating, s.rel, s.plot, idx, 1, now);
});

// 6. Guia EPG de Demonstração
const insertEpg = db.prepare(`
  INSERT OR REPLACE INTO epg_programmes (id, channel_id, title, description, start_time, end_time, category, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
`);
insertEpg.run('epg-p1-1', 'epg-101', 'Brasileirão: Flamengo x Palmeiras (Ao Vivo)', 'Transmissão completa em tempo real direto do Maracanã com som surround.', now - 30 * 60 * 1000, now + 90 * 60 * 1000, 'Futebol', now);
insertEpg.run('epg-p1-2', 'epg-101', 'Linha de Passe: Pós-Jogo e Análises', 'Debate completo dos lances e entrevistas exclusivas.', now + 90 * 60 * 1000, now + 150 * 60 * 1000, 'Debate', now);

console.log('[Seed] Concluído! Banco de dados populado com clientes, categorias, canais, VOD e EPG.');
