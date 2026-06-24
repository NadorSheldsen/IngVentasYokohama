function getStoredUser() {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
  const rawUser = localStorage.getItem('user');

  if (!isLoggedIn || !rawUser) {
    window.location.href = 'login.html';
    return null;
  }

  try {
    return JSON.parse(rawUser);
  } catch (error) {
    localStorage.removeItem('isLoggedIn');
    localStorage.removeItem('user');
    window.location.href = 'login.html';
    return null;
  }
}

function applyTheme() {
  const hasManualTheme = localStorage.getItem('theme');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const isDark = hasManualTheme === 'dark' || (!hasManualTheme && prefersDark);

  document.documentElement.classList.toggle('dark-theme', isDark);

  const icon = document.getElementById('themeIcon');
  if (icon) {
    icon.textContent = isDark ? '☀️' : '🌙';
  }

  // Update logo based on theme
  const logo = document.querySelector('.brand-logo');
  if (logo) {
    logo.src = isDark ? '/img/yokohamalogoblanco.png' : '/img/yokohamalogo.png';
  }
}

function toggleTheme() {
  const isDarkNow = document.documentElement.classList.contains('dark-theme');
  localStorage.setItem('theme', isDarkNow ? 'light' : 'dark');
  applyTheme();
  
  // Re-render charts with new theme colors if they exist
  if (allFlotas.length > 0) {
    fetchAndRenderCharts();
  }
}

function renderUserName(user) {
  const nameElement = document.getElementById('userName');
  if (!nameElement) {
    return;
  }

  const value = (user?.UsuariosNombre || '').toString().trim();
  nameElement.textContent = value || 'Usuario';
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

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
  if (!localStorage.getItem('theme')) {
    applyTheme();
  }
});

const user = getStoredUser();
if (user) {
  renderUserName(user);
}
initializeUserMenu();
applyTheme();

// Data Storage
let allFlotas = [];
let allCharts = {};
let selectedFlotaId = null;
let metricsDataset = null;
const fleetIndicators = document.getElementById('fleetIndicators');
const btnAdminIncentivos = document.getElementById('btnAdminIncentivos');
btnAdminIncentivos?.addEventListener('click', () => {
  window.location.href = 'incentivos.html';
});

const btnAdminRendimientos = document.getElementById('btnAdminRendimientos');
btnAdminRendimientos?.addEventListener('click', () => {
  // Clear any saved flota context so header navigation opens rendimientos sin filtro
  try {
    localStorage.removeItem('selectedFlotaForRendimientos');
    localStorage.removeItem('selectedFlotaContext');
  } catch (e) {}
  window.location.href = 'rendimientos.html';
});

function getSelectedFlotaName() {
  if (selectedFlotaId === null) {
    return '';
  }
  const flotaSeleccionada = allFlotas.find((f) => normalizeNumericId(f.idFlotas) === selectedFlotaId);
  return String(flotaSeleccionada?.FlotasNombre || '').trim();
}

function navigateToPageWithSelectedFlota(targetPage, storageKey) {
  const nombreFlota = getSelectedFlotaName();
  if (!nombreFlota) {
    alert('Por favor selecciona una flota primero');
    return;
  }

  localStorage.setItem(storageKey, nombreFlota);
  localStorage.setItem('selectedFlotaContext', nombreFlota);
  window.location.href = targetPage;
}

const btnIndicadorVehiculos = document.getElementById('btnIndicadorVehiculos');
btnIndicadorVehiculos?.addEventListener('click', () => {
  navigateToPageWithSelectedFlota('rendimientos.html', 'selectedFlotaForRendimientos');
});

const btnIndicadorDesecho = document.getElementById('btnIndicadorDesecho');
btnIndicadorDesecho?.addEventListener('click', () => {
  navigateToPageWithSelectedFlota('desecho.html', 'selectedFlotaForDesecho');
});

const btnIndicadorSemaforo = document.getElementById('btnIndicadorSemaforo');
btnIndicadorSemaforo?.addEventListener('click', () => {
  navigateToPageWithSelectedFlota('semaforo.html', 'selectedFlotaForSemaforo');
});

const btnIndicadorInspeccion = document.getElementById('btnIndicadorInspeccion');
btnIndicadorInspeccion?.addEventListener('click', () => {
  navigateToPageWithSelectedFlota('inspeccion.html', 'selectedFlotaForInspeccion');
});


function normalizeNumericId(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function extractFlotaId(record) {
  if (!record || typeof record !== 'object') {
    return null;
  }

  const candidateKeys = [
    'Flotas_idFlotas',
    'idFlotas',
    'flotaId',
    'Flota_idFlota',
    'flotasId',
    'FlotasId'
  ];

  for (const key of candidateKeys) {
    if (Object.prototype.hasOwnProperty.call(record, key)) {
      const parsed = normalizeNumericId(record[key]);
      if (parsed !== null) {
        return parsed;
      }
    }
  }

  return null;
}

function updateFleetIndicators(values, hasSelection) {
  if (!fleetIndicators) {
    return;
  }

  const safeValues = values || {
    inspeccion: null,
    desecho: null,
    semaforo: null,
    vehiculos: null
  };

  fleetIndicators.classList.toggle('is-muted', !hasSelection);

  const indicators = fleetIndicators.querySelectorAll('.fleet-indicator');
  indicators.forEach((item) => {
    const metricKey = item.dataset.metric;
    const countElement = item.querySelector('[data-count]');
    if (!countElement) {
      return;
    }

    const rawValue = safeValues[metricKey];
    countElement.textContent = hasSelection && Number.isFinite(rawValue) ? String(rawValue) : ' ';
  });
}

async function fetchMetricsDataset(force = false) {
  if (metricsDataset && !force) {
    return metricsDataset;
  }

  try {
    const [semaforo, desecho, inspeccion, vehiculos] = await Promise.all([
      fetch('/api/pruebas-semaforo').then((r) => r.json()),
      fetch('/api/pruebas-desecho').then((r) => r.json()),
      fetch('/api/pruebas-inspeccion').then((r) => r.json()),
      fetch('/api/vehiculos').then((r) => r.json())
    ]);

    metricsDataset = {
      semaforo: Array.isArray(semaforo) ? semaforo : [],
      desecho: Array.isArray(desecho) ? desecho : [],
      inspeccion: Array.isArray(inspeccion) ? inspeccion : [],
      vehiculos: Array.isArray(vehiculos) ? vehiculos : []
    };
  } catch (error) {
    console.error('Error fetching fleet indicators dataset:', error);
    metricsDataset = {
      semaforo: [],
      desecho: [],
      inspeccion: [],
      vehiculos: []
    };
  }

  return metricsDataset;
}

async function computeFleetIndicatorCounts(flotaId) {
  if (!metricsDataset) {
    return {
      inspeccion: 0,
      desecho: 0,
      semaforo: 0,
      vehiculos: 0
    };
  }

  const targetFlotaId = normalizeNumericId(flotaId);
  if (targetFlotaId === null) {
    return {
      inspeccion: 0,
      desecho: 0,
      semaforo: 0,
      vehiculos: 0
    };
  }

  const countByFlota = (records) => records.filter((record) => extractFlotaId(record) === targetFlotaId).length;

  const vehiculosCount = metricsDataset.vehiculos.filter((vehiculo) =>
    normalizeNumericId(vehiculo?.Flotas_idFlotas) === targetFlotaId
  ).length;

  return {
    inspeccion: countByFlota(metricsDataset.inspeccion),
    desecho: countByFlota(metricsDataset.desecho),
    semaforo: countByFlota(metricsDataset.semaforo),
    vehiculos: vehiculosCount
  };
}

function getFilteredFlotas() {
  const searchInput = document.getElementById('searchFleet');
  const searchTerm = (searchInput?.value || '').toLowerCase();

  if (!searchTerm) {
    return allFlotas;
  }

  return allFlotas.filter((flota) =>
    (flota.FlotasNombre || '').toLowerCase().includes(searchTerm) ||
    (flota.FlotasClasificacion || '').toLowerCase().includes(searchTerm) ||
    (flota.FlotasZona || '').toLowerCase().includes(searchTerm)
  );
}

async function handleFleetSelection(flotaId) {
  const parsedFlotaId = normalizeNumericId(flotaId);
  if (parsedFlotaId === null) {
    return;
  }

  selectedFlotaId = selectedFlotaId === parsedFlotaId ? null : parsedFlotaId;

  renderFleetList(getFilteredFlotas());

  if (selectedFlotaId === null) {
    updateFleetIndicators(null, false);
    await updatePruebasChartForFlota(null);
    return;
  }

  const selectedName = getSelectedFlotaName();
  if (selectedName) {
    localStorage.setItem('selectedFlotaContext', selectedName);
  }

  await fetchMetricsDataset();
  const counts = await computeFleetIndicatorCounts(selectedFlotaId);
  updateFleetIndicators(counts, true);
  await updatePruebasChartForFlota(selectedFlotaId);
}

// Fetch flotas from API
async function fetchFlotas() {
  try {
    const response = await fetch('/api/flotas');
    if (!response.ok) throw new Error('Error al cargar flotas');
    allFlotas = await response.json();
    renderFleetList(allFlotas);
    await Promise.all([
      fetchAndRenderCharts(),
      fetchMetricsDataset()
    ]);
    updateFleetIndicators(null, false);
  } catch (error) {
    console.error('Error fetching flotas:', error);
    document.getElementById('fleetList').innerHTML = '<p class="loading-text">Error al cargar flotas</p>';
  }
}

// Fetch test counts for charts
async function fetchTestCounts() {
  try {
    const [semaforo, desecho, inspeccion, rendimiento] = await Promise.all([
      fetch('/api/pruebas-semaforo').then(r => r.json()),
      fetch('/api/pruebas-desecho').then(r => r.json()),
      fetch('/api/pruebas-inspeccion').then(r => r.json()),
      fetch('/api/prueba-rendimiento').then(r => r.json())
    ]);

    return {
      Semaforo: semaforo.length || 0,
      Desecho: desecho.length || 0,
      Inspeccion: inspeccion.length || 0,
      Rendimiento: rendimiento.length || 0
    };
  } catch (error) {
    console.error('Error fetching test counts:', error);
    return { Semaforo: 0, Desecho: 0, Inspeccion: 0, Rendimiento: 0 };
  }
}

// Fetch test counts by flota
async function fetchTestCountsByFlota(flotaId) {
  if (!metricsDataset) {
    return {
      Semaforo: 0,
      Desecho: 0,
      Inspeccion: 0,
      Rendimiento: 0
    };
  }

  const targetFlotaId = normalizeNumericId(flotaId);
  if (targetFlotaId === null) {
    return {
      Semaforo: 0,
      Desecho: 0,
      Inspeccion: 0,
      Rendimiento: 0
    };
  }

  // Count by flota
  const countByFlota = (records) => records.filter((record) => extractFlotaId(record) === targetFlotaId).length;

  // Rendimiento = count of vehiculos associated with this flota
  const vehiculosCount = metricsDataset.vehiculos.filter((vehiculo) =>
    normalizeNumericId(vehiculo?.Flotas_idFlotas) === targetFlotaId
  ).length;

  const result = {
    Semaforo: countByFlota(metricsDataset.semaforo),
    Desecho: countByFlota(metricsDataset.desecho),
    Inspeccion: countByFlota(metricsDataset.inspeccion),
    Rendimiento: vehiculosCount
  };
  
  console.log('Test counts by flota', targetFlotaId, ':', result);
  
  return result;
}

function formatChartLabel(label, percentage) {
  const safeLabel = (label || '').toString().trim();
  const suffix = percentage + '%';
  const maxCharsPerLine = 14;

  if (safeLabel.length <= maxCharsPerLine) {
    return safeLabel + ', ' + suffix;
  }

  const midpoint = Math.floor(safeLabel.length / 2);
  let splitIndex = safeLabel.lastIndexOf(' ', midpoint);
  if (splitIndex <= 0) {
    splitIndex = safeLabel.indexOf(' ', midpoint);
  }

  if (splitIndex <= 0) {
    splitIndex = maxCharsPerLine;
  }

  const firstLine = safeLabel.slice(0, splitIndex).trim();
  const secondLineLabel = safeLabel.slice(splitIndex).trim();
  const secondLine = secondLineLabel ? secondLineLabel + ', ' + suffix : suffix;

  return [firstLine, secondLine];
}

// Update Pruebas chart for selected flota or global
async function updatePruebasChartForFlota(flotaId) {
  const chartContainer = document.querySelector('[data-metric="chartPruebas"]')?.closest('.chart-container') || document.getElementById('chartPruebas')?.closest('.chart-container');
  const ctxPruebas = document.getElementById('chartPruebas');
  
  if (!ctxPruebas || !chartContainer) {
    return;
  }

  // Get data based on whether flota is selected
  let testCounts;
  if (flotaId) {
    testCounts = await fetchTestCountsByFlota(flotaId);
    chartContainer.classList.add('chart-enlarged');
  } else {
    testCounts = await fetchTestCounts();
    chartContainer.classList.remove('chart-enlarged');
  }

  // Destroy existing chart
  if (allCharts.pruebas) {
    allCharts.pruebas.destroy();
  }

  // Ensure consistent label order: Semaforo, Desecho, Inspeccion, Rendimiento
  const labelOrder = ['Semaforo', 'Desecho', 'Inspeccion', 'Rendimiento'];
  const labels = labelOrder.filter(label => testCounts.hasOwnProperty(label));
  const data = labels.map(label => testCounts[label] || 0);
  const total = data.reduce((a, b) => a + b, 0);

  // Detect theme for label colors
  const isDarkTheme = document.documentElement.classList.contains('dark-theme');
  const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

  allCharts.pruebas = new Chart(ctxPruebas, {
    type: 'doughnut',
    data: {
      labels: labels,
      datasets: [{
        data: data,
        backgroundColor: ['#ffce56', '#ff6384', '#4bc0c0', '#36a2eb'],
        borderWidth: 2,
        borderColor: '#fff'
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '50%',
      layout: {
        padding: { top: 30, right: 30, bottom: 50, left: 30 }
      },
      plugins: {
        legend: { display: false },
        tooltip: { enabled: true },
        datalabels: {
          color: labelColor,
          display: function(context) {
            return context.dataset.data[context.dataIndex] > 0;
          },
          formatter: (value, ctx) => {
            const percentage = total > 0 ? ((value / total) * 100).toFixed(0) : 0;
            const label = ctx.chart.data.labels[ctx.dataIndex];
            return formatChartLabel(label, percentage);
          },
          font: {
            size: 15,
            weight: 'normal'
          },
          anchor: 'end',
          align: 'end',
          offset: 10,
          clamp: true,
          borderWidth: 1,
          borderColor: function(context) {
            return context.dataset.backgroundColor[context.dataIndex];
          },
          borderRadius: 3,
          backgroundColor: function(context) {
            const alpha = context.active ? 0.9 : 0.7;
            return 'rgba(255, 255, 255, ' + alpha + ')';
          },
          padding: 3
        }
      }
    },
    plugins: [ChartDataLabels]
  });
}

// Render fleet list
function renderFleetList(flotas) {
  const container = document.getElementById('fleetList');
  if (!flotas || flotas.length === 0) {
    container.innerHTML = '<p class="loading-text">No hay flotas disponibles</p>';
    return;
  }

  container.innerHTML = flotas.map(flota => `
    <div class="fleet-item ${selectedFlotaId === normalizeNumericId(flota.idFlotas) ? 'is-selected' : ''}" data-flota-id="${flota.idFlotas}">
      <div class="fleet-name">${flota.FlotasNombre || 'Sin nombre'}</div>
      <div class="fleet-details">
        ${flota.FlotasClasificacion || 'N/A'} - ${flota.FlotasZona || 'N/A'}
      </div>
    </div>
  `).join('');

  container.querySelectorAll('.fleet-item').forEach((item) => {
    item.addEventListener('click', () => {
      handleFleetSelection(item.dataset.flotaId);
    });
  });
}

// Search functionality
document.getElementById('searchFleet')?.addEventListener('input', (e) => {
  renderFleetList(getFilteredFlotas());
});

// Chart rendering
async function fetchAndRenderCharts() {
  // Destroy existing charts
  Object.values(allCharts).forEach(chart => chart?.destroy());
  allCharts = {};

  // Group data for charts
  const clasificacionData = groupBy(allFlotas, 'FlotasClasificacion');
  const zonaData = groupBy(allFlotas, 'FlotasZona');
  const testCounts = await fetchTestCounts();

  // Detect theme for label colors
  const isDarkTheme = document.documentElement.classList.contains('dark-theme');
  const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

  // Chart 1: Clasificación
  const ctxClasificacion = document.getElementById('chartClasificacion');
  if (ctxClasificacion) {
    const labels = Object.keys(clasificacionData);
    const data = Object.values(clasificacionData);
    const total = data.reduce((a, b) => a + b, 0);
    
    allCharts.clasificacion = new Chart(ctxClasificacion, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: ['#d71920', '#ff6384', '#36a2eb', '#ffce56', '#4bc0c0', '#9966ff'],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '50%',
        layout: {
          padding: { top: 30, right: 30, bottom: 50, left: 30 }
        },
        plugins: {
          legend: { display: false },
          tooltip: { enabled: true },
          datalabels: {
            color: labelColor,
            display: function(context) {
              return context.dataset.data[context.dataIndex] > 0;
            },
            formatter: (value, ctx) => {
              const percentage = ((value / total) * 100).toFixed(0);
              const label = ctx.chart.data.labels[ctx.dataIndex];
              return formatChartLabel(label, percentage);
            },
            font: {
              size: 15,
              weight: 'normal'
            },
            anchor: 'end',
            align: 'end',
            offset: 10,
            clamp: true,
            borderWidth: 1,
            borderColor: function(context) {
              return context.dataset.backgroundColor[context.dataIndex];
            },
            borderRadius: 3,
            backgroundColor: function(context) {
              const alpha = context.active ? 0.9 : 0.7;
              return 'rgba(255, 255, 255, ' + alpha + ')';
            },
            padding: 3
          }
        }
      },
      plugins: [ChartDataLabels]
    });
  }

  // Chart 2: Zona
  const ctxZona = document.getElementById('chartZona');
  if (ctxZona) {
    const labels = Object.keys(zonaData);
    const data = Object.values(zonaData);
    const total = data.reduce((a, b) => a + b, 0);
    
    allCharts.zona = new Chart(ctxZona, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: ['#36a2eb', '#ff6384', '#ffce56', '#4bc0c0', '#9966ff', '#d71920'],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '50%',
        layout: {
          padding: { top: 30, right: 30, bottom: 50, left: 30 }
        },
        plugins: {
          legend: { display: false },
          tooltip: { enabled: true },
          datalabels: {
            color: labelColor,
            display: function(context) {
              return context.dataset.data[context.dataIndex] > 0;
            },
            formatter: (value, ctx) => {
              const percentage = ((value / total) * 100).toFixed(0);
              const label = ctx.chart.data.labels[ctx.dataIndex];
              return formatChartLabel(label, percentage);
            },
            font: {
              size: 15,
              weight: 'normal'
            },
            anchor: 'end',
            align: 'end',
            offset: 10,
            clamp: true,
            borderWidth: 1,
            borderColor: function(context) {
              return context.dataset.backgroundColor[context.dataIndex];
            },
            borderRadius: 3,
            backgroundColor: function(context) {
              const alpha = context.active ? 0.9 : 0.7;
              return 'rgba(255, 255, 255, ' + alpha + ')';
            },
            padding: 3
          }
        }
      },
      plugins: [ChartDataLabels]
    });
  }

  // Chart 3: Test counts
  const ctxPruebas = document.getElementById('chartPruebas');
  if (ctxPruebas) {
    // Ensure consistent label order: Semaforo, Desecho, Inspeccion, Rendimiento
    const labelOrder = ['Semaforo', 'Desecho', 'Inspeccion', 'Rendimiento'];
    const labels = labelOrder.filter(label => testCounts.hasOwnProperty(label));
    const data = labels.map(label => testCounts[label] || 0);
    const total = data.reduce((a, b) => a + b, 0);
    
    allCharts.pruebas = new Chart(ctxPruebas, {
      type: 'doughnut',
      data: {
        labels: labels,
        datasets: [{
          data: data,
          backgroundColor: ['#ffce56', '#ff6384', '#4bc0c0', '#36a2eb'],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '50%',
        layout: {
          padding: { top: 30, right: 30, bottom: 50, left: 30 }
        },
        plugins: {
          legend: { display: false },
          tooltip: { enabled: true },
          datalabels: {
            color: labelColor,
            display: function(context) {
              return context.dataset.data[context.dataIndex] > 0;
            },
            formatter: (value, ctx) => {
              const percentage = total > 0 ? ((value / total) * 100).toFixed(0) : 0;
              const label = ctx.chart.data.labels[ctx.dataIndex];
              return formatChartLabel(label, percentage);
            },
            font: {
              size: 15,
              weight: 'normal'
            },
            anchor: 'end',
            align: 'end',
            offset: 10,
            clamp: true,
            borderWidth: 1,
            borderColor: function(context) {
              return context.dataset.backgroundColor[context.dataIndex];
            },
            borderRadius: 3,
            backgroundColor: function(context) {
              const alpha = context.active ? 0.9 : 0.7;
              return 'rgba(255, 255, 255, ' + alpha + ')';
            },
            padding: 3
          }
        }
      },
      plugins: [ChartDataLabels]
    });
  }
}

// Helper function to group array by key
function groupBy(array, key) {
  return array.reduce((acc, item) => {
    const groupKey = item[key] || 'Sin clasificar';
    acc[groupKey] = (acc[groupKey] || 0) + 1;
    return acc;
  }, {});
}

// Initialize dashboard
fetchFlotas();
