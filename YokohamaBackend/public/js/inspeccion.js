// inspeccion.js - Logica para la pagina de Inspecciones Vehiculares

// Theme management
function toggleTheme() {
  const isDark = document.documentElement.classList.toggle('dark-theme');
  localStorage.setItem('theme', isDark ? 'dark' : 'light');
  updateThemeIcon();
  updateLogo();
  renderCharts();
}

function updateThemeIcon() {
  const isDark = document.documentElement.classList.contains('dark-theme');
  const icon = document.getElementById('themeIcon');
  if (icon) {
    icon.textContent = isDark ? '☀️' : '🌙';
  }
}

function updateLogo() {
  const logo = document.getElementById('brandLogo');
  if (!logo) return;
  const isDark = document.documentElement.classList.contains('dark-theme');
  logo.src = isDark ? '/img/yokohamalogoblanco.png' : '/img/yokohamalogo.png';
}

function getStoredUser() {
  try {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  } catch (error) {
    console.error('Error parsing user from localStorage:', error);
    return null;
  }
}

function renderUserName(user) {
  const userNameElement = document.getElementById('userName');
  if (userNameElement && user) {
    userNameElement.textContent = user.UsuariosNombre || 'Usuario';
  }
}

function initializeUserMenu() {
  const userMenu = document.getElementById('userMenu');
  const userMenuToggle = document.getElementById('userMenuToggle');
  const userDropdown = document.getElementById('userDropdown');
  const logoutBtn = document.getElementById('logoutBtn');

  if (!userMenu || !userMenuToggle || !userDropdown || !logoutBtn) {
    return;
  }

  function closeDropdown() {
    userDropdown.classList.remove('open');
    userDropdown.setAttribute('aria-hidden', 'true');
  }

  function toggleDropdown() {
    const isOpen = userDropdown.classList.toggle('open');
    userDropdown.setAttribute('aria-hidden', isOpen ? 'false' : 'true');
  }

  userMenuToggle.addEventListener('click', function (event) {
    event.stopPropagation();
    toggleDropdown();
  });

  userMenuToggle.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      toggleDropdown();
    }
  });

  logoutBtn.addEventListener('click', function () {
    localStorage.removeItem('isLoggedIn');
    localStorage.removeItem('user');
    closeDropdown();
    window.location.href = 'login.html';
  });

  document.addEventListener('click', function (event) {
    if (!userMenu.contains(event.target)) {
      closeDropdown();
    }
  });
}

// Initialize theme and user
updateThemeIcon();
updateLogo();
const user = getStoredUser();
if (user) {
  renderUserName(user);
}
initializeUserMenu();

// Navigation
const btnGoInicio = document.getElementById('btnGoInicio');
btnGoInicio?.addEventListener('click', () => {
  window.location.href = 'index.html';
});

const btnAdminIncentivos = document.getElementById('btnAdminIncentivos');
btnAdminIncentivos?.addEventListener('click', () => {
  window.location.href = 'incentivos.html';
});

const btnAdminRendimientos = document.getElementById('btnAdminRendimientos');
btnAdminRendimientos?.addEventListener('click', () => {
  try {
    localStorage.removeItem('selectedFlotaForRendimientos');
    localStorage.removeItem('selectedFlotaContext');
  } catch (e) {}
  window.location.href = 'rendimientos.html';
});

// Data variables
let allLlantasInspeccion = [];
let allPruebasInspeccion = [];
let filteredLlantasInspeccion = [];
let currentView = 'PRESIONES_INFLADO';
let currentCharts = [];
let selectedPruebaId = null;

const NAV_FLOTA_KEYS = {
  'desecho.html': 'selectedFlotaForDesecho',
  'semaforo.html': 'selectedFlotaForSemaforo',
  'inspeccion.html': 'selectedFlotaForInspeccion',
  'rendimientos.html': 'selectedFlotaForRendimientos'
};

const RENOVABLE_MM_THRESHOLD = 8;

const photoModal = document.getElementById('photoModal');
const btnClosePhotoModal = document.getElementById('btnClosePhotoModal');
const photoModalImg1 = document.getElementById('photoModalImg1');
const photoModalImg2 = document.getElementById('photoModalImg2');
const photoModalEmpty1 = document.getElementById('photoModalEmpty1');
const photoModalEmpty2 = document.getElementById('photoModalEmpty2');
const inspeccionListContainer = document.getElementById('inspeccionList');

function getCurrentFlotaContext() {
  const uniqueFlotas = Array.from(new Set(
    filteredLlantasInspeccion
      .map((llanta) => String(llanta?.FlotasNombre || '').trim())
      .filter(Boolean)
  ));

  if (uniqueFlotas.length === 1) {
    return uniqueFlotas[0];
  }

  return String(localStorage.getItem('selectedFlotaContext') || '').trim();
}

function persistFlotaContextForTarget(targetHref) {
  const flota = getCurrentFlotaContext();
  if (!flota) {
    return;
  }

  const targetPage = String(targetHref || '').split('?')[0].split('#')[0].toLowerCase();
  const targetStorageKey = NAV_FLOTA_KEYS[targetPage];

  localStorage.setItem('selectedFlotaContext', flota);
  if (targetStorageKey) {
    localStorage.setItem(targetStorageKey, flota);
  }
}

function bindQuickAccessNavigationContext() {
  document.querySelectorAll('.quick-access-icons a[href]').forEach((link) => {
    link.addEventListener('click', () => {
      persistFlotaContextForTarget(link.getAttribute('href'));
    });
  });
}

bindQuickAccessNavigationContext();

function getPhotoSrc(value) {
  if (!value) {
    return '';
  }
  const stringValue = String(value).trim();
  if (!stringValue) {
    return '';
  }

  if (stringValue.startsWith('data:image')) {
    return stringValue;
  }

  try {
    const testDecode = atob(stringValue.substring(0, 40));
    if (testDecode.startsWith('/9j/')) {
      const decoded = atob(stringValue);
      return `data:image/jpeg;base64,${decoded}`;
    }
  } catch (_) {
    // ignore
  }

  return `data:image/jpeg;base64,${stringValue}`;
}

function setModalImage(targetImg, targetEmpty, src) {
  if (!targetImg || !targetEmpty) {
    return;
  }
  if (src) {
    targetImg.src = src;
    targetImg.style.display = 'block';
    targetEmpty.style.display = 'none';

    targetImg.onerror = function () {
      targetImg.style.display = 'none';
      targetEmpty.style.display = 'block';
    };
  } else {
    targetImg.removeAttribute('src');
    targetImg.style.display = 'none';
    targetEmpty.style.display = 'block';
  }
}

function openPhotoModal(foto1, foto2) {
  if (!photoModal) return;
  const src1 = getPhotoSrc(foto1);
  const src2 = getPhotoSrc(foto2);
  setModalImage(photoModalImg1, photoModalEmpty1, src1);
  setModalImage(photoModalImg2, photoModalEmpty2, src2);
  photoModal.classList.add('open');
  photoModal.setAttribute('aria-hidden', 'false');
}

function closePhotoModal() {
  if (!photoModal) return;
  photoModal.classList.remove('open');
  photoModal.setAttribute('aria-hidden', 'true');
}

btnClosePhotoModal?.addEventListener('click', closePhotoModal);
photoModal?.addEventListener('click', (event) => {
  if (event.target === photoModal) {
    closePhotoModal();
  }
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    closePhotoModal();
  }
});

async function fetchLlantasInspeccion() {
  try {
    const response = await fetch('/api/llantas-inspeccion');
    if (!response.ok) throw new Error('Error fetching llantas inspeccion');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Error fetching data:', error);
    return [];
  }
}

async function fetchPruebasInspeccion() {
  try {
    let url = '/api/pruebas-inspeccion';
    const savedFlota = localStorage.getItem('selectedFlotaForInspeccion') || localStorage.getItem('selectedFlotaContext');
    if (savedFlota) {
      url += '?flotaName=' + encodeURIComponent(savedFlota);
    }
    const response = await fetch(url);
    if (!response.ok) throw new Error('Error fetching pruebas inspeccion');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Error fetching pruebas inspeccion:', error);
    return [];
  }
}

function applyFlotaFilter() {
  const savedFlota = localStorage.getItem('selectedFlotaForInspeccion') || localStorage.getItem('selectedFlotaContext');
  if (savedFlota) {
    const normalizedSavedFlota = String(savedFlota).trim().toLowerCase();
    const matchingPruebaIds = new Set(
      allPruebasInspeccion
        .filter((prueba) => String(prueba?.FlotasNombre || '').trim().toLowerCase().includes(normalizedSavedFlota))
        .map((prueba) => String(prueba?.idPruebaInspeccion || '').trim())
        .filter(Boolean)
    );

    filteredLlantasInspeccion = allLlantasInspeccion.filter((llanta) => {
      const llantaFlota = String(llanta?.FlotasNombre || '').trim().toLowerCase();
      const pruebaId = String(getPruebaId(llanta) || '').trim();
      return (llantaFlota && llantaFlota.includes(normalizedSavedFlota)) || matchingPruebaIds.has(pruebaId);
    });

    localStorage.setItem('selectedFlotaContext', savedFlota);
    localStorage.removeItem('selectedFlotaForInspeccion');
  } else {
    filteredLlantasInspeccion = [...allLlantasInspeccion];
  }
}

function getPruebaId(llanta) {
  return llanta?.pruebasinspeccion_idPruebaInspeccion ?? llanta?.PruebasInspeccion_idPruebaInspeccion ?? null;
}

function getPruebaFecha(llanta) {
  if (!llanta?.PruebaInspeccionFecha) return 'Sin fecha';
  return new Date(llanta.PruebaInspeccionFecha).toLocaleDateString('es-MX');
}

function getPruebaNombreById(pruebaId) {
  const targetId = String(pruebaId || '').trim();
  if (!targetId) return '';

  const prueba = allPruebasInspeccion.find(
    (item) => String(item?.idPruebaInspeccion || '').trim() === targetId
  );

  return String(
    prueba?.PruebaInspeccionTitulo || prueba?.PruebasInspeccionTitulo || prueba?.PruebaInspeccionNombre || ''
  ).trim();
}

function enrichChartTitlesWithPruebaName() {
  const chartsContainer = document.getElementById('chartsContainer');
  if (!chartsContainer) return;

  chartsContainer.querySelectorAll('.chart-card').forEach((card) => {
    const titleElement = card.querySelector('h3');
    if (!titleElement) return;

    const pruebaId = String(card.dataset.pruebaId || '').trim();
    const pruebaNombre = getPruebaNombreById(pruebaId);
    if (!pruebaNombre) return;

    const currentText = String(titleElement.textContent || '').trim();
    if (!currentText || /\|\s*Prueba:/i.test(currentText)) return;

    const lines = currentText
      .split('\n')
      .map((line) => String(line).trim())
      .filter(Boolean);

    if (lines.length === 0) return;

    const heading = lines[0];
    const fecha = lines[1] || 'Sin fecha';
    titleElement.textContent = `${heading}\n${fecha} | Prueba: ${pruebaNombre}`;
  });
}

function computeAvgMm(llanta) {
  const values = [
    llanta.LlantasInspeccionMm1,
    llanta.LlantasInspeccionMm2,
    llanta.LlantasInspeccionMm3,
    llanta.LlantasInspeccionMm4
  ].map((v) => parseFloat(v)).filter((v) => Number.isFinite(v));

  if (values.length === 0) return 0;
  const sum = values.reduce((acc, v) => acc + v, 0);
  return sum / values.length;
}

function computeFactorDel(llanta) {
  const values = [
    llanta.LlantasInspeccionMm1,
    llanta.LlantasInspeccionMm2,
    llanta.LlantasInspeccionMm3,
    llanta.LlantasInspeccionMm4
  ].map((v) => parseFloat(v)).filter((v) => Number.isFinite(v));

  if (values.length === 0) {
    return null;
  }

  const maxMm = Math.max(...values);
  const minMm = Math.min(...values);
  return maxMm - minMm;
}

function toBooleanFlag(value) {
  if (value === true || value === false) {
    return value;
  }
  if (value === 1 || value === '1') {
    return true;
  }
  if (value === 0 || value === '0') {
    return false;
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'si' || normalized === 'sí' || normalized === 'yes' || normalized === 'true') {
      return true;
    }
    if (normalized === 'no' || normalized === 'false') {
      return false;
    }
  }
  return null;
}

function normalizePressureStatus(value) {
  if (value === null || value === undefined || value === '') return 'SIN DATO';
  const raw = String(value).trim();
  if (!raw) return 'SIN DATO';
  const lower = raw.toLowerCase();
  if (lower.includes('arriba')) return 'ARRIBA DE PRESION';
  if (lower.includes('abajo')) return 'ABAJO DE PRESION';
  if (lower.includes('inaccesible')) return 'INACCESIBLE';
  if (lower.includes('en presion') || lower.includes('en presión') || lower.includes('ok')) return 'EN PRESION';
  if (Number.isFinite(parseFloat(raw))) return 'PRESION REGISTRADA';
  return raw.toUpperCase();
}

function toFiniteNumber(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function getInspeccionPresionClass(llanta) {
  const vigiaFlag = toBooleanFlag(llanta?.LlantasInspeccionVigia);
  if (vigiaFlag === true) {
    return 'presion-valor presion-vigia';
  }

  const presion = toFiniteNumber(llanta?.LlantasInspeccionPresion);
  const pMin = toFiniteNumber(llanta?.ParametrosPMin);
  const pSug = toFiniteNumber(llanta?.ParametrosPSug);

  if (presion !== null && pMin !== null && pSug !== null) {
    if (presion <= pMin) return 'presion-valor presion-rojo';
    if (presion <= pSug) return 'presion-valor presion-verde';
    return 'presion-valor presion-amarillo';
  }

  const normalized = normalizePressureStatus(llanta?.LlantasInspeccionPresion);
  if (normalized === 'ABAJO DE PRESION') return 'presion-valor presion-rojo';
  if (normalized === 'EN PRESION') return 'presion-valor presion-verde';
  if (normalized === 'ARRIBA DE PRESION') return 'presion-valor presion-amarillo';

  return 'presion-valor';
}

function getInspeccionPresionBucket(llanta) {
  const vigiaFlag = toBooleanFlag(llanta?.LlantasInspeccionVigia);
  if (vigiaFlag === true) {
    return 'VIGIA';
  }

  const presion = toFiniteNumber(llanta?.LlantasInspeccionPresion);
  const pMin = toFiniteNumber(llanta?.ParametrosPMin);
  const pSug = toFiniteNumber(llanta?.ParametrosPSug);

  if (presion !== null && pMin !== null && pSug !== null) {
    if (presion <= pMin) return 'ABAJO DE PRESION';
    if (presion <= pSug) return 'EN PRESION';
    return 'ARRIBA DE PRESION';
  }

  const normalized = normalizePressureStatus(llanta?.LlantasInspeccionPresion);
  if (normalized === 'ABAJO DE PRESION') return 'ABAJO DE PRESION';
  if (normalized === 'EN PRESION') return 'EN PRESION';
  if (normalized === 'ARRIBA DE PRESION') return 'ARRIBA DE PRESION';
  if (normalized === 'INACCESIBLE') return 'INACCESIBLE';
  if (normalized === 'PRESION REGISTRADA') return 'PRESION REGISTRADA';
  return 'SIN DATO';
}

function normalizeDesgasteStatus(value) {
  if (!value) return 'SIN DATO';
  const lower = String(value).toLowerCase();
  if (lower.includes('irregular')) return 'IRREGULAR';
  return 'NORMAL';
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function hashString(value) {
  const text = String(value || '');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash) + text.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function normalizeTypeName(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

const VEHICLE_ICON_TEMPLATES = [
  '<path d="M3 12l2-4h11l2 4v4H3z"></path><circle cx="8" cy="18" r="1.7"></circle><circle cx="16" cy="18" r="1.7"></circle>',
  '<path d="M3 7h11v7H3z"></path><path d="M14 10h4l3 3v1h-2"></path><circle cx="8" cy="18" r="1.7"></circle><circle cx="17" cy="18" r="1.7"></circle>',
  '<rect x="4" y="5" width="16" height="12" rx="2"></rect><circle cx="8" cy="18" r="1.7"></circle><circle cx="16" cy="18" r="1.7"></circle>',
  '<path d="M2.5 13l2-4.5h12l2 4.5v3.5h-16z"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="15.5" cy="18" r="1.7"></circle>',
  '<path d="M4 8h10v8H4z"></path><path d="M14 10h4l2 2.5V16h-2"></path><circle cx="8" cy="18" r="1.7"></circle><circle cx="17" cy="18" r="1.7"></circle>',
  '<path d="M3 11l2-3.5h10l3 3.5V16H3z"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="15.5" cy="18" r="1.7"></circle>',
  '<path d="M4 10h9v6H4z"></path><path d="M13 11h5l2 2.2V16h-2"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="17" cy="18" r="1.7"></circle>',
  '<path d="M3 9h13v7H3z"></path><path d="M6 6h6v3"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="13.5" cy="18" r="1.7"></circle>',
  '<path d="M3 12l2-3h10l2 3v4H3z"></path><path d="M7 9V7h5v2"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="14.5" cy="18" r="1.7"></circle>',
  '<rect x="3.5" y="7" width="13" height="8" rx="1.5"></rect><path d="M16.5 10h3l1.5 1.8V15h-1.8"></path><circle cx="7.5" cy="18" r="1.7"></circle><circle cx="17.2" cy="18" r="1.7"></circle>',
  '<path d="M4 11h12l2 2.6V16H4z"></path><path d="M6 8h5v3"></path><circle cx="8" cy="18" r="1.7"></circle><circle cx="16" cy="18" r="1.7"></circle>',
  '<path d="M3 10h10v6H3z"></path><path d="M13 10h5l2 2.6V16h-2"></path><path d="M5 8h4v2"></path><circle cx="7" cy="18" r="1.7"></circle><circle cx="17" cy="18" r="1.7"></circle>'
];

function getTipoVehiculoIcon(typeName, iconKey) {
  const normalized = normalizeTypeName(typeName);

  if (normalized.includes('autobus') || normalized.includes('bus') || normalized.includes('microbus')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[2]}</svg>`;
  }
  if (normalized.includes('tracto') || normalized.includes('quinta rueda') || normalized.includes('quintaru') || normalized.includes('trailer')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[1]}</svg>`;
  }
  if (normalized.includes('camioneta') || normalized.includes('pickup') || normalized.includes('van') || normalized.includes('utilitario')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[0]}</svg>`;
  }
  if (normalized.includes('camion') || normalized.includes('torton') || normalized.includes('rabon')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[4]}</svg>`;
  }
  if (normalized.includes('remolque') || normalized.includes('plataforma') || normalized.includes('caja seca') || normalized.includes('caja')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[7]}</svg>`;
  }
  if (normalized.includes('moto')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[8]}</svg>`;
  }
  if (normalized.includes('suv') || normalized.includes('auto') || normalized.includes('sedan') || normalized.includes('hatchback')) {
    return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[3]}</svg>`;
  }

  const fallbackSeed = hashString(typeName || 'sin-tipo');
  const fallbackIndex = fallbackSeed % VEHICLE_ICON_TEMPLATES.length;
  const parsedKey = Number(iconKey);
  const iconIndex = Number.isFinite(parsedKey)
    ? Math.abs(parsedKey) % VEHICLE_ICON_TEMPLATES.length
    : fallbackIndex;

  return `<svg viewBox="0 0 24 24" aria-hidden="true">${VEHICLE_ICON_TEMPLATES[iconIndex]}</svg>`;
}

function selectChart(pruebaId) {
  if (selectedPruebaId === pruebaId) {
    selectedPruebaId = null;
  } else {
    selectedPruebaId = pruebaId;
  }

  document.querySelectorAll('.chart-card').forEach(card => {
    if (card.dataset.pruebaId === String(pruebaId) && selectedPruebaId === pruebaId) {
      card.classList.add('selected');
    } else {
      card.classList.remove('selected');
    }
  });

  renderTable();
  updateTotalCount();
}

function renderCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  if (!chartsContainer) return;

  currentCharts.forEach(chart => chart.destroy());
  currentCharts = [];
  chartsContainer.innerHTML = '';

  if (currentView === 'PRESIONES_INFLADO') {
    renderPresionesInfladoCharts();
  } else if (currentView === 'PARTICIPACION_MARCA') {
    renderParticipacionMarcaCharts();
  } else if (currentView === 'RENOVABILIDAD') {
    renderRenovabilidadCharts();
  } else if (currentView === 'REMANENTES_MM') {
    renderRemanentesMmCharts();
  } else if (currentView === 'DESGASTES_IRREGULARES') {
    renderDesgastesIrregularesCharts();
  } else if (currentView === 'VEHICULOS_INSPECCIONADOS') {
    renderVehiculosInspeccionadosCharts();
  }

  enrichChartTitlesWithPruebaName();
}

function buildPruebaGroups() {
  const dataByPrueba = {};
  filteredLlantasInspeccion.forEach(llanta => {
    const pruebaId = getPruebaId(llanta);
    const pruebaFecha = getPruebaFecha(llanta);
    if (!pruebaId) return;
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        items: []
      };
    }
    dataByPrueba[pruebaId].items.push(llanta);
  });

  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

  return { dataByPrueba, sortedPruebas };
}

function renderPresionesInfladoCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `PRESIONES DE INFLADO\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const pressureCounts = {};
    prueba.items.forEach(item => {
      const status = getInspeccionPresionBucket(item);
      pressureCounts[status] = (pressureCounts[status] || 0) + 1;
    });

    const pressureOrder = ['ABAJO DE PRESION', 'EN PRESION', 'ARRIBA DE PRESION', 'VIGIA', 'PRESION REGISTRADA', 'INACCESIBLE', 'SIN DATO'];
    const labels = [...pressureOrder.filter(label => pressureCounts[label]), ...Object.keys(pressureCounts).filter(label => !pressureOrder.includes(label)).sort()];
    const data = labels.map(label => pressureCounts[label]);

    const colorMap = {
      'EN PRESION': '#4CAF50',
      'ARRIBA DE PRESION': '#FF9800',
      'ABAJO DE PRESION': '#F44336',
      'VIGIA': '#7C3AED',
      'INACCESIBLE': '#9E9E9E',
      'PRESION REGISTRADA': '#2196F3',
      'SIN DATO': '#BDBDBD'
    };
    const colors = labels.map(label => colorMap[label] || '#90A4AE');

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const chart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: colors,
          borderWidth: 2,
          borderColor: isDarkTheme ? '#1a1a1a' : '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: labelColor,
              font: { size: 9 },
              padding: 8,
              boxWidth: 12
            }
          },
          tooltip: { enabled: true },
          datalabels: {
            color: '#fff',
            formatter: (value, context) => {
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = ((value / total) * 100).toFixed(1);
              return percentage > 5 ? `${percentage}%` : '';
            },
            font: { size: 10, weight: 'bold' }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderParticipacionMarcaCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `PARTICIPACION POR MARCA\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const marcaCounts = {};
    prueba.items.forEach(item => {
      const marca = item.LlantasMarca || 'Sin marca';
      marcaCounts[marca] = (marcaCounts[marca] || 0) + 1;
    });

    const labels = Object.keys(marcaCounts).sort((a, b) => marcaCounts[b] - marcaCounts[a]);
    const data = labels.map(label => marcaCounts[label]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const colors = [
      '#4CAF50', '#2196F3', '#FFC107', '#FF9800', '#F44336',
      '#9C27B0', '#00BCD4', '#8BC34A', '#FF5722', '#607D8B',
      '#E91E63', '#3F51B5', '#009688', '#CDDC39', '#795548'
    ];

    const chart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: labels.map((_, i) => colors[i % colors.length]),
          borderWidth: 2,
          borderColor: isDarkTheme ? '#1a1a1a' : '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: labelColor,
              font: { size: 9 },
              padding: 8,
              boxWidth: 12,
              generateLabels: function(chart) {
                const data = chart.data;
                const isDark = document.documentElement.classList.contains('dark-theme');
                const textColor = isDark ? '#f5f5f5' : '#111827';
                if (data.labels.length && data.datasets.length) {
                  return data.labels.map((label, i) => {
                    const meta = chart.getDatasetMeta(0);
                    const style = meta.controller.getStyle(i);
                    const truncatedLabel = label.length > 20 ? label.substring(0, 20) + '...' : label;
                    return {
                      text: truncatedLabel,
                      fillStyle: style.backgroundColor,
                      strokeStyle: style.borderColor,
                      lineWidth: style.borderWidth,
                      hidden: !chart.getDataVisibility(i),
                      index: i,
                      fontColor: textColor
                    };
                  });
                }
                return [];
              }
            }
          },
          tooltip: {
            enabled: true,
            callbacks: {
              title: function(context) {
                const index = context[0].dataIndex;
                return labels[index];
              },
              label: function(context) {
                const value = context.parsed || 0;
                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                const percentage = ((value / total) * 100).toFixed(1);
                return `Cantidad: ${value} (${percentage}%)`;
              }
            }
          },
          datalabels: {
            color: '#fff',
            formatter: (value, context) => {
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = ((value / total) * 100).toFixed(1);
              return percentage > 5 ? `${percentage}%` : '';
            },
            font: { size: 10, weight: 'bold' }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderRenovabilidadCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `RENOVABILIDAD\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const counts = { RENOVABLE: 0, 'NO RENOVABLE': 0 };
    prueba.items.forEach(item => {
      const avg = computeAvgMm(item);
      if (avg >= RENOVABLE_MM_THRESHOLD) {
        counts.RENOVABLE += 1;
      } else {
        counts['NO RENOVABLE'] += 1;
      }
    });

    const labels = Object.keys(counts);
    const data = labels.map(label => counts[label]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const chart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: ['#4CAF50', '#F44336'],
          borderWidth: 2,
          borderColor: isDarkTheme ? '#1a1a1a' : '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: labelColor,
              font: { size: 9 },
              padding: 8,
              boxWidth: 12
            }
          },
          tooltip: { enabled: true },
          datalabels: {
            color: '#fff',
            formatter: (value, context) => {
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = ((value / total) * 100).toFixed(1);
              return percentage > 5 ? `${percentage}%` : '';
            },
            font: { size: 10, weight: 'bold' }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderRemanentesMmCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `REMANENTES EN MM\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const mmCounts = {};
    prueba.items.forEach(item => {
      const avg = computeAvgMm(item);
      const bucket = Number.isFinite(avg) ? Math.round(avg) : 0;
      const label = `${bucket}`;
      mmCounts[label] = (mmCounts[label] || 0) + 1;
    });

    const labels = Object.keys(mmCounts).sort((a, b) => Number(a) - Number(b));
    const data = labels.map(label => mmCounts[label]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';
    const colors = ['#F44336', '#FF9800', '#FFC107', '#4CAF50', '#2196F3'];

    const chart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Cantidad',
          data: data,
          backgroundColor: labels.map((_, i) => colors[i % colors.length]),
          borderWidth: 1,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { enabled: true },
          datalabels: {
            color: '#000',
            anchor: 'end',
            align: 'top',
            formatter: (value) => value,
            font: { size: 10, weight: 'bold' }
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { color: labelColor, stepSize: 1, font: { size: 10 } },
            grid: { color: isDarkTheme ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)' }
          },
          x: {
            ticks: { color: labelColor, font: { size: 9 } },
            grid: { display: false }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderDesgastesIrregularesCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `DESGASTES IRREGULARES\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const counts = {};
    prueba.items.forEach(item => {
      const status = normalizeDesgasteStatus(item.LlantasInspeccionDesgaste);
      counts[status] = (counts[status] || 0) + 1;
    });

    const labels = Object.keys(counts);
    const data = labels.map(label => counts[label]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const chart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: ['#F44336', '#4CAF50', '#9E9E9E'],
          borderWidth: 2,
          borderColor: isDarkTheme ? '#1a1a1a' : '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: labelColor,
              font: { size: 9 },
              padding: 8,
              boxWidth: 12
            }
          },
          tooltip: { enabled: true },
          datalabels: {
            color: '#fff',
            formatter: (value, context) => {
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = ((value / total) * 100).toFixed(1);
              return percentage > 5 ? `${percentage}%` : '';
            },
            font: { size: 10, weight: 'bold' }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderVehiculosInspeccionadosCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });

    const title = document.createElement('h3');
    title.textContent = `VEHICULOS INSPECCIONADOS\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const statsDiv = document.createElement('div');
    statsDiv.style.padding = '4px';
    statsDiv.style.flex = '1';
    statsDiv.style.display = 'flex';
    statsDiv.style.flexDirection = 'column';
    statsDiv.style.gap = '5px';
    statsDiv.style.justifyContent = 'center';
    statsDiv.style.overflow = 'hidden';

    const totalLlantas = prueba.items.length;
    const vehiculosSet = new Set(prueba.items.map(item => item.VehiculoInspeccionNo || item.vehiculosinspeccion_idVehiculoInspeccion));
    const totalVehiculos = vehiculosSet.size;
    const mmPromedio = totalLlantas > 0
      ? (prueba.items.reduce((acc, item) => acc + computeAvgMm(item), 0) / totalLlantas).toFixed(2)
      : '0.00';

    const vehiculosUnicos = new Map();
    prueba.items.forEach((item) => {
      const vehiculoKey = String(item.VehiculoInspeccionNo || item.vehiculosinspeccion_idVehiculoInspeccion || item.Vehiculos_idVehiculos || '').trim();
      if (!vehiculoKey || vehiculosUnicos.has(vehiculoKey)) {
        return;
      }
      vehiculosUnicos.set(vehiculoKey, String(item.TipoVehiculosNombre || 'Sin tipo').trim() || 'Sin tipo');
    });

    const tipoVehiculoCounts = {};
    vehiculosUnicos.forEach((tipo) => {
      tipoVehiculoCounts[tipo] = (tipoVehiculoCounts[tipo] || 0) + 1;
    });

    const tipoVehiculoIconMap = new Map();
    Object.keys(tipoVehiculoCounts)
      .sort((a, b) => a.localeCompare(b, 'es-MX'))
      .forEach((tipo, index) => {
        tipoVehiculoIconMap.set(tipo, index);
      });

    const tiposVehiculoLegendHtml = Object.keys(tipoVehiculoCounts)
      .sort((a, b) => a.localeCompare(b, 'es-MX'))
      .map((tipo) => `
        <div class="tipos-vehiculo-legend-item">
          <span class="tipos-vehiculo-legend-icon">${getTipoVehiculoIcon(tipo, tipoVehiculoIconMap.get(tipo))}</span>
          <span class="tipos-vehiculo-legend-label">${escapeHtml(tipo)}</span>
        </div>
      `)
      .join('') || '<div class="tipos-vehiculo-legend-empty">Sin datos de tipo</div>';

    const tiposVehiculoHtml = Object.entries(tipoVehiculoCounts)
      .sort((a, b) => b[1] - a[1])
      .map(([tipo, count]) => `
        <div class="tipo-vehiculo-item" aria-label="${escapeHtml(tipo)}: ${count}">
          <span class="tipo-vehiculo-icon">${getTipoVehiculoIcon(tipo, tipoVehiculoIconMap.get(tipo))}</span>
          <span class="tipo-vehiculo-count">${count}</span>
        </div>
      `)
      .join('') || '<div class="tipo-vehiculo-empty">Sin datos de tipo</div>';

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const textColor = isDarkTheme ? '#f5f5f5' : '#111827';

    statsDiv.innerHTML = `
      <div style="text-align: center; padding: 6px 8px; background: rgba(239, 68, 68, 0.1); border-radius: 6px; border-left: 3px solid var(--line-red);">
        <div style="font-size: 18px; font-weight: bold; color: var(--line-red); line-height: 1.2;">${totalVehiculos}</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">TOTAL VEHICULOS</div>
      </div>
      <div style="text-align: center; padding: 6px 8px; background: rgba(33, 150, 243, 0.1); border-radius: 6px; border-left: 3px solid #2196F3;">
        <div style="font-size: 18px; font-weight: bold; color: #2196F3; line-height: 1.2;">${totalLlantas}</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">TOTAL LLANTAS</div>
      </div>
      <div style="text-align: center; padding: 6px 8px; background: rgba(76, 175, 80, 0.1); border-radius: 6px; border-left: 3px solid #4CAF50;">
        <div style="font-size: 18px; font-weight: bold; color: #4CAF50; line-height: 1.2;">${mmPromedio} mm</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">MM PROMEDIO</div>
      </div>
      <div class="tipos-vehiculo-card" style="text-align: left; padding: 6px 8px; background: rgba(255, 152, 0, 0.1); border-radius: 6px; border-left: 3px solid #FF9800;">
        <div style="font-size: 9px; color: ${textColor}; margin-bottom: 4px; line-height: 1.1; text-transform: uppercase; font-weight: 700;">TIPOS DE VEHICULO</div>
        <div class="tipos-vehiculo-legend-popup" role="tooltip">
          <div class="tipos-vehiculo-legend-title">Tipos de vehiculo</div>
          ${tiposVehiculoLegendHtml}
        </div>
        <div class="tipos-vehiculo-list">${tiposVehiculoHtml}</div>
      </div>
    `;

    chartCard.appendChild(statsDiv);
    chartsContainer.appendChild(chartCard);
  });
}

function renderTable() {
  const inspeccionList = document.getElementById('inspeccionList');
  if (!inspeccionList) return;

  let dataToDisplay = filteredLlantasInspeccion;
  if (selectedPruebaId !== null) {
    dataToDisplay = filteredLlantasInspeccion.filter(llanta =>
      String(getPruebaId(llanta)) === String(selectedPruebaId)
    );
  }

  if (dataToDisplay.length === 0) {
    inspeccionList.innerHTML = '<p class="loading-text">No hay datos disponibles</p>';
    return;
  }

  const table = document.createElement('table');
  table.className = 'desecho-table';

  table.innerHTML = `
    <thead>
      <tr class="ranuras-header-row">
        <th rowspan="2">#</th>
        <th rowspan="2">NO. VEHICULO</th>
        <th rowspan="2">TIPO</th>
        <th rowspan="2">MARCA</th>
        <th rowspan="2">MODELO</th>
        <th rowspan="2">MEDIDA</th>
        <th rowspan="2">DOT</th>
        <th rowspan="2">PISO</th>
        <th rowspan="2">PSI</th>
        <th rowspan="2">DESGASTE</th>
        <th colspan="4" class="ranuras-title">RANURAS</th>
        <th rowspan="2" class="factor-delta-col">F. Delta</th>
        <th rowspan="2">COND. PELIGROSA</th>
        <th rowspan="2">OBSERVACION</th>
        <th rowspan="2">COMENTARIO</th>
        <th rowspan="2">FOTO(S)</th>
      </tr>
      <tr class="ranuras-subheader-row">
        <th>1</th>
        <th>2</th>
        <th>3</th>
        <th>4</th>
      </tr>
    </thead>
    <tbody>
      ${dataToDisplay.map((llanta, index) => {
        const hasPhoto = Boolean(llanta.LlantasInspeccionFoto || llanta.LlantasInspeccionFoto2);
        const buttonClass = hasPhoto ? 'foto-btn has-photo' : 'foto-btn';
        const title = hasPhoto ? 'Ver fotos' : 'Sin fotos';
        const originalIndex = filteredLlantasInspeccion.indexOf(llanta);
        const factorDel = computeFactorDel(llanta);
        const factorDelText = Number.isFinite(factorDel) ? String(Math.round(factorDel)) : '-';
        const condPelFlag = toBooleanFlag(llanta.LlantasInspeccionCondPel);
        const condPelCheckbox = `<input type="checkbox" class="danger-checkbox" disabled ${condPelFlag ? 'checked' : ''} aria-label="Condición peligrosa" />`;
        const presionCellClass = getInspeccionPresionClass(llanta);
        return `
        <tr class="${condPelFlag ? 'row-condicion-peligrosa' : ''}">
          <td>${index + 1}</td>
          <td>${llanta.VehiculoInspeccionNo || '-'}</td>
          <td>${llanta.TipoVehiculosNombre || '-'}</td>
          <td>${llanta.LlantasMarca || '-'}</td>
          <td>${llanta.LlantasModelo || '-'}</td>
          <td>${llanta.LlantasMedida || '-'}</td>
          <td>${llanta.LlantasInspeccionDOT || '-'}</td>
          <td>${llanta.LlantasInspeccionPiso || '-'}</td>
          <td class="${presionCellClass}">${llanta.LlantasInspeccionPresion ?? '-'}</td>
          <td>${llanta.LlantasInspeccionDesgaste || '-'}</td>
          <td>${llanta.LlantasInspeccionMm1 ?? '-'}</td>
          <td>${llanta.LlantasInspeccionMm2 ?? '-'}</td>
          <td>${llanta.LlantasInspeccionMm3 ?? '-'}</td>
          <td>${llanta.LlantasInspeccionMm4 ?? '-'}</td>
          <td class="factor-delta-col">${factorDelText}</td>
          <td class="danger-cell">${condPelCheckbox}</td>
          <td>${llanta.LlantasInspeccionObservacion || '-'}</td>
          <td>${llanta.LlantasInspeccionComentario || '-'}</td>
          <td>
            <button class="${buttonClass}" title="${title}" data-index="${originalIndex}" type="button">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
              </svg>
            </button>
          </td>
        </tr>
      `;
      }).join('')}
    </tbody>
  `;

  inspeccionList.innerHTML = '';
  inspeccionList.appendChild(table);
}

inspeccionListContainer?.addEventListener('click', (event) => {
  const button = event.target.closest('.foto-btn');
  if (!button) {
    return;
  }
  const index = Number(button.dataset.index);
  if (!Number.isFinite(index)) {
    return;
  }
  const llanta = filteredLlantasInspeccion[index];
  if (!llanta) {
    return;
  }
  const hasPhoto = Boolean(llanta.LlantasInspeccionFoto || llanta.LlantasInspeccionFoto2);
  if (!hasPhoto) {
    return;
  }
  openPhotoModal(llanta.LlantasInspeccionFoto, llanta.LlantasInspeccionFoto2);
});

function updateTotalCount() {
  const totalElement = document.getElementById('totalLlantas');
  if (!totalElement) return;

  let count = filteredLlantasInspeccion.length;
  if (selectedPruebaId !== null) {
    count = filteredLlantasInspeccion.filter(llanta =>
      String(getPruebaId(llanta)) === String(selectedPruebaId)
    ).length;
  }
  totalElement.textContent = `Total de llantas analizadas: ${count}`;
}

function exportVisibleTableToExcel(containerId, fileNamePrefix) {
  const container = document.getElementById(containerId);
  const table = container?.querySelector('table');

  if (!table) {
    alert('No hay tabla disponible para exportar');
    return;
  }

  const clonedTable = table.cloneNode(true);
  clonedTable.querySelectorAll('button').forEach((button) => {
    const cell = button.closest('td');
    if (cell) {
      const buttonText = (button.textContent || '').trim();
      cell.textContent = buttonText || '-';
    }
  });

  const html = `
    <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
      <head>
        <meta charset="utf-8" />
      </head>
      <body>
        ${clonedTable.outerHTML}
      </body>
    </html>
  `;

  const blob = new Blob(['\ufeff', html], { type: 'application/vnd.ms-excel;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');

  link.href = url;
  link.download = `${fileNamePrefix}_${stamp}.xls`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function getVisibleTableHtml(containerId) {
  const container = document.getElementById(containerId);
  const table = container?.querySelector('table');
  if (!table) {
    return '';
  }

  const clonedTable = table.cloneNode(true);
  clonedTable.querySelectorAll('button').forEach((button) => {
    const cell = button.closest('td');
    if (cell) {
      const buttonText = (button.textContent || '').trim();
      cell.textContent = buttonText || '-';
    }
  });

  return clonedTable.outerHTML;
}

function getVisibleTableData(containerId) {
  const container = document.getElementById(containerId);
  const table = container?.querySelector('table');
  if (!table) {
    return { headers: [], rows: [] };
  }

  const normalizeText = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const headerCells = Array.from(table.querySelectorAll('thead tr th'));
  const headers = headerCells.map((cell) => normalizeText(cell.textContent));

  const bodyRows = Array.from(table.querySelectorAll('tbody tr'));
  const rows = bodyRows.map((row) => {
    const cells = Array.from(row.querySelectorAll('td, th'));
    return cells.map((cell) => normalizeText(cell.textContent));
  }).filter((row) => row.some((value) => value));

  return { headers, rows };
}

async function sendVisibleTableByEmail(containerId, viewName) {
  const emailInput = document.getElementById('emailInput');
  const btnEnviarEmail = document.getElementById('btnEnviarEmail');
  const rawEmails = (emailInput?.value || '').trim();

  if (!rawEmails) {
    alert('Por favor ingresa al menos un email');
    return;
  }

  const emails = rawEmails
    .split(',')
    .map((email) => email.trim())
    .filter(Boolean);

  const previousText = btnEnviarEmail?.textContent;
  if (btnEnviarEmail) {
    btnEnviarEmail.disabled = true;
    btnEnviarEmail.textContent = 'Enviando...';
  }

  try {
    const flota = getCurrentFlotaContext() || localStorage.getItem('selectedFlotaForInspeccion') || '';
    const referencia = (function(){
      const m = ['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
      const d = new Date();
      return `${m[d.getMonth()].toUpperCase()} ${d.getFullYear()}`;
    })();
    const link = `${window.location.origin}${window.location.pathname}?flotaName=${encodeURIComponent(flota)}`;

    const response = await fetch('/api/reports/send-link-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ emails, viewName, link, flota, referencia })
    });

    const result = await response.json();
    if (!response.ok) throw new Error(result.message || 'No se pudo enviar el correo');

    alert('Correo enviado correctamente');
    if (emailInput) emailInput.value = '';
  } catch (error) {
    console.error('Error enviando correo:', error);
    alert(error.message || 'Error al enviar correo');
  } finally {
    if (btnEnviarEmail) {
      btnEnviarEmail.disabled = false;
      btnEnviarEmail.textContent = previousText || 'Enviar';
    }
  }
}

function setActiveButton(activeButtonId) {
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  const button = document.getElementById(activeButtonId);
  if (button) {
    button.classList.add('active');
  }
}

// Button event listeners
const btnPresionesInflado = document.getElementById('btnPresionesInflado');
const btnParticipacionMarca = document.getElementById('btnParticipacionMarca');
const btnRenovabilidad = document.getElementById('btnRenovabilidad');
const btnRemanentesMm = document.getElementById('btnRemanentesMm');
const btnDesgastesIrregulares = document.getElementById('btnDesgastesIrregulares');
const btnVehiculosInspeccionados = document.getElementById('btnVehiculosInspeccionados');

btnPresionesInflado?.addEventListener('click', () => {
  currentView = 'PRESIONES_INFLADO';
  setActiveButton('btnPresionesInflado');
  renderCharts();
});

btnParticipacionMarca?.addEventListener('click', () => {
  currentView = 'PARTICIPACION_MARCA';
  setActiveButton('btnParticipacionMarca');
  renderCharts();
});

btnRenovabilidad?.addEventListener('click', () => {
  currentView = 'RENOVABILIDAD';
  setActiveButton('btnRenovabilidad');
  renderCharts();
});

btnRemanentesMm?.addEventListener('click', () => {
  currentView = 'REMANENTES_MM';
  setActiveButton('btnRemanentesMm');
  renderCharts();
});

btnDesgastesIrregulares?.addEventListener('click', () => {
  currentView = 'DESGASTES_IRREGULARES';
  setActiveButton('btnDesgastesIrregulares');
  renderCharts();
});

const btnExportarExcel = document.getElementById('btnExportarExcel');
btnExportarExcel?.addEventListener('click', () => {
  exportVisibleTableToExcel('inspeccionList', 'inspeccion');
});

const btnEnviarEmail = document.getElementById('btnEnviarEmail');
btnEnviarEmail?.addEventListener('click', () => {
  sendVisibleTableByEmail('inspeccionList', 'Inspeccion');
});

btnVehiculosInspeccionados?.addEventListener('click', () => {
  currentView = 'VEHICULOS_INSPECCIONADOS';
  setActiveButton('btnVehiculosInspeccionados');
  renderCharts();
});

async function init() {
  const [llantasInspeccion, pruebasInspeccion] = await Promise.all([
    fetchLlantasInspeccion(),
    fetchPruebasInspeccion()
  ]);
  allLlantasInspeccion = llantasInspeccion;
  allPruebasInspeccion = pruebasInspeccion;
  applyFlotaFilter();
  renderCharts();
  renderTable();
  updateTotalCount();
}

init();
