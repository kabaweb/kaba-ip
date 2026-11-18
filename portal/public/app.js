let devices = [];
let selectedDeviceId = null;
let isBroadcast = false;
let currentPlaylistType = 'xtream';
let systemSettings = {};
let currentUser = null;
let operatorsList = [];

function getPublicBaseUrl() {
  if (systemSettings && systemSettings.portal_public_domain && systemSettings.portal_public_domain.trim()) {
    return systemSettings.portal_public_domain.trim().replace(/\/+$/, '');
  }
  return window.location.origin;
}

function getAdminToken() {
  return localStorage.getItem('ipvio_admin_token') || '';
}

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'x-admin-token': getAdminToken()
  };
}

// 1. Autenticação e Login
async function handleLogin(e) {
  e.preventDefault();
  const username = document.getElementById('login-user').value.trim();
  const password = document.getElementById('login-pass').value.trim();
  const btn = document.getElementById('login-btn');
  const errBox = document.getElementById('login-error');

  errBox.classList.add('hidden');
  btn.disabled = true;
  btn.innerText = 'Verificando...';

  try {
    const res = await fetch('/api/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Usuário ou senha incorretos.');
    }

    localStorage.setItem('ipvio_admin_token', data.token);
    localStorage.setItem('ipvio_admin_username', data.username || data.user?.username || username);
    if (data.user) {
      currentUser = data.user;
      localStorage.setItem('ipvio_user_info', JSON.stringify(data.user));
    }

    await showApp();
    loadDevices();
    if (currentUser?.role === 'admin') {
      loadOtaUpdates();
    }
  } catch (err) {
    errBox.innerText = err.message;
    errBox.classList.remove('hidden');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<span>Acessar Painel</span><i class="bi bi-arrow-right"></i>`;
  }
}

function handleLogout() {
  localStorage.removeItem('ipvio_admin_token');
  localStorage.removeItem('ipvio_admin_username');
  localStorage.removeItem('ipvio_user_info');
  currentUser = null;
  showLogin();
}

function showLogin() {
  document.getElementById('login-screen').classList.remove('hidden');
  document.getElementById('app-container').classList.add('hidden');
}

async function showApp() {
  document.getElementById('login-screen').classList.add('hidden');
  document.getElementById('app-container').classList.remove('hidden');

  try {
    const raw = localStorage.getItem('ipvio_user_info');
    if (raw) currentUser = JSON.parse(raw);
  } catch (_) {}

  try {
    const meRes = await fetch('/api/admin/me', { headers: authHeaders() });
    if (meRes.ok) {
      const meData = await meRes.json();
      if (meData.user) {
        currentUser = meData.user;
        localStorage.setItem('ipvio_user_info', JSON.stringify(currentUser));
      }
    }
  } catch (_) {}

  currentUser = currentUser || { username: 'admin', role: 'admin' };
  applyUserRole();
  await loadOperators();

  if (currentUser.role === 'admin') {
    loadSettings();
    loadUsers();
  }
}

function applyUserRole() {
  const user = currentUser || { username: 'admin', role: 'admin' };
  const userDisplay = document.getElementById('user-display');
  const roleLabel = user.role === 'admin' ? 'Administrador' : 'Operador';
  const roleBadge = user.role === 'admin' ? 'badge bg-success-subtle text-success border border-success-subtle' : 'badge bg-info-subtle text-info border border-info-subtle';

  if (userDisplay) {
    userDisplay.innerHTML = `<div class="fw-bold text-white small">${escapeHtml(user.name || user.username)}</div><span class="${roleBadge} rounded-pill" style="font-size: 0.65rem;">${roleLabel}</span>`;
  }

  const tstBtn = document.getElementById('tab-tests-btn');
  const autBtn = document.getElementById('tab-automations-btn');
  const otaBtn = document.getElementById('tab-ota-btn');

  if (user.role !== 'admin') {
    if (tstBtn) tstBtn.classList.add('hidden');
    if (autBtn) autBtn.classList.add('hidden');
    if (otaBtn) otaBtn.classList.add('hidden');
  } else {
    if (tstBtn) tstBtn.classList.remove('hidden');
    if (autBtn) autBtn.classList.remove('hidden');
    if (otaBtn) otaBtn.classList.remove('hidden');
  }
}

// Alternar abas
function switchTab(tab) {
  const tabs = ['devices', 'tests', 'automations', 'ota'];
  tabs.forEach(t => {
    const btn = document.getElementById(`tab-${t}-btn`);
    const sec = document.getElementById(`section-${t}`);
    if (btn) btn.classList.toggle('active', t === tab);
    if (sec) sec.classList.toggle('hidden', t !== tab);
  });

  if (tab === 'devices') {
    loadDevices();
  } else if (tab === 'tests') {
    loadTests();
  } else if (tab === 'automations') {
    loadSettings();
  } else if (tab === 'ota') {
    loadOtaUpdates();
  }
}

// Formatar tempo relativo
function formatTimeAgo(ts) {
  if (!ts) return 'Nunca';
  const diffSec = Math.floor((Date.now() - Number(ts)) / 1000);
  if (diffSec < 60) return 'Agora mesmo';
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} min atrás`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} h atrás`;
  return `${Math.floor(diffSec / 86400)} d atrás`;
}

// Carregar lista de dispositivos
async function loadDevices() {
  try {
    const res = await fetch('/api/admin/devices', { headers: authHeaders() });
    if (res.status === 401) {
      showLogin();
      return;
    }
    const data = await res.json();
    devices = data.devices || [];
    renderDevices(devices);
    updateStats();
  } catch (err) {
    console.error('Erro ao carregar dispositivos:', err);
  }
}

function getEffectivePlaylistType(d) {
  if (d && d.playlist_type && d.playlist_type !== 'none') return d.playlist_type;
  if (d && (d.xtream_user || d.xtream_pass)) return 'xtream';
  if (d && d.playlist_url) return 'm3u';
  return 'none';
}

function updateStats() {
  const total = devices.length;
  const online = devices.filter(d => d.isOnline).length;
  const withPlaylist = devices.filter(d => getEffectivePlaylistType(d) !== 'none').length;

  document.getElementById('stat-total').innerText = total;
  document.getElementById('stat-online').innerText = online;
  document.getElementById('stat-playlists').innerText = withPlaylist;
}

function getPairingCode(d) {
  if (d.pairing_code) return d.pairing_code;
  const src = (d.mac || '').replace(/[^a-zA-Z0-9]/g, '') || (d.id || '').replace(/[^a-zA-Z0-9]/g, '');
  const hex = src.slice(-6).toUpperCase();
  return hex.length === 6 ? `${hex.slice(0, 3)}-${hex.slice(3)}` : (hex || '-');
}

// Carregar lista de operadores
async function loadOperators() {
  try {
    const res = await fetch('/api/admin/operators', { headers: authHeaders() });
    if (!res.ok) return;
    const data = await res.json();
    operatorsList = data.operators || [];

    const selects = ['edit-device-owner', 'playlist-owner-id', 'link-owner-id'];
    selects.forEach(id => {
      const el = document.getElementById(id);
      if (!el) return;
      const currentVal = el.value;
      let html = '<option value="">Livre (Sem responsável / Visível para todos)</option>';
      operatorsList.forEach(op => {
        const roleBadge = op.role === 'admin' ? ' (Admin)' : ' (Operador)';
        html += `<option value="${op.id}">${escapeHtml(op.name || op.username)}${roleBadge}</option>`;
      });
      el.innerHTML = html;
      el.value = currentVal;
    });
  } catch (err) {
    console.warn('Erro ao carregar lista de operadores:', err);
  }
}

// Renderizar tabela de dispositivos
function renderDevices(list) {
  const tbody = document.getElementById('devices-tbody');
  const mobileContainer = document.getElementById('devices-mobile-container');

  if (list.length === 0) {
    const emptyHtml = `Nenhum dispositivo registrado ainda.<br><span class="small text-body-secondary">Abra o app Korp TV no Android conectado a este portal.</span>`;
    if (tbody) tbody.innerHTML = `<tr><td colspan="11" class="text-center py-5 text-secondary">${emptyHtml}</td></tr>`;
    if (mobileContainer) {
      mobileContainer.innerHTML = `
        <div class="glass-card p-4 text-center text-secondary">
          <i class="bi bi-tv fs-2 mb-2 d-block text-secondary opacity-50"></i>
          ${emptyHtml}
        </div>
      `;
    }
    return;
  }

  // 1. Renderização Desktop (Tabela)
  if (tbody) {
    tbody.innerHTML = list.map(d => {
      const isOnline = d.isOnline;
      const isBlocked = d.status === 'blocked';
      const pairingCode = getPairingCode(d);

      let statusBadge = '';
      if (isBlocked) {
        statusBadge = `<span class="badge rounded-pill bg-danger-subtle text-danger border border-danger-subtle d-inline-flex align-items-center gap-1.5 px-2.5 py-1"><span class="pulse-dot bg-danger"></span> Bloqueado</span>`;
      } else if (isOnline) {
        statusBadge = `<span class="badge rounded-pill bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1.5 px-2.5 py-1"><span class="pulse-dot bg-success"></span> Online</span>`;
      } else {
        statusBadge = `<span class="badge rounded-pill bg-secondary-subtle text-secondary border border-secondary-subtle d-inline-flex align-items-center gap-1.5 px-2.5 py-1"><span class="pulse-dot bg-secondary"></span> Offline</span>`;
      }

      const ownerName = d.owner_name || d.owner_username;
      let ownerBadge = '';
      if (ownerName) {
        ownerBadge = `<span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2.5 py-1" title="Gerenciado por ${escapeHtml(ownerName)}"><i class="bi bi-person me-1"></i>${escapeHtml(ownerName)}</span>`;
      } else {
        ownerBadge = `<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill px-2 py-0.5">Livre</span>`;
      }

      const effectivePlType = getEffectivePlaylistType(d);
      let plBadge = '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill px-2 py-1">Sem Lista</span>';
      if (effectivePlType === 'xtream') {
        plBadge = `<span class="badge bg-info-subtle text-info border border-info-subtle rounded-pill px-2 py-1 font-monospace" title="${d.xtream_host || ''}">Xtream</span>`;
      } else if (effectivePlType === 'm3u') {
        plBadge = `<span class="badge bg-warning-subtle text-warning border border-warning-subtle rounded-pill px-2 py-1 font-monospace" title="${d.playlist_url || ''}">M3U</span>`;
      }

      // Assistindo Agora (Now Playing)
      let nowPlayingBadge = '<span class="small text-body-secondary font-monospace">-</span>';
      if (d.isPlaying && d.currentPlaying) {
        let typeIcon = '<i class="bi bi-broadcast text-danger"></i>';
        if (d.currentPlayingType === 'movie') typeIcon = '<i class="bi bi-film text-info"></i>';
        else if (d.currentPlayingType === 'series') typeIcon = '<i class="bi bi-collection-play text-warning"></i>';

        nowPlayingBadge = `
          <div class="d-inline-flex align-items-center gap-1.5 px-2.5 py-1 rounded-pill bg-success-subtle border border-success-subtle text-success small" style="max-width: 190px;" title="${escapeHtml(d.currentPlaying)}">
            <span>${typeIcon}</span>
            <span class="text-truncate fw-semibold">${escapeHtml(d.currentPlaying)}</span>
          </div>
        `;
      } else if (d.now_playing && !d.isPlaying) {
        nowPlayingBadge = `<span class="text-secondary small text-truncate d-inline-block" style="max-width: 150px;" title="Último reproduzido: ${escapeHtml(d.now_playing)}">Pausado (${escapeHtml(d.now_playing.slice(0, 16))}...)</span>`;
      }

      // Badge de Validade
      let expBadge = '<span class="text-secondary small">-</span>';
      if (effectivePlType !== 'none') {
        const expStr = d.playlist_exp_date || 'Não consultada';
        if (expStr.includes('/')) {
          const parts = expStr.split('/');
          const expDate = new Date(parts[2], parts[1] - 1, parts[0]);
          const diffDays = Math.ceil((expDate - new Date()) / (1000 * 60 * 60 * 24));

          if (diffDays <= 0) {
            expBadge = `<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2 py-1 font-monospace" title="Vencido"><i class="bi bi-x-circle me-1"></i>${expStr} (Vencida)</span>`;
          } else if (diffDays <= 5) {
            expBadge = `<span class="badge bg-warning-subtle text-warning border border-warning-subtle rounded-pill px-2 py-1 font-monospace" title="Vence em breve"><i class="bi bi-exclamation-triangle me-1"></i>${expStr} (${diffDays}d)</span>`;
          } else {
            expBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-1 font-monospace" title="Válida"><i class="bi bi-check-circle me-1"></i>${expStr} (${diffDays}d)</span>`;
          }
        } else if (expStr.toLowerCase().includes('ilimitado')) {
          expBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2 py-1 font-monospace"><i class="bi bi-infinity me-1"></i>Ilimitada</span>`;
        } else {
          expBadge = `<span class="badge bg-body-tertiary text-light border border-secondary-subtle rounded-pill px-2 py-1 font-monospace">${escapeHtml(expStr)}</span>`;
        }

        expBadge += `
          <button onclick="refreshValidity('${d.id}')" class="btn btn-sm btn-link text-info p-0 ms-1 align-baseline" title="Verificar validade no provedor agora">
            <i class="bi bi-arrow-repeat"></i>
          </button>
        `;
      }

      let phoneHtml = '';
      if (d.client_phone) {
        const cleanPhone = String(d.client_phone).replace(/\D/g, '');
        phoneHtml = `
          <div class="mt-0.5">
            <a href="https://wa.me/${cleanPhone}" target="_blank" class="text-decoration-none d-inline-flex align-items-center gap-1 text-success small font-monospace" title="Abrir conversa no WhatsApp">
              <i class="bi bi-whatsapp"></i> ${escapeHtml(d.client_phone)}
            </a>
          </div>
        `;
      }

      let notesHtml = '';
      if (d.notes) {
        notesHtml = `
          <div class="mt-1 text-warning small bg-warning-subtle px-2 py-0.5 rounded border border-warning-subtle text-truncate" style="max-width: 200px;" title="${escapeHtml(d.notes)}">
            <i class="bi bi-sticky me-1"></i>${escapeHtml(d.notes)}
          </div>
        `;
      }

      const displayName = d.client_name || d.name || 'Sem nome';

      return `
        <tr>
          <td>${statusBadge}</td>
          <td>
            <div class="d-inline-flex align-items-center gap-1.5 px-2.5 py-1 rounded-3 bg-info-subtle border border-info-subtle text-info font-monospace fw-bold small">
              <span style="letter-spacing: 0.05em;">${escapeHtml(pairingCode)}</span>
              <button onclick="navigator.clipboard.writeText('${pairingCode}'); alert('Código ${pairingCode} copiado!')" class="btn btn-sm btn-link text-info p-0 ms-1" title="Copiar Código">
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
          </td>
          <td>
            <div class="fw-bold text-white d-flex align-items-center gap-2">
              ${escapeHtml(displayName)}
              <button onclick="openEditDeviceModal('${d.id}')" class="btn btn-sm btn-link text-secondary p-0" title="Editar Nome, WhatsApp e Notas">
                <i class="bi bi-pencil-square"></i>
              </button>
            </div>
            ${phoneHtml}
            ${notesHtml}
            <div class="text-secondary small mt-0.5" style="font-size: 0.75rem;">${escapeHtml(d.model || 'Android TV')}</div>
          </td>
          <td class="text-nowrap">${ownerBadge}</td>
          <td>
            <div class="font-monospace small text-light d-flex align-items-center gap-1.5">
              ${escapeHtml(d.mac || d.id)}
              <button onclick="navigator.clipboard.writeText('${d.mac || d.id}'); alert('Copiado!')" class="btn btn-sm btn-link text-secondary p-0" title="Copiar">
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
            <div class="text-body-tertiary font-monospace" style="font-size: 0.7rem;">${escapeHtml(d.id.slice(0, 16))}...</div>
          </td>
          <td>
            <div class="small text-light font-monospace">${escapeHtml(d.ip || '0.0.0.0')}</div>
            <div class="text-info small fw-medium" style="font-size: 0.72rem;">v${escapeHtml(d.app_version || '1.0')}</div>
          </td>
          <td>${plBadge}</td>
          <td>${nowPlayingBadge}</td>
          <td class="text-nowrap">${expBadge}</td>
          <td class="text-secondary small">${formatTimeAgo(d.last_seen)}</td>
          <td class="text-end text-nowrap">
            <div class="d-flex align-items-center justify-content-end gap-1">
              <button onclick="openLinkCodeModal('${escapeHtml(pairingCode)}')" class="btn btn-sm btn-info text-white fw-bold px-2.5 py-1 rounded-pill" title="Vincular Lista por Código">
                ⚡ Vincular
              </button>
              <button onclick="openPlaylistModal('${d.id}')" class="btn btn-sm btn-outline-light px-2.5 py-1 rounded-pill" title="Configurar Playlist">
                Playlist
              </button>
              <button onclick="openMessageModal('${d.id}')" class="btn btn-sm btn-outline-secondary px-2.5 py-1 rounded-pill" title="Enviar Mensagem">
                Mensagem
              </button>
              <button onclick="triggerExpiryReminder('${d.id}')" class="btn btn-sm btn-outline-warning px-2.5 py-1 rounded-pill" title="Enviar Aviso de Vencimento para a TV">
                🔔 Aviso
              </button>
              <button onclick="toggleBlock('${d.id}', '${d.status}')" class="btn btn-sm ${isBlocked ? 'btn-outline-success' : 'btn-outline-danger'} px-2.5 py-1 rounded-pill" title="${isBlocked ? 'Desbloquear' : 'Bloquear'}">
                ${isBlocked ? 'Desbloquear' : 'Bloquear'}
              </button>
              <button onclick="deleteDevice('${d.id}')" class="btn btn-sm btn-link text-danger p-1" title="Excluir">
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  // 2. Renderização Mobile (Cards Touch-Friendly)
  if (mobileContainer) {
    mobileContainer.innerHTML = list.map(d => {
      const isOnline = d.isOnline;
      const isBlocked = d.status === 'blocked';
      const pairingCode = getPairingCode(d);
      const effectivePlType = getEffectivePlaylistType(d);
      const displayName = d.client_name || d.name || 'Sem nome';

      let statusBadge = '';
      if (isBlocked) {
        statusBadge = `<span class="badge rounded-pill bg-danger-subtle text-danger border border-danger-subtle d-inline-flex align-items-center gap-1 px-2 py-0.5"><span class="pulse-dot bg-danger"></span> Bloqueado</span>`;
      } else if (isOnline) {
        statusBadge = `<span class="badge rounded-pill bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1 px-2 py-0.5"><span class="pulse-dot bg-success"></span> Online</span>`;
      } else {
        statusBadge = `<span class="badge rounded-pill bg-secondary-subtle text-secondary border border-secondary-subtle d-inline-flex align-items-center gap-1 px-2 py-0.5"><span class="pulse-dot bg-secondary"></span> Offline</span>`;
      }

      const ownerName = d.owner_name || d.owner_username;
      let ownerBadge = ownerName
        ? `<span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2 py-0.5" style="font-size: 0.7rem;"><i class="bi bi-person me-0.5"></i>${escapeHtml(ownerName)}</span>`
        : `<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill px-2 py-0.5" style="font-size: 0.7rem;">Livre</span>`;

      let plBadge = '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle rounded-pill px-2 py-0.5" style="font-size: 0.7rem;">Sem Lista</span>';
      if (effectivePlType === 'xtream') {
        plBadge = `<span class="badge bg-info-subtle text-info border border-info-subtle rounded-pill px-2 py-0.5 font-monospace" style="font-size: 0.7rem;">Xtream</span>`;
      } else if (effectivePlType === 'm3u') {
        plBadge = `<span class="badge bg-warning-subtle text-warning border border-warning-subtle rounded-pill px-2 py-0.5 font-monospace" style="font-size: 0.7rem;">M3U</span>`;
      }

      let phoneBtn = '';
      if (d.client_phone) {
        const cleanPhone = String(d.client_phone).replace(/\D/g, '');
        phoneBtn = `
          <a href="https://wa.me/${cleanPhone}" target="_blank" class="btn btn-sm btn-outline-success d-inline-flex align-items-center gap-1 px-2 py-1 rounded-pill" style="font-size: 0.75rem;">
            <i class="bi bi-whatsapp"></i> ${escapeHtml(d.client_phone)}
          </a>
        `;
      }

      let nowPlayingMobile = '';
      if (d.isPlaying && d.currentPlaying) {
        nowPlayingMobile = `
          <div class="d-flex align-items-center gap-1.5 p-2 rounded-3 bg-success-subtle border border-success-subtle text-success small mt-2">
            <i class="bi bi-broadcast text-danger"></i>
            <span class="fw-semibold text-truncate">${escapeHtml(d.currentPlaying)}</span>
          </div>
        `;
      }

      let expBadgeMobile = '';
      if (effectivePlType !== 'none') {
        const expStr = d.playlist_exp_date || 'Não consultada';
        expBadgeMobile = `<span class="badge bg-body-tertiary text-light border border-secondary-subtle rounded-pill px-2 py-0.5 font-monospace" style="font-size: 0.7rem;">${escapeHtml(expStr)}</span>`;
      }

      return `
        <div class="device-card-mobile shadow-sm">
          <!-- Topo do Card Mobile -->
          <div class="d-flex align-items-center justify-content-between mb-2 pb-2 border-bottom border-secondary-subtle">
            <div class="d-flex align-items-center gap-1.5 flex-wrap">
              ${statusBadge}
              ${ownerBadge}
              ${plBadge}
            </div>
            <!-- Código de Ativação Mobile -->
            <div class="d-inline-flex align-items-center gap-1 px-2 py-0.5 rounded-3 bg-info-subtle border border-info-subtle text-info font-monospace fw-bold small">
              <span>${escapeHtml(pairingCode)}</span>
              <button onclick="navigator.clipboard.writeText('${pairingCode}'); alert('Código ${pairingCode} copiado!')" class="btn btn-sm btn-link text-info p-0 ms-1" title="Copiar">
                <i class="bi bi-clipboard"></i>
              </button>
            </div>
          </div>

          <!-- Informações Principais -->
          <div class="d-flex align-items-start justify-content-between gap-2 mb-2">
            <div>
              <div class="fw-bold text-white fs-6 d-flex align-items-center gap-1.5">
                ${escapeHtml(displayName)}
                <button onclick="openEditDeviceModal('${d.id}')" class="btn btn-sm btn-link text-secondary p-0" title="Editar">
                  <i class="bi bi-pencil-square"></i>
                </button>
              </div>
              <div class="text-secondary small" style="font-size: 0.75rem;">
                ${escapeHtml(d.model || 'Android TV')} · <span class="font-monospace text-light">${escapeHtml(d.mac || d.id)}</span>
              </div>
              <div class="text-body-tertiary font-monospace" style="font-size: 0.7rem;">
                IP: ${escapeHtml(d.ip || '0.0.0.0')} · v${escapeHtml(d.app_version || '1.0')} · ${formatTimeAgo(d.last_seen)}
              </div>
            </div>
            ${phoneBtn}
          </div>

          ${nowPlayingMobile}

          <!-- Linha de Validade / Notas -->
          <div class="d-flex align-items-center justify-content-between gap-2 mt-2 pt-2 border-top border-secondary-subtle">
            <div class="small text-secondary" style="font-size: 0.75rem;">
              Validade: ${expBadgeMobile || '<span class="text-body-tertiary">Sem lista</span>'}
            </div>
            ${d.notes ? `<div class="badge bg-warning-subtle text-warning border border-warning-subtle text-truncate" style="max-width: 140px;" title="${escapeHtml(d.notes)}">${escapeHtml(d.notes)}</div>` : ''}
          </div>

          <!-- Ações Rápidas Mobile (Botões de Toque) -->
          <div class="d-grid gap-2 mt-3" style="grid-template-columns: repeat(3, 1fr);">
            <button onclick="openLinkCodeModal('${escapeHtml(pairingCode)}')" class="btn btn-sm btn-info text-white fw-bold py-1.5 rounded-pill text-truncate">
              ⚡ Vincular
            </button>
            <button onclick="openPlaylistModal('${d.id}')" class="btn btn-sm btn-outline-light py-1.5 rounded-pill text-truncate">
              Playlist
            </button>
            <button onclick="openMessageModal('${d.id}')" class="btn btn-sm btn-outline-secondary py-1.5 rounded-pill text-truncate">
              Mensagem
            </button>
            <button onclick="triggerExpiryReminder('${d.id}')" class="btn btn-sm btn-outline-warning py-1.5 rounded-pill text-truncate">
              🔔 Aviso
            </button>
            <button onclick="toggleBlock('${d.id}', '${d.status}')" class="btn btn-sm ${isBlocked ? 'btn-outline-success' : 'btn-outline-danger'} py-1.5 rounded-pill text-truncate">
              ${isBlocked ? 'Desbloquear' : 'Bloquear'}
            </button>
            <button onclick="deleteDevice('${d.id}')" class="btn btn-sm btn-outline-danger py-1.5 rounded-pill text-truncate">
              <i class="bi bi-trash me-1"></i>Excluir
            </button>
          </div>
        </div>
      `;
    }).join('');
  }
}

// Filtro em tempo real
function filterDevices() {
  const q = document.getElementById('search-input').value.toLowerCase();
  const st = document.getElementById('status-filter').value;

  const filtered = devices.filter(d => {
    const matchQuery = !q || (d.name && d.name.toLowerCase().includes(q)) ||
      (d.mac && d.mac.toLowerCase().includes(q)) ||
      (d.id && d.id.toLowerCase().includes(q)) ||
      (d.ip && d.ip.includes(q)) ||
      (d.model && d.model.toLowerCase().includes(q));

    let matchStatus = true;
    if (st === 'online') matchStatus = d.isOnline;
    else if (st === 'offline') matchStatus = !d.isOnline && d.status !== 'blocked';
    else if (st === 'blocked') matchStatus = d.status === 'blocked';

    return matchQuery && matchStatus;
  });

  renderDevices(filtered);
}

// Reconsultar validade da lista no servidor
async function refreshValidity(id) {
  try {
    const res = await fetch(`/api/admin/devices/${id}/check-validity`, {
      method: 'POST',
      headers: authHeaders()
    });
    const data = await res.json();
    if (res.ok && data.success) {
      alert(`Validade atualizada com sucesso!\n\nStatus: ${data.status}\nData de Expiração: ${data.expDate}\nDias Restantes: ${data.daysRemaining !== null ? data.daysRemaining + ' dias' : 'Ilimitado'}`);
      loadDevices();
    } else {
      alert(`Não foi possível consultar a validade: ${data.error || 'Erro desconhecido'}`);
    }
  } catch (err) {
    alert('Erro ao consultar validade: ' + err.message);
  }
}

// Modal de Edição de Cliente (Nome, WhatsApp, Notas, Responsável e Vencimento)
let editingDeviceId = null;
function openEditDeviceModal(id) {
  editingDeviceId = id;
  const dev = devices.find(d => d.id === id);
  document.getElementById('edit-device-subtitle').innerText = `Aparelho: ${dev?.model || id} (${dev?.mac || ''})`;
  document.getElementById('edit-device-name').value = dev?.client_name || dev?.name || '';
  document.getElementById('edit-device-phone').value = dev?.client_phone || '';
  document.getElementById('edit-device-notes').value = dev?.notes || '';

  const ownerSelect = document.getElementById('edit-device-owner');
  if (ownerSelect) {
    ownerSelect.value = dev?.owner_id !== null && dev?.owner_id !== undefined ? String(dev.owner_id) : '';
  }

  const expInput = document.getElementById('edit-device-exp');
  if (expInput) {
    expInput.value = dev?.playlist_exp_date || '';
  }

  document.getElementById('edit-device-modal').classList.remove('hidden');
}

async function saveDeviceEdit() {
  if (!editingDeviceId) return;
  const name = document.getElementById('edit-device-name').value.trim();
  const phone = document.getElementById('edit-device-phone').value.trim();
  const notes = document.getElementById('edit-device-notes').value.trim();
  const ownerId = document.getElementById('edit-device-owner')?.value;
  const expDate = document.getElementById('edit-device-exp')?.value.trim();

  await fetch(`/api/admin/devices/${editingDeviceId}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({
      name,
      clientName: name,
      clientPhone: phone,
      notes,
      ownerId: ownerId !== undefined ? ownerId : undefined,
      expDate: expDate || undefined
    })
  });

  closeModal('edit-device-modal');
  loadDevices();
}

// Lembrete manual de vencimento para a tela do cliente
async function triggerExpiryReminder(id) {
  const dev = devices.find(d => d.id === id);
  const clientName = dev?.client_name || dev?.name || 'Cliente';
  if (!confirm(`Deseja enviar um alerta de vencimento para a tela da TV de "${clientName}"?`)) return;

  try {
    const res = await fetch(`/api/admin/devices/${id}/expiry-reminder`, {
      method: 'POST',
      headers: authHeaders()
    });
    const data = await res.json();
    if (res.ok && data.success) {
      let msg = `✅ Lembrete de vencimento enviado para a TV!\n\nMensagem na tela:\n"${data.message}"`;
      if (data.sentWa) {
        msg += `\n\n✓ Enviado também via WhatsApp!`;
      }
      alert(msg);
      loadDevices();
    } else {
      alert(`Erro ao enviar aviso: ${data.error || 'Falha na requisição'}`);
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

// Renomear Dispositivo (compatibilidade)
async function renameDevice(id, currentName) {
  openEditDeviceModal(id);
}

// Bloquear / Desbloquear Dispositivo
async function toggleBlock(id, currentStatus) {
  const next = currentStatus === 'blocked' ? 'active' : 'blocked';
  if (!confirm(`Deseja realmente ${next === 'blocked' ? 'BLOQUEAR' : 'DESBLOQUEAR'} este aparelho?`)) return;

  await fetch(`/api/admin/devices/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ status: next })
  });
  loadDevices();
}

// Excluir Dispositivo
async function deleteDevice(id) {
  if (!confirm('Excluir este dispositivo do portal? Ele será reinserido se conectar novamente.')) return;
  await fetch(`/api/admin/devices/${id}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  loadDevices();
}

// Modal de Playlist
function openPlaylistModal(id) {
  selectedDeviceId = id;
  const dev = devices.find(d => d.id === id);
  document.getElementById('modal-device-subtitle').innerText = `Dispositivo: ${dev?.name || id} (${dev?.mac || ''})`;

  currentPlaylistType = (dev?.playlist_type && dev.playlist_type !== 'none')
    ? dev.playlist_type
    : (dev?.xtream_user || dev?.xtream_pass ? 'xtream' : (dev?.playlist_url ? 'm3u' : 'xtream'));
  setPlaylistType(currentPlaylistType);

  const defaultHost = systemSettings.default_iptv_url || '';
  document.getElementById('xtream-host').value = dev?.xtream_host || defaultHost;
  document.getElementById('xtream-user').value = dev?.xtream_user || '';
  document.getElementById('xtream-pass').value = dev?.xtream_pass || '';
  document.getElementById('m3u-url').value = dev?.playlist_url || '';
  if (document.getElementById('playlist-client-name')) {
    document.getElementById('playlist-client-name').value = dev?.client_name || dev?.name || '';
  }
  if (document.getElementById('playlist-client-phone')) {
    document.getElementById('playlist-client-phone').value = dev?.client_phone || '';
  }
  if (document.getElementById('playlist-client-notes')) {
    document.getElementById('playlist-client-notes').value = dev?.notes || '';
  }
  if (document.getElementById('playlist-owner-id')) {
    document.getElementById('playlist-owner-id').value = dev?.owner_id !== null && dev?.owner_id !== undefined ? String(dev.owner_id) : '';
  }
  if (document.getElementById('playlist-exp-manual')) {
    document.getElementById('playlist-exp-manual').value = dev?.playlist_exp_date || '';
  }

  document.getElementById('playlist-modal').classList.remove('hidden');
}

function setPlaylistType(type) {
  currentPlaylistType = type;
  const btnX = document.getElementById('btn-type-xtream');
  const btnM = document.getElementById('btn-type-m3u');
  const btnN = document.getElementById('btn-type-none');
  const fX = document.getElementById('fields-xtream');
  const fM = document.getElementById('fields-m3u');

  btnX.className = type === 'xtream' ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary';
  btnM.className = type === 'm3u' ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary';
  btnN.className = type === 'none' ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-outline-secondary';

  fX.classList.toggle('hidden', type !== 'xtream');
  fM.classList.toggle('hidden', type !== 'm3u');
}

async function savePlaylist() {
  if (!selectedDeviceId) return;

  const defaultHost = systemSettings.default_iptv_url || '';
  const inputHost = document.getElementById('xtream-host').value.trim();
  const effectiveHost = inputHost || (currentPlaylistType === 'xtream' ? defaultHost : '');

  const inputUser = document.getElementById('xtream-user').value.trim();
  const inputPass = document.getElementById('xtream-pass').value.trim();
  const inputM3u = document.getElementById('m3u-url').value.trim();

  let resolvedType = currentPlaylistType;
  if ((!resolvedType || resolvedType === 'none') && (inputUser || inputPass)) resolvedType = 'xtream';
  else if ((!resolvedType || resolvedType === 'none') && inputM3u) resolvedType = 'm3u';

  const payload = {
    type: resolvedType,
    url: inputM3u,
    host: effectiveHost,
    user: inputUser,
    pass: inputPass,
    clientName: document.getElementById('playlist-client-name')?.value.trim() || '',
    name: document.getElementById('playlist-client-name')?.value.trim() || '',
    clientPhone: document.getElementById('playlist-client-phone')?.value.trim() || '',
    notes: document.getElementById('playlist-client-notes')?.value.trim() || '',
    ownerId: document.getElementById('playlist-owner-id')?.value,
    expDate: document.getElementById('playlist-exp-manual')?.value.trim() || undefined
  };

  const res = await fetch(`/api/admin/devices/${selectedDeviceId}/playlist`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload)
  });

  if (res.ok) {
    const data = await res.json();
    let msg = 'Playlist enviada para o dispositivo com sucesso!';
    if (data.validity?.expDate) {
      msg += `\n\nValidade detectada no provedor: ${data.validity.expDate} (Status: ${data.validity.status})`;
    }
    alert(msg);
    closeModal('playlist-modal');
    loadDevices();
  } else {
    alert('Erro ao salvar playlist.');
  }
}

// Modal de Mensagem Push
function openMessageModal(id) {
  selectedDeviceId = id;
  isBroadcast = false;
  const dev = devices.find(d => d.id === id);
  document.getElementById('modal-message-subtitle').innerText = `Destinatário: ${dev?.name || id} (${dev?.mac || ''})`;
  document.getElementById('msg-text').value = '';
  document.getElementById('message-modal').classList.remove('hidden');
}

function openBroadcastModal() {
  selectedDeviceId = null;
  isBroadcast = true;
  document.getElementById('modal-message-subtitle').innerText = 'Destinatário: TODOS os dispositivos ativos';
  document.getElementById('msg-text').value = '';
  document.getElementById('message-modal').classList.remove('hidden');
}

async function sendMessage() {
  const text = document.getElementById('msg-text').value.trim();
  const type = document.getElementById('msg-type').value;
  if (!text) return alert('Digite a mensagem.');

  const endpoint = isBroadcast ? '/api/admin/devices/broadcast-message' : `/api/admin/devices/${selectedDeviceId}/message`;

  const res = await fetch(endpoint, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ message: text, type })
  });

  if (res.ok) {
    alert('Mensagem enviada! Aparecerá na tela do aparelho.');
    closeModal('message-modal');
  } else {
    alert('Erro ao enviar mensagem.');
  }
}

function closeModal(id) {
  document.getElementById(id).classList.add('hidden');
}

// Modal de Vincular Aparelho por Código
function openLinkCodeModal(prefillCode = '') {
  document.getElementById('link-code-input').value = prefillCode || '';
  const defaultHost = systemSettings.default_iptv_url || '';
  document.getElementById('link-xtream-host').value = defaultHost;
  document.getElementById('link-xtream-user').value = '';
  document.getElementById('link-xtream-pass').value = '';
  if (document.getElementById('link-client-name')) document.getElementById('link-client-name').value = '';
  if (document.getElementById('link-client-phone')) document.getElementById('link-client-phone').value = '';
  if (document.getElementById('link-client-notes')) document.getElementById('link-client-notes').value = '';

  const ownerSelect = document.getElementById('link-owner-id');
  if (ownerSelect) {
    if (currentUser && currentUser.role !== 'admin') {
      ownerSelect.value = String(currentUser.id);
    } else {
      ownerSelect.value = '';
    }
  }
  if (document.getElementById('link-exp-manual')) {
    document.getElementById('link-exp-manual').value = '';
  }

  document.getElementById('link-modal').classList.remove('hidden');
  if (!prefillCode) {
    document.getElementById('link-code-input').focus();
  } else if (!defaultHost) {
    document.getElementById('link-xtream-host').focus();
  } else {
    document.getElementById('link-xtream-user').focus();
  }
}

async function submitLinkByCode() {
  const code = document.getElementById('link-code-input').value.trim();
  const defaultHost = systemSettings.default_iptv_url || '';
  const inputHost = document.getElementById('link-xtream-host').value.trim();
  const host = inputHost || defaultHost;
  const user = document.getElementById('link-xtream-user').value.trim();
  const pass = document.getElementById('link-xtream-pass').value.trim();
  const name = document.getElementById('link-client-name')?.value.trim() || '';
  const phone = document.getElementById('link-client-phone')?.value.trim() || '';
  const notes = document.getElementById('link-client-notes')?.value.trim() || '';
  const ownerId = document.getElementById('link-owner-id')?.value;
  const expDate = document.getElementById('link-exp-manual')?.value.trim();

  if (!code) return alert('Por favor, informe o Código de Ativação do aparelho.');
  if (!host || !user || !pass) return alert('Por favor, preencha o Servidor (ou defina a URL Padrão nas Configurações), Usuário e Senha.');

  const btn = document.getElementById('btn-submit-link');
  btn.disabled = true;
  btn.innerText = 'Vinculando e Verificando...';

  try {
    const res = await fetch('/api/admin/devices/link-by-code', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        code,
        type: 'xtream',
        host,
        user,
        pass,
        name,
        phone,
        notes,
        ownerId: ownerId !== undefined ? ownerId : undefined,
        expDate: expDate || undefined
      })
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Não foi possível vincular o aparelho.');
    }

    let msg = `Aparelho "${data.deviceName || code}" vinculado com sucesso!\nAs credenciais foram enviadas para o aparelho.`;
    if (data.validity?.expDate) {
      msg += `\n\nValidade detectada no provedor: ${data.validity.expDate} (Status: ${data.validity.status})`;
    }
    alert(msg);
    closeModal('link-modal');
    loadDevices();
  } catch (err) {
    alert('Erro: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Vincular & Enviar para o Aparelho';
  }
}

// OTA: Carregar e Submeter
async function loadOtaUpdates() {
  try {
    const permInput = document.getElementById('permanent-download-link');
    if (permInput) {
      permInput.value = `${getPublicBaseUrl()}/app.apk`;
    }

    const res = await fetch('/api/admin/ota', { headers: authHeaders() });
    if (res.status === 401) {
      showLogin();
      return;
    }
    const data = await res.json();
    const list = data.updates || [];

    if (list.length > 0) {
      document.getElementById('stat-ota-version').innerText = `${list[0].version_name} (${list[0].version_code})`;
    } else {
      document.getElementById('stat-ota-version').innerText = '-';
    }

    const tbody = document.getElementById('ota-history-tbody');
    if (list.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="py-4 text-center text-secondary">Nenhuma versão publicada ainda.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map(u => `
      <tr>
        <td class="fw-bold text-white">${escapeHtml(u.version_name)}</td>
        <td class="font-monospace small text-body-secondary">${u.version_code}</td>
        <td>${u.mandatory ? '<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill">Sim</span>' : '<span class="badge bg-secondary-subtle text-secondary rounded-pill">Não</span>'}</td>
        <td class="small text-secondary">${new Date(Number(u.released_at)).toLocaleDateString('pt-BR')}</td>
        <td>
          <a href="${escapeHtml(u.download_url)}" target="_blank" class="btn btn-sm btn-link text-info p-0 font-monospace text-decoration-none">
            <i class="bi bi-download me-1"></i>Baixar APK
          </a>
        </td>
        <td class="text-end">
          <button onclick="deleteOtaUpdate(${u.id}, '${escapeHtml(u.version_name)}')" class="btn btn-sm btn-outline-danger rounded-pill px-2.5 py-1" title="Excluir versão e arquivo do servidor">
            <i class="bi bi-trash me-1"></i>Excluir
          </button>
        </td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Erro ao carregar OTA:', err);
  }
}

async function deleteOtaUpdate(id, versionName) {
  if (!confirm(`Tem certeza que deseja excluir a versão "${versionName || id}"?\n\nO registro e o arquivo APK físico no servidor serão excluídos.`)) {
    return;
  }
  try {
    const res = await fetch(`/api/admin/ota/${id}`, {
      method: 'DELETE',
      headers: authHeaders()
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      loadOtaUpdates();
    } else {
      alert('Erro ao excluir versão: ' + (data.error || res.statusText));
    }
  } catch (err) {
    alert('Erro de conexão ao excluir: ' + err.message);
  }
}

function copyPermanentLink() {
  const permInput = document.getElementById('permanent-download-link');
  if (!permInput) return;
  permInput.select();
  permInput.setSelectionRange(0, 99999);
  navigator.clipboard.writeText(permInput.value).then(() => {
    alert('Link permanente copiado com sucesso!\n\n' + permInput.value);
  }).catch(() => {
    prompt('Copie o link permanente abaixo:', permInput.value);
  });
}

async function submitOta(e) {
  e.preventDefault();
  const btn = document.getElementById('ota-submit-btn');
  btn.disabled = true;
  btn.innerText = 'Enviando APK...';

  try {
    const vName = document.getElementById('ota-version-name').value.trim();
    const vCode = document.getElementById('ota-version-code').value.trim();
    const changelog = document.getElementById('ota-changelog').value.trim();
    const mandatory = document.getElementById('ota-mandatory').checked;
    const apkFile = document.getElementById('ota-apk-file').files[0];
    const directUrl = document.getElementById('ota-download-url').value.trim();

    if (apkFile) {
      const formData = new FormData();
      formData.append('apk', apkFile);
      formData.append('versionName', vName);
      formData.append('versionCode', vCode);
      formData.append('changelog', changelog);
      formData.append('mandatory', mandatory ? '1' : '0');

      const res = await fetch('/api/admin/ota/upload', {
        method: 'POST',
        headers: { 'x-admin-token': getAdminToken() },
        body: formData
      });
      const data = await res.json();
      if (!res.ok || !data.success) throw new Error(data.error || 'Falha no upload do APK.');
      alert(`Atualização OTA publicada com sucesso!\nVersão detectada: v${data.versionName} (Code ${data.versionCode})`);
    } else if (directUrl) {
      if (!vName || !vCode) {
        alert('Ao usar link externo de download, preencha o Nome e o Código da Versão.');
        btn.disabled = false;
        btn.innerText = 'Publicar Versão';
        return;
      }
      const res = await fetch('/api/admin/ota', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          versionName: vName,
          versionCode: vCode,
          downloadUrl: directUrl,
          changelog,
          mandatory
        })
      });
      const data = await res.json();
      if (!res.ok || !data.success) throw new Error(data.error || 'Falha ao registrar versão.');
      alert('Atualização OTA publicada com sucesso! Os aparelhos clientes já podem baixar essa versão.');
    } else {
      alert('Selecione um arquivo APK ou informe uma URL de download.');
      btn.disabled = false;
      btn.innerText = 'Publicar Versão';
      return;
    }

    document.getElementById('ota-form').reset();
    loadOtaUpdates();
  } catch (err) {
    alert('Erro: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Publicar Versão';
  }
}

function escapeHtml(str) {
  return String(str || '').replace(/[&<>"']/g, m => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[m]);
}

// ==============================================================================
// AUTOMAÇÕES, WHATSAPP & CONFIGURAÇÕES IPTV
// ==============================================================================

async function loadSettings() {
  try {
    const res = await fetch('/api/admin/settings', { headers: authHeaders() });
    if (!res.ok) return;
    const data = await res.json();
    const s = data.settings || {};
    systemSettings = s;

    if (document.getElementById('iptv-default-url')) {
      document.getElementById('iptv-default-url').value = s.default_iptv_url || '';
    }
    if (document.getElementById('wa-api-url')) {
      document.getElementById('wa-api-url').value = s.whatsapp_api_url || '';
    }
    if (document.getElementById('wa-api-token')) {
      document.getElementById('wa-api-token').value = s.whatsapp_api_token || '';
    }
    if (document.getElementById('auto-expiry-enabled')) {
      document.getElementById('auto-expiry-enabled').checked = Boolean(s.auto_expiry_enabled);
    }
    if (document.getElementById('auto-expiry-days')) {
      document.getElementById('auto-expiry-days').value = s.auto_expiry_days || 3;
    }
    if (document.getElementById('auto-expiry-notify-tv')) {
      document.getElementById('auto-expiry-notify-tv').checked = Boolean(s.auto_expiry_notify_tv);
    }
    if (document.getElementById('auto-expiry-notify-wa')) {
      document.getElementById('auto-expiry-notify-wa').checked = Boolean(s.auto_expiry_notify_wa);
    }
    if (document.getElementById('auto-expiry-message')) {
      document.getElementById('auto-expiry-message').value = s.auto_expiry_message || '';
    }

    if (document.getElementById('test-api-without-adults')) {
      document.getElementById('test-api-without-adults').value = s.test_api_without_adults || '';
    }
    if (document.getElementById('test-api-with-adults')) {
      document.getElementById('test-api-with-adults').value = s.test_api_with_adults || '';
    }
    if (document.getElementById('portal-public-domain')) {
      document.getElementById('portal-public-domain').value = s.portal_public_domain || '';
    }
    if (document.getElementById('downloader-code')) {
      document.getElementById('downloader-code').value = s.downloader_code || '';
    }
    if (document.getElementById('test-app-url')) {
      document.getElementById('test-app-url').value = s.test_app_url || '';
    }
    if (document.getElementById('test-auto-whatsapp')) {
      document.getElementById('test-auto-whatsapp').checked = Boolean(s.test_auto_whatsapp);
    }
    if (document.getElementById('test-whatsapp-message')) {
      document.getElementById('test-whatsapp-message').value = s.test_whatsapp_message || '';
    }
    if (document.getElementById('tmdb-api-key')) {
      document.getElementById('tmdb-api-key').value = s.tmdb_api_key || '';
    }
    if (document.getElementById('omdb-api-key')) {
      document.getElementById('omdb-api-key').value = s.omdb_api_key || '';
    }
    loadMetadataStats();
    const publicLink = document.getElementById('link-public-teste');
    if (publicLink) {
      publicLink.href = `${getPublicBaseUrl()}/teste`;
      publicLink.innerText = `${getPublicBaseUrl()}/teste`;
    }
  } catch (err) {
    console.error('Erro ao carregar configurações:', err);
  }
}

async function saveDefaultIptvUrl() {
  const url = document.getElementById('iptv-default-url').value.trim();
  if (!url) return alert('Por favor, informe a URL do servidor IPTV.');

  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ default_iptv_url: url })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('URL do IPTV padrão salva com sucesso!');
    } else {
      alert('Erro ao salvar URL: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function migrateAllIptvUrl() {
  const url = document.getElementById('iptv-default-url').value.trim();
  if (!url) return alert('Por favor, informe a URL do servidor IPTV.');

  const conf = confirm(
    `ATENÇÃO: Deseja alterar o servidor IPTV de TODOS os clientes ativos para:\n\n${url}\n\nTodos os aparelhos conectados receberão essa nova URL no próximo sinal.`
  );
  if (!conf) return;

  try {
    const res = await fetch('/api/admin/iptv/migrate-url', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ newUrl: url })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert(`Migração em massa concluída com sucesso!\n${data.affected} dispositivo(s) atualizado(s) para o novo servidor.`);
      loadDevices();
    } else {
      alert('Erro na migração: ' + (data.error || 'Erro desconhecido'));
    }
  } catch (err) {
    alert('Erro ao migrar clientes: ' + err.message);
  }
}

async function saveWhatsAppApiSettings() {
  const url = document.getElementById('wa-api-url').value.trim();
  const token = document.getElementById('wa-api-token').value.trim();

  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        whatsapp_api_url: url,
        whatsapp_api_token: token
      })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('Credenciais da API do WhatsApp salvas com sucesso!');
    } else {
      alert('Erro ao salvar credenciais: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function testWhatsAppMessage() {
  const phone = document.getElementById('wa-test-phone').value.trim();
  const url = document.getElementById('wa-api-url').value.trim();
  const token = document.getElementById('wa-api-token').value.trim();

  if (!phone) return alert('Digite o número de WhatsApp com DDD para o teste (ex: 5511999999999).');

  const btn = document.getElementById('btn-test-wa');
  btn.disabled = true;
  btn.innerText = 'Enviando...';

  try {
    const res = await fetch('/api/admin/whatsapp/test', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        phone,
        apiUrl: url,
        apiToken: token,
        message: '✅ Mensagem de teste oficial enviada com sucesso pelo Portal Korp TV!'
      })
    });
    const data = await res.json();
    if (res.ok && data.success) {
      alert(`Mensagem enviada com sucesso para ${phone}! Verifique o WhatsApp.`);
    } else {
      alert(`Erro no envio da mensagem: ${data.error || 'Verifique a URL e o Token da API'}`);
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Testar';
  }
}

async function saveAutomationRules() {
  const enabled = document.getElementById('auto-expiry-enabled').checked;
  const days = document.getElementById('auto-expiry-days').value;
  const notifyTv = document.getElementById('auto-expiry-notify-tv').checked;
  const notifyWa = document.getElementById('auto-expiry-notify-wa').checked;
  const message = document.getElementById('auto-expiry-message').value;

  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        auto_expiry_enabled: enabled,
        auto_expiry_days: days,
        auto_expiry_notify_tv: notifyTv,
        auto_expiry_notify_wa: notifyWa,
        auto_expiry_message: message
      })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('Regras de automação salvas com sucesso!');
    } else {
      alert('Erro ao salvar regras: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function runAutomationsNow() {
  const btn = document.getElementById('btn-run-automations');
  btn.disabled = true;
  btn.innerText = 'Verificando...';

  try {
    const res = await fetch('/api/admin/automations/run-expiry', {
      method: 'POST',
      headers: authHeaders()
    });
    const data = await res.json();
    if (res.ok && data.success) {
      let msg = `Verificação concluída!\n\nDispositivos escaneados: ${data.scanned || 0}`;
      msg += `\nAvisos enviados na TV: ${data.notifiedTv || 0}`;
      msg += `\nMensagens enviadas no WhatsApp: ${data.notifiedWa || 0}`;
      if (data.errors && data.errors.length > 0) {
        msg += `\n\nErros:\n${data.errors.join('\n')}`;
      }
      alert(msg);
      loadDevices();
    } else {
      alert(`Erro: ${data.error || data.reason || 'Falha ao executar automações'}`);
    }
  } catch (err) {
    alert('Erro ao executar: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = '⚡ Executar Agora';
  }
}

// Salvar Configurações de Testes (Painel Slim)
async function saveTestSettings() {
  const apiWithout = document.getElementById('test-api-without-adults').value.trim();
  const apiWith = document.getElementById('test-api-with-adults').value.trim();
  const portalDomain = document.getElementById('portal-public-domain')?.value.trim() || '';
  const downloaderCode = document.getElementById('downloader-code')?.value.trim() || '';
  const appUrl = document.getElementById('test-app-url').value.trim();
  const autoWa = document.getElementById('test-auto-whatsapp').checked;
  const waMsg = document.getElementById('test-whatsapp-message').value.trim();

  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({
        test_api_without_adults: apiWithout,
        test_api_with_adults: apiWith,
        portal_public_domain: portalDomain,
        downloader_code: downloaderCode,
        test_app_url: appUrl,
        test_auto_whatsapp: autoWa,
        test_whatsapp_message: waMsg
      })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('Configurações de teste e Painel Slim salvas com sucesso!');
    } else {
      alert('Erro ao salvar: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

function copyTesteLink() {
  const link = `${getPublicBaseUrl()}/teste`;
  navigator.clipboard.writeText(link).then(() => {
    alert(`Link copiado: ${link}`);
  }).catch(() => {
    prompt('Copie o link abaixo:', link);
  });
}

// ==============================================================================
// SOLICITAÇÕES DE TESTES REALIZADAS
// ==============================================================================

async function loadTests() {
  const tbody = document.getElementById('tests-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="7" class="text-center py-6 text-gray-500">Carregando solicitações de testes...</td></tr>';

  try {
    const res = await fetch('/api/admin/tests', { headers: authHeaders() });
    if (!res.ok) return;
    const data = await res.json();
    const tests = data.tests || [];

    if (tests.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center py-8 text-gray-500">Nenhum teste solicitado ainda.<br><span class="text-xs text-gray-600">Compartilhe o link /teste com seus clientes.</span></td></tr>';
      return;
    }

    tbody.innerHTML = tests.map(t => {
      const isAdult = t.type === 'with_adults';
      const cleanPhone = String(t.phone || '').replace(/\D/g, '');
      const dateStr = t.created_at ? new Date(Number(t.created_at)).toLocaleString('pt-BR') : '-';

      const typeBadge = isAdult
        ? '<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill">🔞 Com Adultos</span>'
        : '<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill">🟢 Sem Adultos</span>';

      let waBadge = '';
      if (t.is_valid_wa) {
        waBadge = `<span class="badge bg-success-subtle text-success border border-success-subtle font-monospace ms-1" style="font-size: 0.65rem;" title="WhatsApp Válido${t.wa_lid ? ' (LID: ' + t.wa_lid + ')' : ''}">✓ Válido</span>`;
      } else {
        waBadge = `<span class="badge bg-secondary-subtle text-secondary font-monospace ms-1" style="font-size: 0.65rem;">Não verificado</span>`;
      }

      let lidHtml = '';
      if (t.wa_lid) {
        lidHtml = `<div class="text-body-tertiary font-monospace" style="font-size: 0.7rem;" title="WhatsApp LID">${escapeHtml(t.wa_lid)}</div>`;
      }

      return `
        <tr>
          <td class="text-secondary font-monospace small">${dateStr}</td>
          <td>
            <div class="fw-bold text-white d-flex align-items-center">
              <span>${escapeHtml(t.name || 'Cliente')}</span>
              ${waBadge}
            </div>
            <div class="mt-0.5">
              <a href="https://wa.me/${cleanPhone}" target="_blank" class="text-decoration-none d-inline-flex align-items-center gap-1 text-success small font-monospace">
                <i class="bi bi-whatsapp"></i> ${escapeHtml(t.phone)}
              </a>
            </div>
            ${lidHtml}
          </td>
          <td>${typeBadge}</td>
          <td class="font-monospace fw-bold text-info select-all">${escapeHtml(t.username || '-')}</td>
          <td class="font-monospace text-light select-all">${escapeHtml(t.password || '-')}</td>
          <td class="font-monospace text-warning text-nowrap">${escapeHtml(t.expires_at_formatted || '-')}</td>
          <td class="text-end text-nowrap">
            <div class="d-flex align-items-center justify-content-end gap-1">
              ${t.pay_url ? `<a href="${escapeHtml(t.pay_url)}" target="_blank" class="btn btn-sm btn-outline-warning rounded-pill px-2.5 py-1" title="Checkout Painel Slim"><i class="bi bi-credit-card me-1"></i>Checkout</a>` : ''}
              <a href="https://wa.me/${cleanPhone}" target="_blank" class="btn btn-sm btn-success fw-semibold rounded-pill px-2.5 py-1">
                <i class="bi bi-whatsapp me-1"></i>Conversar
              </a>
              <button onclick="deleteTest(${t.id})" class="btn btn-sm btn-link text-danger p-1" title="Excluir">
                <i class="bi bi-trash"></i>
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-6 text-red-400">Erro ao carregar testes: ${escapeHtml(err.message)}</td></tr>`;
  }
}

async function deleteTest(id) {
  if (!confirm('Excluir este registro de teste?')) return;
  try {
    await fetch(`/api/admin/tests/${id}`, { method: 'DELETE', headers: authHeaders() });
    loadTests();
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

// ==============================================================================
// MODAL: QR CODE & DOWNLOAD APK
// ==============================================================================

let portalQrCodeInstance = null;

function openQrModal() {
  const permUrl = `${getPublicBaseUrl()}/app.apk`;
  document.getElementById('modal-qr-download-link').value = permUrl;
  document.getElementById('btn-modal-direct-apk').href = permUrl;

  const container = document.getElementById('portal-qrcode-container');
  container.innerHTML = '';

  portalQrCodeInstance = new QRCode(container, {
    text: permUrl,
    width: 200,
    height: 200,
    colorDark: "#000000",
    colorLight: "#ffffff",
    correctLevel: QRCode.CorrectLevel.H
  });

  const downBox = document.getElementById('modal-downloader-box');
  const downCode = document.getElementById('modal-downloader-code');
  if (downBox && downCode) {
    if (systemSettings && systemSettings.downloader_code && systemSettings.downloader_code.trim()) {
      downCode.innerText = systemSettings.downloader_code.trim();
      downBox.classList.remove('hidden');
    } else {
      downBox.classList.add('hidden');
    }
  }

  document.getElementById('qr-modal').classList.remove('hidden');
}

function copyModalDownloaderCode() {
  const code = document.getElementById('modal-downloader-code')?.innerText || '';
  if (!code) return;
  navigator.clipboard.writeText(code).then(() => {
    alert(`Código Downloader copiado: ${code}`);
  }).catch(() => {
    prompt('Copie o código abaixo:', code);
  });
}

function copyModalQrLink() {
  const input = document.getElementById('modal-qr-download-link');
  navigator.clipboard.writeText(input.value).then(() => {
    alert(`Link de download copiado: ${input.value}`);
  }).catch(() => {
    prompt('Copie o link abaixo:', input.value);
  });
}

function downloadQrCodeImage() {
  const container = document.getElementById('portal-qrcode-container');
  const canvas = container.querySelector('canvas');
  const img = container.querySelector('img');

  let dataUrl = null;
  if (canvas) {
    dataUrl = canvas.toDataURL('image/png');
  } else if (img && img.src) {
    dataUrl = img.src;
  }

  if (!dataUrl) {
    alert('Não foi possível gerar a imagem do QR Code no momento.');
    return;
  }

  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = 'qrcode-korp-tv-download.png';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

// -------------------------------------------------------------
// METADADOS & CACHE (TMDB / OMDB)
// -------------------------------------------------------------
function togglePasswordVisibility(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.type = el.type === 'password' ? 'text' : 'password';
}

async function loadMetadataStats() {
  try {
    const res = await fetch('/api/admin/meta/stats', { headers: authHeaders() });
    if (!res.ok) return;
    const data = await res.json();
    const stats = data.stats || {};
    if (document.getElementById('meta-cache-count')) {
      document.getElementById('meta-cache-count').innerText = `${stats.total || 0} itens`;
    }
    if (document.getElementById('meta-cache-tmdb')) {
      document.getElementById('meta-cache-tmdb').innerText = stats.tmdb || 0;
    }
    if (document.getElementById('meta-cache-omdb')) {
      document.getElementById('meta-cache-omdb').innerText = stats.omdb || 0;
    }
  } catch (_) {}
}

async function saveTmdbOnly() {
  const key = document.getElementById('tmdb-api-key').value.trim();
  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ tmdb_api_key: key })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('Chave API do TMDB salva com sucesso no banco de dados!');
      loadMetadataStats();
    } else {
      alert('Erro ao salvar TMDB: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function testTmdbOnly() {
  const btn = document.getElementById('btn-test-tmdb');
  const statusEl = document.getElementById('tmdb-test-status');
  const key = document.getElementById('tmdb-api-key').value.trim();
  if (!key) return alert('Por favor, informe a chave da API do TMDB para testar.');

  btn.disabled = true;
  btn.innerText = 'Testando...';
  if (statusEl) statusEl.innerText = 'Verificando...';

  try {
    const res = await fetch('/api/admin/meta/test', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ tmdbKey: key })
    });
    const data = await res.json();
    const r = data.results?.tmdb;
    if (r) {
      if (statusEl) {
        statusEl.innerHTML = r.ok
          ? '<span class="text-emerald-400 font-bold">🟢 Válida</span>'
          : `<span class="text-rose-400 font-bold" title="${r.message}">🔴 Falhou</span>`;
      }
      alert('TMDB: ' + r.message);
    }
  } catch (err) {
    alert('Erro ao testar TMDB: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Testar';
  }
}

async function clearTmdbCache() {
  if (!confirm('Deseja limpar todos os metadados do TMDB salvos em cache no banco de dados?')) return;
  try {
    const res = await fetch('/api/admin/meta/clear-cache', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ provider: 'tmdb' })
    });
    alert('Cache do TMDB limpo com sucesso!');
    loadMetadataStats();
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function saveOmdbOnly() {
  const key = document.getElementById('omdb-api-key').value.trim();
  try {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ omdb_api_key: key })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok && data.success) {
      alert('Chave API do OMDb salva com sucesso no banco de dados!');
      loadMetadataStats();
    } else {
      alert('Erro ao salvar OMDb: ' + (data.error || `HTTP ${res.status}`));
    }
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

async function testOmdbOnly() {
  const btn = document.getElementById('btn-test-omdb');
  const statusEl = document.getElementById('omdb-test-status');
  const key = document.getElementById('omdb-api-key').value.trim();
  if (!key) return alert('Por favor, informe a chave da API do OMDb para testar.');

  btn.disabled = true;
  btn.innerText = 'Testando...';
  if (statusEl) statusEl.innerText = 'Verificando...';

  try {
    const res = await fetch('/api/admin/meta/test', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ omdbKey: key })
    });
    const data = await res.json();
    const r = data.results?.omdb;
    if (r) {
      if (statusEl) {
        statusEl.innerHTML = r.ok
          ? '<span class="text-emerald-400 font-bold">🟢 Válida</span>'
          : `<span class="text-rose-400 font-bold" title="${r.message}">🔴 Falhou</span>`;
      }
      alert('OMDb: ' + r.message);
    }
  } catch (err) {
    alert('Erro ao testar OMDb: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Testar';
  }
}

async function clearOmdbCache() {
  if (!confirm('Deseja limpar todas as avaliações do OMDb salvas em cache no banco de dados?')) return;
  try {
    const res = await fetch('/api/admin/meta/clear-cache', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ provider: 'omdb' })
    });
    alert('Cache do OMDb limpo com sucesso!');
    loadMetadataStats();
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

// -------------------------------------------------------------
// GERENCIAMENTO DE USUÁRIOS & OPERADORES
// -------------------------------------------------------------
let usersList = [];

async function loadUsers() {
  const tbody = document.getElementById('users-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="5" class="text-center py-6 text-gray-500">Carregando usuários...</td></tr>';

  try {
    const res = await fetch('/api/admin/users', { headers: authHeaders() });
    if (!res.ok) {
      if (res.status === 403) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-6 text-gray-500">Apenas administradores podem gerenciar usuários.</td></tr>';
      }
      return;
    }
    const data = await res.json();
    usersList = data.users || [];

    if (usersList.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center py-6 text-gray-500">Nenhum operador ou usuário cadastrado.</td></tr>';
      return;
    }

    tbody.innerHTML = usersList.map(u => {
      const isAdm = u.role === 'admin';
      const roleBadge = isAdm
        ? '<span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill">Administrador</span>'
        : '<span class="badge bg-info-subtle text-info border border-info-subtle rounded-pill">Operador</span>';

      const createdAtStr = u.created_at ? new Date(Number(u.created_at)).toLocaleDateString('pt-BR') : '-';
      const isSelf = currentUser && currentUser.id === u.id;

      return `
        <tr>
          <td class="fw-bold text-white">${escapeHtml(u.name || '-')}</td>
          <td class="font-monospace text-info fw-semibold">${escapeHtml(u.username)}</td>
          <td>${roleBadge}</td>
          <td class="text-secondary font-monospace small">${createdAtStr}</td>
          <td class="text-end text-nowrap">
            <div class="d-flex align-items-center justify-content-end gap-1">
              <button onclick="openUserModal(${u.id})" class="btn btn-sm btn-outline-primary rounded-pill px-2.5 py-1" title="Editar Usuário">
                Editar
              </button>
              ${isSelf ? '' : `
                <button onclick="deleteUser(${u.id}, '${escapeHtml(u.username)}')" class="btn btn-sm btn-link text-danger p-1" title="Excluir Usuário">
                  <i class="bi bi-trash"></i>
                </button>
              `}
            </div>
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    console.error('Erro ao listar usuários:', err);
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-6 text-red-400">Erro: ${err.message}</td></tr>`;
  }
}

function openUserModal(userId = null) {
  const modal = document.getElementById('user-modal');
  const title = document.getElementById('user-modal-title');
  const idInput = document.getElementById('user-id-input');
  const nameInput = document.getElementById('user-name-input');
  const userInput = document.getElementById('user-username-input');
  const passInput = document.getElementById('user-password-input');
  const passHint = document.getElementById('user-pass-hint');
  const roleInput = document.getElementById('user-role-input');

  if (userId) {
    const user = usersList.find(u => u.id === userId);
    if (!user) return;
    title.innerText = `Editar Usuário (${user.username})`;
    idInput.value = user.id;
    nameInput.value = user.name || '';
    userInput.value = user.username || '';
    passInput.value = '';
    passInput.required = false;
    passHint.innerText = '(deixe em branco para manter a atual)';
    roleInput.value = user.role || 'operator';
  } else {
    title.innerText = 'Novo Operador / Usuário';
    idInput.value = '';
    nameInput.value = '';
    userInput.value = '';
    passInput.value = '';
    passInput.required = true;
    passHint.innerText = '(obrigatória)';
    roleInput.value = 'operator';
  }

  modal.classList.remove('hidden');
}

async function saveUser(e) {
  e.preventDefault();
  const id = document.getElementById('user-id-input').value;
  const name = document.getElementById('user-name-input').value.trim();
  const username = document.getElementById('user-username-input').value.trim();
  const password = document.getElementById('user-password-input').value.trim();
  const role = document.getElementById('user-role-input').value;
  const btn = document.getElementById('user-modal-btn');

  btn.disabled = true;
  btn.innerText = 'Salvando...';

  try {
    const isEdit = Boolean(id);
    const url = isEdit ? `/api/admin/users/${id}` : '/api/admin/users';
    const method = isEdit ? 'PUT' : 'POST';

    const payload = { name, username, role };
    if (password) payload.password = password;

    const res = await fetch(url, {
      method,
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Erro ao salvar usuário.');
    }

    closeModal('user-modal');
    loadUsers();
    loadOperators();
    alert(isEdit ? 'Usuário atualizado com sucesso!' : 'Novo usuário cadastrado com sucesso!');
  } catch (err) {
    alert('Erro: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerText = 'Salvar Usuário';
  }
}

async function deleteUser(id, username) {
  if (!confirm(`Tem certeza que deseja excluir o usuário "${username}"?`)) return;

  try {
    const res = await fetch(`/api/admin/users/${id}`, {
      method: 'DELETE',
      headers: authHeaders()
    });
    const data = await res.json();
    if (!res.ok || !data.success) {
      throw new Error(data.error || 'Erro ao excluir usuário.');
    }
    loadUsers();
    loadOperators();
    loadDevices();
  } catch (err) {
    alert('Erro: ' + err.message);
  }
}

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
  const token = getAdminToken();
  if (token) {
    showApp();
    loadDevices();
    loadOtaUpdates();
    setInterval(loadDevices, 15000);
  } else {
    showLogin();
  }
});
