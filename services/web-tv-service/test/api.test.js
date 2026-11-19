import test from 'node:test';
import assert from 'node:assert/strict';
import { db } from '../src/db.js';

test('Banco de Dados: deve conter tabelas essenciais', () => {
  const tables = db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(t => t.name);
  assert.ok(tables.includes('clients'), 'Tabela clients existe');
  assert.ok(tables.includes('client_playlists'), 'Tabela client_playlists existe');
  assert.ok(tables.includes('categories'), 'Tabela categories existe');
  assert.ok(tables.includes('stream_items'), 'Tabela stream_items existe');
  assert.ok(tables.includes('connected_devices'), 'Tabela connected_devices existe');
});

test('API Health: endpoint responde status online', async () => {
  const res = await fetch('http://localhost:3333/health');
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.status, 'online');
  assert.equal(data.service, 'korp-web-tv-microservice');
});

test('Playlist Sync M3U: processa e armazena lista m3u', async () => {
  const m3uSample = `#EXTM3U
#EXTINF:-1 tvg-id="globo" tvg-logo="https://example.com/globo.png" group-title="CANAIS ABERTOS", TV Globo SD
http://stream.example.com/globo.ts
#EXTINF:-1 tvg-id="sportv" tvg-logo="https://example.com/sportv.png" group-title="ESPORTES", SporTV HD
http://stream.example.com/sportv.m3u8`;

  const res = await fetch('http://localhost:3333/api/playlist/sync', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'm3u', m3u_url: m3uSample })
  });
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.equal(data.channelsCount, 2);
});

test('API Categorias: retorna categorias cadastradas', async () => {
  const res = await fetch('http://localhost:3333/api/categories?type=live');
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.ok(data.count > 0);
  assert.equal(data.categories[0].type, 'live');
});

test('API Streams: filtra canais por tipo live', async () => {
  const res = await fetch('http://localhost:3333/api/streams?type=live');
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.ok(data.streams.length > 0);
  assert.equal(data.streams[0].stream_type, 'live');
});

test('API Auth Pair: gera código de 6 dígitos para Smart TV', async () => {
  const res = await fetch('http://localhost:3333/api/auth/pair', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ device_type: 'lg_webos', device_name: 'LG OLED C3' })
  });
  assert.equal(res.status, 201);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.match(data.pair_code, /^\d{6}$/);
});

test('API Device Status: retorna canais e playlist ativa', async () => {
  const res = await fetch('http://localhost:3333/api/device/status');
  assert.equal(res.status, 200);
  const data = await res.json();
  assert.equal(data.success, true);
  assert.ok(data.channels > 0);
  assert.equal(data.isReady, true);
});


