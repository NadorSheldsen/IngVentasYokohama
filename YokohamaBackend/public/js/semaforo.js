// semaforo.js - Logica para la pagina de semaforos

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

updateThemeIcon();
updateLogo();
const user = getStoredUser();
if (user) {
  renderUserName(user);
}
initializeUserMenu();

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

let allLlantasSemaforo = [];
let allPruebasSemaforo = [];
let filteredLlantasSemaforo = [];
let currentView = 'ALERTA_RETIRO';
let currentCharts = [];
let selectedPruebaId = null;

const NAV_FLOTA_KEYS = {
  'desecho.html': 'selectedFlotaForDesecho',
  'semaforo.html': 'selectedFlotaForSemaforo',
  'inspeccion.html': 'selectedFlotaForInspeccion',
  'rendimientos.html': 'selectedFlotaForRendimientos'
};

const photoModal = document.getElementById('photoModal');
const btnClosePhotoModal = document.getElementById('btnClosePhotoModal');
const photoModalImg1 = document.getElementById('photoModalImg1');
const photoModalImg2 = document.getElementById('photoModalImg2');
const photoModalEmpty1 = document.getElementById('photoModalEmpty1');
const photoModalEmpty2 = document.getElementById('photoModalEmpty2');
const semaforoListContainer = document.getElementById('semaforoList');

function getCurrentFlotaContext() {
  const uniqueFlotas = Array.from(new Set(
    filteredLlantasSemaforo
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
  if (!value) return '';
  const stringValue = String(value).trim();
  if (!stringValue) return '';

  if (stringValue.startsWith('data:image')) {
    return stringValue;
  }

  try {
    const testDecode = atob(stringValue.substring(0, 40));
    if (testDecode.startsWith('/9j/')) {
      return `data:image/jpeg;base64,${atob(stringValue)}`;
    }
  } catch (_) {
    // ignore
  }

  return `data:image/jpeg;base64,${stringValue}`;
}

function setModalImage(targetImg, targetEmpty, src) {
  if (!targetImg || !targetEmpty) return;
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
  setModalImage(photoModalImg1, photoModalEmpty1, getPhotoSrc(foto1));
  setModalImage(photoModalImg2, photoModalEmpty2, getPhotoSrc(foto2));
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
  if (event.target === photoModal) closePhotoModal();
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closePhotoModal();
});

async function fetchLlantasSemaforo() {
  try {
    const response = await fetch('/api/llantas-semaforo');
    if (!response.ok) throw new Error('Error fetching llantas semaforo');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Error fetching semaforo:', error);
    return [];
  }
}

async function fetchPruebasSemaforo() {
  try {
    let url = '/api/pruebas-semaforo';
    const savedFlota = localStorage.getItem('selectedFlotaForSemaforo') || localStorage.getItem('selectedFlotaContext');
    if (savedFlota) {
      url += '?flotaName=' + encodeURIComponent(savedFlota);
    }
    const response = await fetch(url);
    if (!response.ok) throw new Error('Error fetching pruebas semaforo');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Error fetching pruebas semaforo:', error);
    return [];
  }
}

function applyFlotaFilter() {
  const savedFlota = localStorage.getItem('selectedFlotaForSemaforo') || localStorage.getItem('selectedFlotaContext');
  if (savedFlota) {
    const normalizedSavedFlota = String(savedFlota).trim().toLowerCase();
    const matchingPruebaIds = new Set(
      allPruebasSemaforo
        .filter((prueba) => String(prueba?.FlotasNombre || '').trim().toLowerCase().includes(normalizedSavedFlota))
        .map((prueba) => String(prueba?.idPruebasSemaforo || '').trim())
        .filter(Boolean)
    );

    filteredLlantasSemaforo = allLlantasSemaforo.filter((llanta) => {
      const llantaFlota = String(llanta?.FlotasNombre || '').trim().toLowerCase();
      const pruebaId = String(llanta?.PruebasSemaforo_idPruebasSemaforo || '').trim();
      return (llantaFlota && llantaFlota.includes(normalizedSavedFlota)) || matchingPruebaIds.has(pruebaId);
    });

    localStorage.setItem('selectedFlotaContext', savedFlota);
    localStorage.removeItem('selectedFlotaForSemaforo');
  } else {
    filteredLlantasSemaforo = [...allLlantasSemaforo];
  }
}

function getPruebaId(llanta) {
  return llanta?.PruebasSemaforo_idPruebasSemaforo ?? null;
}

function getPruebaFecha(llanta) {
  if (!llanta?.PruebasSemaforoFecha) return 'Sin fecha';
  return new Date(llanta.PruebasSemaforoFecha).toLocaleDateString('es-MX');
}

function getPruebaNombreById(pruebaId) {
  const targetId = String(pruebaId || '').trim();
  if (!targetId) return '';

  const prueba = allPruebasSemaforo.find(
    (item) => String(item?.idPruebasSemaforo || '').trim() === targetId
  );

  return String(
    prueba?.PruebasSemaforoTitulo || prueba?.PruebasSemaforoNombre || prueba?.PruebasSemaforoTituloNombre || ''
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

function colorToAlerta(color) {
  const value = String(color || '').toLowerCase();
  if (value.includes('verde')) return 'CORRECTO MM';
  if (value.includes('amarillo')) return 'MEDIO MM';
  if (value.includes('rojo')) return 'BAJO MM';
  return color ? String(color).toUpperCase() : 'SIN DATO';
}

function alertaRetiroClass(alertaText) {
  const value = String(alertaText || '').toUpperCase();
  if (value.includes('CORRECTO')) return 'alerta-retiro alerta-retiro-verde';
  if (value.includes('MEDIO')) return 'alerta-retiro alerta-retiro-amarillo';
  if (value.includes('BAJO')) return 'alerta-retiro alerta-retiro-rojo';
  return 'alerta-retiro';
}

function normalizePresion(value) {
  if (value === null || value === undefined || value === '') return 'SIN DATO';
  const raw = String(value).trim();
  const lower = raw.toLowerCase();
  if (lower.includes('arriba')) return 'ARRIBA DE PRESION';
  if (lower.includes('abajo')) return 'ABAJO DE PRESION';
  if (lower.includes('en presion') || lower.includes('en presión') || lower.includes('ok')) return 'EN PRESION';
  if (lower.includes('inaccesible')) return 'INACCESIBLE';
  if (Number.isFinite(parseFloat(raw))) return 'PSI REGISTRADA';
  return raw.toUpperCase();
}

function getSemaforoPresionBucket(llanta) {
  const vigiaFlag = toBooleanFlag(llanta?.LlantasSemaforoVigia);
  if (vigiaFlag === true) {
    return 'VIGIA';
  }

  const presion = toFiniteNumber(llanta?.LlantasSemaforoPresion);
  const pMin = toFiniteNumber(llanta?.ParametrosPMin);
  const pSug = toFiniteNumber(llanta?.ParametrosPSug);

  if (presion !== null && pMin !== null && pSug !== null) {
    if (presion <= pMin) return 'ABAJO DE PRESION';
    if (presion <= pSug) return 'EN PRESION';
    return 'ARRIBA DE PRESION';
  }

  const normalized = normalizePresion(llanta?.LlantasSemaforoPresion);
  if (normalized === 'ABAJO DE PRESION') return 'ABAJO DE PRESION';
  if (normalized === 'EN PRESION') return 'EN PRESION';
  if (normalized === 'ARRIBA DE PRESION') return 'ARRIBA DE PRESION';
  if (normalized === 'INACCESIBLE') return 'INACCESIBLE';
  if (normalized === 'PSI REGISTRADA') return 'PSI REGISTRADA';
  return 'SIN DATO';
}

const PRESION_CHART_COLORS = {
  'ABAJO DE PRESION': '#F44336',
  'EN PRESION': '#4CAF50',
  'ARRIBA DE PRESION': '#FFC107',
  'VIGIA': '#7C3AED',
  'INACCESIBLE': '#9E9E9E',
  'PSI REGISTRADA': '#2196F3',
  'SIN DATO': '#BDBDBD'
};

const PRESION_CHART_ORDER = [
  'ABAJO DE PRESION',
  'EN PRESION',
  'ARRIBA DE PRESION',
  'VIGIA',
  'PSI REGISTRADA',
  'INACCESIBLE',
  'SIN DATO'
];

function normalizeObservacion(value) {
  if (!value) return 'SIN OBSERVACION';
  return String(value).trim().toUpperCase();
}

function toNumber(value, fallback = 0) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : fallback;
}

function toFiniteNumber(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
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

function getSemaforoPresionClass(llanta) {
  const vigiaFlag = toBooleanFlag(llanta?.LlantasSemaforoVigia);
  if (vigiaFlag === true) {
    return 'presion-valor presion-vigia';
  }

  const presion = toFiniteNumber(llanta?.LlantasSemaforoPresion);
  const pMin = toFiniteNumber(llanta?.ParametrosPMin);
  const pSug = toFiniteNumber(llanta?.ParametrosPSug);

  if (presion !== null && pMin !== null && pSug !== null) {
    if (presion <= pMin) return 'presion-valor presion-rojo';
    if (presion <= pSug) return 'presion-valor presion-verde';
    return 'presion-valor presion-amarillo';
  }

  const normalized = normalizePresion(llanta?.LlantasSemaforoPresion);
  if (normalized === 'ABAJO DE PRESION') return 'presion-valor presion-rojo';
  if (normalized === 'EN PRESION') return 'presion-valor presion-verde';
  if (normalized === 'ARRIBA DE PRESION') return 'presion-valor presion-amarillo';

  return 'presion-valor';
}

function buildPosicionMapByVehiculo(llantas) {
  const llantasByVehiculo = new Map();

  llantas.forEach((llanta) => {
    const vehiculoKey = String(
      llanta?.VehiculoSemaforo_idVehiculoSemaforo
      ?? llanta?.VehiculoSemaforoNo
      ?? 'sin-vehiculo'
    );

    if (!llantasByVehiculo.has(vehiculoKey)) {
      llantasByVehiculo.set(vehiculoKey, []);
    }

    llantasByVehiculo.get(vehiculoKey).push(llanta);
  });

  const positionMap = new Map();

  llantasByVehiculo.forEach((items) => {
    items
      .slice()
      .sort((a, b) => toNumber(a?.idLlantasSemaforo, 0) - toNumber(b?.idLlantasSemaforo, 0))
      .forEach((item, index) => {
        const itemKey = `${String(item?.idLlantasSemaforo ?? '')}::${String(item?.VehiculoSemaforo_idVehiculoSemaforo ?? item?.VehiculoSemaforoNo ?? '')}`;
        positionMap.set(itemKey, index + 1);
      });
  });

  return positionMap;
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

function buildPruebaGroups() {
  const dataByPrueba = {};
  filteredLlantasSemaforo.forEach(llanta => {
    const pruebaId = getPruebaId(llanta);
    if (!pruebaId) return;

    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: getPruebaFecha(llanta),
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

function createDoughnutChart(canvas, labels, data, colors, labelColor, isDarkTheme) {
  return new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data,
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
}

function renderChartsByCounter(titlePrefix, keySelector, colorPalette) {
  const chartsContainer = document.getElementById('chartsContainer');
  const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;

    if (selectedPruebaId === pruebaId) chartCard.classList.add('selected');

    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => selectChart(pruebaId));

    const title = document.createElement('h3');
    title.textContent = `${titlePrefix}\n${prueba.fecha}`;
    chartCard.appendChild(title);

    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const counter = {};
    prueba.items.forEach(item => {
      const key = keySelector(item);
      counter[key] = (counter[key] || 0) + 1;
    });

    const labels = Object.keys(counter).sort((a, b) => counter[b] - counter[a]);
    const data = labels.map(label => counter[label]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';
    const colors = labels.map((label, i) => {
      if (!Array.isArray(colorPalette) && typeof colorPalette === 'object') {
        return colorPalette[label] || '#9E9E9E';
      }
      return colorPalette[i % colorPalette.length];
    });

    const chart = createDoughnutChart(canvas, labels, data, colors, labelColor, isDarkTheme);
    currentCharts.push(chart);
  });
}

function renderCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  if (!chartsContainer) return;

  currentCharts.forEach(chart => chart.destroy());
  currentCharts = [];
  chartsContainer.innerHTML = '';

  if (currentView === 'ALERTA_RETIRO') {
    renderChartsByCounter('ALERTA DE RETIRO', item => colorToAlerta(item.LlantasSemaforoColor), {
      'CORRECTO MM': '#4CAF50',
      'MEDIO MM': '#FFC107',
      'BAJO MM': '#F44336',
      'SIN DATO': '#9E9E9E'
    });
  } else if (currentView === 'PRESIONES_INFLADO') {
    const chartsContainer = document.getElementById('chartsContainer');
    const { dataByPrueba, sortedPruebas } = buildPruebaGroups();

    sortedPruebas.forEach(pruebaId => {
      const prueba = dataByPrueba[pruebaId];
      const chartCard = document.createElement('div');
      chartCard.className = 'chart-card';
      chartCard.dataset.pruebaId = pruebaId;

      if (selectedPruebaId === pruebaId) chartCard.classList.add('selected');

      chartCard.style.cursor = 'pointer';
      chartCard.addEventListener('click', () => selectChart(pruebaId));

      const title = document.createElement('h3');
      title.textContent = `PRESIONES DE INFLADO\n${prueba.fecha}`;
      chartCard.appendChild(title);

      const canvas = document.createElement('canvas');
      chartCard.appendChild(canvas);
      chartsContainer.appendChild(chartCard);

      const pressureCounts = {};
      prueba.items.forEach(item => {
        const label = getSemaforoPresionBucket(item);
        pressureCounts[label] = (pressureCounts[label] || 0) + 1;
      });

      const labels = PRESION_CHART_ORDER.filter(label => pressureCounts[label]);
      const extraLabels = Object.keys(pressureCounts).filter(label => !PRESION_CHART_ORDER.includes(label)).sort();
      const finalLabels = [...labels, ...extraLabels];
      const data = finalLabels.map(label => pressureCounts[label]);
      const colors = finalLabels.map(label => PRESION_CHART_COLORS[label] || '#90A4AE');

      const isDarkTheme = document.documentElement.classList.contains('dark-theme');
      const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

      const chart = createDoughnutChart(canvas, finalLabels, data, colors, labelColor, isDarkTheme);
      currentCharts.push(chart);
    });
  } else if (currentView === 'OBSERVACIONES') {
    renderChartsByCounter('OBSERVACIONES', item => normalizeObservacion(item.LlantasSemaforoObserv), ['#90CAF9', '#A5D6A7', '#FFCC80', '#CE93D8', '#EF9A9A', '#B0BEC5']);
  } else if (currentView === 'TIPO_PISO') {
    renderChartsByCounter('TIPO DE PISO', item => (item.LlantasSemaforoPiso || 'SIN DATO').toUpperCase(), ['#90CAF9', '#FFCC80', '#A5D6A7', '#F48FB1', '#B39DDB']);
  } else if (currentView === 'MARCA_MODELO') {
    renderChartsByCounter('MARCA / MODELO', item => `${item.LlantasMarca || 'SIN MARCA'} / ${item.LlantasModelo || 'SIN MODELO'}`.toUpperCase(), ['#8BC34A', '#90CAF9', '#FFD54F', '#FF8A65', '#4DB6AC', '#BA68C8']);
  } else if (currentView === 'MEDIDAS') {
    renderChartsByCounter('MEDIDAS', item => (item.LlantasMedida || 'SIN MEDIDA').toUpperCase(), ['#90CAF9', '#A5D6A7', '#FFE082', '#FFAB91', '#CE93D8']);
  }

  enrichChartTitlesWithPruebaName();
}

function renderTable() {
  const semaforoList = document.getElementById('semaforoList');
  if (!semaforoList) return;

  let dataToDisplay = filteredLlantasSemaforo;
  if (selectedPruebaId !== null) {
    dataToDisplay = filteredLlantasSemaforo.filter((llanta) => String(getPruebaId(llanta)) === String(selectedPruebaId));
  }

  if (dataToDisplay.length === 0) {
    semaforoList.innerHTML = '<p class="loading-text">No hay datos disponibles</p>';
    return;
  }

  const positionMap = buildPosicionMapByVehiculo(dataToDisplay);

  const table = document.createElement('table');
  table.className = 'desecho-table';

  table.innerHTML = `
    <thead>
      <tr>
        <th>#</th>
        <th>NO. VEHÍCULO</th>
        <th>TIPO VEHÍCULO</th>
        <th>POSICIÓN</th>
        <th>MARCA</th>
        <th>MODELO</th>
        <th>MEDIDA</th>
        <th>TIPO PISO</th>
        <th>ALERTA DE RETIRO</th>
        <th>PRESIÓN</th>
        <th>VIGÍA</th>
        <th>CONDICIÓN PELIGROSA</th>
        <th>OBSERVACIONES</th>
        <th>FOTO(S)</th>
        <th>COMENTARIOS</th>
      </tr>
    </thead>
    <tbody>
      ${dataToDisplay.map((llanta, index) => {
        const hasPhoto = Boolean(llanta.LlantasSemaforoFoto1 || llanta.LlantasSemaforoFoto2);
        const buttonClass = hasPhoto ? 'foto-btn has-photo' : 'foto-btn';
        const title = hasPhoto ? 'Ver fotos' : 'Sin fotos';
        const originalIndex = filteredLlantasSemaforo.indexOf(llanta);
        const condPelFlag = toBooleanFlag(llanta.LlantasSemaforoCondPel);
        const condPelCheckbox = `<input type="checkbox" class="danger-checkbox" disabled ${condPelFlag ? 'checked' : ''} aria-label="Condición peligrosa" />`;
        const vigiaFlag = toBooleanFlag(llanta.LlantasSemaforoVigia);
        const vigia = vigiaFlag === true ? 'SI' : vigiaFlag === false ? 'NO' : '-';
        const llantaKey = `${String(llanta?.idLlantasSemaforo ?? '')}::${String(llanta?.VehiculoSemaforo_idVehiculoSemaforo ?? llanta?.VehiculoSemaforoNo ?? '')}`;
        const posicionCorrelativa = positionMap.get(llantaKey) || '-';
        const alertaRetiro = colorToAlerta(llanta.LlantasSemaforoColor);
        const alertaRetiroCellClass = alertaRetiroClass(alertaRetiro);
        const presionCellClass = getSemaforoPresionClass(llanta);

        return `
          <tr class="${condPelFlag ? 'row-condicion-peligrosa' : ''}">
            <td>${index + 1}</td>
            <td>${llanta.VehiculoSemaforoNo || '-'}</td>
            <td>${llanta.TipoVehiculosNombre || '-'}</td>
            <td>${posicionCorrelativa}</td>
            <td>${llanta.LlantasMarca || '-'}</td>
            <td>${llanta.LlantasModelo || '-'}</td>
            <td>${llanta.LlantasMedida || '-'}</td>
            <td>${llanta.LlantasSemaforoPiso || '-'}</td>
            <td class="${alertaRetiroCellClass}">${alertaRetiro}</td>
            <td class="${presionCellClass}">${llanta.LlantasSemaforoPresion ?? '-'}</td>
            <td>${vigia}</td>
            <td class="danger-cell">${condPelCheckbox}</td>
            <td>${llanta.LlantasSemaforoObserv || '-'}</td>
            <td>
              <button class="${buttonClass}" title="${title}" data-index="${originalIndex}" type="button">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
                </svg>
              </button>
            </td>
            <td>${llanta.LlantasSemaforoComent || '-'}</td>
          </tr>
        `;
      }).join('')}
    </tbody>
  `;

  semaforoList.innerHTML = '';
  semaforoList.appendChild(table);
}

semaforoListContainer?.addEventListener('click', (event) => {
  const photoButton = event.target.closest('.foto-btn.has-photo');
  if (!photoButton) return;

  const index = Number(photoButton.dataset.index);
  if (!Number.isFinite(index)) return;

  const llanta = filteredLlantasSemaforo[index];
  if (!llanta) return;

  openPhotoModal(llanta.LlantasSemaforoFoto1, llanta.LlantasSemaforoFoto2);
});

function updateTotalCount() {
  const totalElement = document.getElementById('totalLlantas');
  if (!totalElement) return;

  let count = filteredLlantasSemaforo.length;
  if (selectedPruebaId !== null) {
    count = filteredLlantasSemaforo.filter((llanta) => String(getPruebaId(llanta)) === String(selectedPruebaId)).length;
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
    // Build a link to the same page including the selected flota context
    const flota = getCurrentFlotaContext() || localStorage.getItem('selectedFlotaForSemaforo') || '';
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
  if (button) button.classList.add('active');
}

document.getElementById('btnAlertaRetiro')?.addEventListener('click', () => {
  currentView = 'ALERTA_RETIRO';
  setActiveButton('btnAlertaRetiro');
  renderCharts();
});

document.getElementById('btnPresionesInflado')?.addEventListener('click', () => {
  currentView = 'PRESIONES_INFLADO';
  setActiveButton('btnPresionesInflado');
  renderCharts();
});

document.getElementById('btnObservaciones')?.addEventListener('click', () => {
  currentView = 'OBSERVACIONES';
  setActiveButton('btnObservaciones');
  renderCharts();
});

document.getElementById('btnTipoPiso')?.addEventListener('click', () => {
  currentView = 'TIPO_PISO';
  setActiveButton('btnTipoPiso');
  renderCharts();
});

document.getElementById('btnMarcaModelo')?.addEventListener('click', () => {
  currentView = 'MARCA_MODELO';
  setActiveButton('btnMarcaModelo');
  renderCharts();
});

document.getElementById('btnMedidas')?.addEventListener('click', () => {
  currentView = 'MEDIDAS';
  setActiveButton('btnMedidas');
  renderCharts();
});

const btnExportarExcel = document.getElementById('btnExportarExcel');
btnExportarExcel?.addEventListener('click', () => {
  exportVisibleTableToExcel('semaforoList', 'semaforo');
});

const btnEnviarEmail = document.getElementById('btnEnviarEmail');
btnEnviarEmail?.addEventListener('click', () => {
  sendVisibleTableByEmail('semaforoList', 'Semaforo');
});

async function init() {
  const [llantasSemaforo, pruebasSemaforo] = await Promise.all([
    fetchLlantasSemaforo(),
    fetchPruebasSemaforo()
  ]);
  allLlantasSemaforo = llantasSemaforo;
  allPruebasSemaforo = pruebasSemaforo;
  applyFlotaFilter();
  renderCharts();
  renderTable();
  updateTotalCount();
}

init();
