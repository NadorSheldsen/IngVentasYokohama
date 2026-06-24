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

  const logo = document.querySelector('.brand-logo');
  if (logo) {
    logo.src = isDark ? '/img/yokohamalogoblanco.png' : '/img/yokohamalogo.png';
  }
}

function toggleTheme() {
  const isDarkNow = document.documentElement.classList.contains('dark-theme');
  localStorage.setItem('theme', isDarkNow ? 'light' : 'dark');
  applyTheme();
  renderInsightsCharts(lastRendimientosGroups);
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

const btnGoInicio = document.getElementById('btnGoInicio');
btnGoInicio?.addEventListener('click', () => {
  window.location.href = 'index.html';
});

const btnAdminIncentivos = document.getElementById('btnAdminIncentivos');
btnAdminIncentivos?.addEventListener('click', () => {
  window.location.href = 'incentivos.html';
});

const btnCompararLlantas = document.getElementById('btnCompararLlantas');
btnCompararLlantas?.addEventListener('click', mostrarGraficoComparacion);

const btnDescargarComparacionPdf = document.getElementById('btnDescargarComparacionPdf');
btnDescargarComparacionPdf?.addEventListener('click', descargarComparacionPdf);

const btnEnviarComparacionWhatsapp = document.getElementById('btnEnviarComparacionWhatsapp');
btnEnviarComparacionWhatsapp?.addEventListener('click', enviarComparacionPorWhatsapp);

const btnCerrarModal = document.getElementById('btnCerrarModal');
btnCerrarModal?.addEventListener('click', cerrarModalComparacion);

const modalComparacion = document.getElementById('modalComparacion');
modalComparacion?.addEventListener('click', (e) => {
  if (e.target === modalComparacion) {
    cerrarModalComparacion();
  }
});

// Event listeners para enviar por correo
const btnEnviarComparacionEmail = document.getElementById('btnEnviarComparacionEmail');
const modalEnviarEmail = document.getElementById('modalEnviarEmail');
const btnCerrarModalEmail = document.getElementById('btnCerrarModalEmail');
const btnConfirmarEnvio = document.getElementById('btnConfirmarEnvio');
const btnCancelarEnvio = document.getElementById('btnCancelarEnvio');
const emailsInput = document.getElementById('emailsInput');
const insightsToggle = document.getElementById('insightsToggle');
const insightsPanel = document.getElementById('insightsPanel');

btnEnviarComparacionEmail?.addEventListener('click', abrirModalEnvioEmail);
btnCerrarModalEmail?.addEventListener('click', cerrarModalEnvioEmail);
btnCancelarEnvio?.addEventListener('click', cerrarModalEnvioEmail);
btnConfirmarEnvio?.addEventListener('click', confirmarEnvioComparacionPorEmail);

insightsToggle?.addEventListener('click', () => {
  const shouldOpen = !insightsPanel?.classList.contains('is-collapsed');
  if (!insightsPanel) {
    return;
  }

  insightsPanel.classList.toggle('is-collapsed', shouldOpen);
  insightsPanel.classList.toggle('is-open', !shouldOpen);
  insightsPanel.setAttribute('aria-hidden', shouldOpen ? 'true' : 'false');
  insightsToggle.setAttribute('aria-expanded', shouldOpen ? 'false' : 'true');

  if (!shouldOpen) {
    renderInsightsCharts(lastRendimientosGroups);
  }
});

modalEnviarEmail?.addEventListener('click', (e) => {
  if (e.target === modalEnviarEmail) {
    cerrarModalEnvioEmail();
  }
});

const rendimientosList = document.getElementById('rendimientosList');
const expandedRendimientoGroups = new Set();
const expandedVehicleGroups = new Set();
const expandedEjeGroups = new Set();
const selectedLlantas = new Set();
let lastRendimientosGroups = [];
let allRendimientosGroups = [];

const filtroFlota = document.getElementById('filtroFlota');
const filtroGiro = document.getElementById('filtroGiro');
const filtroResponsable = document.getElementById('filtroResponsable');
const btnLimpiarFiltros = document.getElementById('btnLimpiarFiltros');
const flotaChips = document.getElementById('flotaChips');
const selectedFlotas = [];

const NAV_FLOTA_KEYS = {
  'desecho.html': 'selectedFlotaForDesecho',
  'semaforo.html': 'selectedFlotaForSemaforo',
  'inspeccion.html': 'selectedFlotaForInspeccion',
  'rendimientos.html': 'selectedFlotaForRendimientos'
};

function getCurrentFlotaContext() {
  if (selectedFlotas.length > 0) {
    return String(selectedFlotas[0] || '').trim();
  }

  if (lastRendimientosGroups.length === 1) {
    return String(lastRendimientosGroups[0]?.transportadora || '').trim();
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

function toNumber(value, fallback = 0) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : fallback;
}

function average(values) {
  const list = values.filter((value) => Number.isFinite(value));
  if (list.length === 0) {
    return 0;
  }
  return list.reduce((sum, value) => sum + value, 0) / list.length;
}

function formatInteger(value) {
  return new Intl.NumberFormat('es-MX', { maximumFractionDigits: 0 }).format(toNumber(value));
}

function formatDecimal(value, fractionDigits = 2) {
  return new Intl.NumberFormat('es-MX', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits
  }).format(toNumber(value));
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function normalizeFilterToken(value) {
  return String(value || '').trim().toLowerCase();
}

function renderFlotaChips() {
  if (!flotaChips) {
    return;
  }

  flotaChips.innerHTML = selectedFlotas.map((flota) => `
    <span class="flota-chip">
      <span>${escapeHtml(flota)}</span>
      <button type="button" class="flota-chip-remove" data-flota="${escapeHtml(flota)}" aria-label="Quitar filtro de ${escapeHtml(flota)}">×</button>
    </span>
  `).join('');
}

function addFlotaFilter(value) {
  const flota = String(value || '').trim();
  if (!flota) {
    return false;
  }

  const normalized = normalizeFilterToken(flota);
  const exists = selectedFlotas.some((item) => normalizeFilterToken(item) === normalized);
  if (exists) {
    return false;
  }

  selectedFlotas.push(flota);
  localStorage.setItem('selectedFlotaContext', flota);
  renderFlotaChips();
  return true;
}

function addFlotasFromInput() {
  if (!filtroFlota) {
    return;
  }

  const rawValue = filtroFlota.value;
  if (!rawValue.trim()) {
    return;
  }

  const tokens = rawValue.split(',').map((item) => item.trim()).filter(Boolean);
  let changed = false;
  tokens.forEach((token) => {
    if (addFlotaFilter(token)) {
      changed = true;
    }
  });

  filtroFlota.value = '';
  if (changed) {
    aplicarFiltros();
  }
}

function removeFlotaFilter(value) {
  const normalized = normalizeFilterToken(value);
  const nextFlotas = selectedFlotas.filter((item) => normalizeFilterToken(item) !== normalized);
  if (nextFlotas.length === selectedFlotas.length) {
    return;
  }

  selectedFlotas.length = 0;
  selectedFlotas.push(...nextFlotas);
  renderFlotaChips();
  aplicarFiltros();
}

function parseDateSafe(value) {
  if (!value) {
    return null;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function getDaysSince(dateValue) {
  const date = parseDateSafe(dateValue);
  if (!date) {
    return null;
  }

  const today = new Date();
  const currentDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const sourceDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diffMs = currentDate.getTime() - sourceDate.getTime();
  return Math.max(0, Math.floor(diffMs / 86400000));
}

function buildAtrasoInfo(daysSinceLastTest) {
  if (!Number.isFinite(daysSinceLastTest)) {
    return {
      dias: null,
      nivel: 'rojo',
      texto: 'Sin prueba',
      clase: 'atraso-rojo',
      enAtraso: true
    };
  }

  if (daysSinceLastTest <= 60) {
    return {
      dias: daysSinceLastTest,
      nivel: 'verde',
      texto: `${daysSinceLastTest} días`,
      clase: 'atraso-verde',
      enAtraso: false
    };
  }

  if (daysSinceLastTest <= 90) {
    return {
      dias: daysSinceLastTest,
      nivel: 'amarillo',
      texto: `${daysSinceLastTest} días`,
      clase: 'atraso-amarillo',
      enAtraso: true
    };
  }

  return {
    dias: daysSinceLastTest,
    nivel: 'rojo',
    texto: `${daysSinceLastTest} días`,
    clase: 'atraso-rojo',
    enAtraso: true
  };
}

async function fetchJsonArray(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Error al obtener ${url}`);
  }
  const payload = await response.json();
  return Array.isArray(payload) ? payload : [];
}

function bindRendimientoToggleEvents() {
  if (!rendimientosList) {
    return;
  }

  rendimientosList.querySelectorAll('.toggle-group-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const groupKey = button.dataset.group;
      if (!groupKey) {
        return;
      }
      if (expandedRendimientoGroups.has(groupKey)) {
        expandedRendimientoGroups.delete(groupKey);
      } else {
        expandedRendimientoGroups.add(groupKey);
      }
      renderRendimientosData(lastRendimientosGroups);
    });
  });

  rendimientosList.querySelectorAll('.toggle-vehicle-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const vehicleKey = button.dataset.vehicle;
      if (!vehicleKey) {
        return;
      }

      if (expandedVehicleGroups.has(vehicleKey)) {
        expandedVehicleGroups.delete(vehicleKey);
      } else {
        expandedVehicleGroups.add(vehicleKey);
        
        // Auto-expand all eje groups when vehicle is expanded
        lastRendimientosGroups.forEach((group) => {
          group.vehiclesData.forEach((vehicle) => {
            if (vehicle.vehicleKey === vehicleKey && vehicle.ejeGroups) {
              vehicle.ejeGroups.forEach((eje) => {
                expandedEjeGroups.add(eje.ejeKey);
              });
            }
          });
        });
      }

      renderRendimientosData(lastRendimientosGroups);
    });
  });

  rendimientosList.querySelectorAll('.toggle-eje-btn').forEach((button) => {
    button.addEventListener('click', () => {
      const ejeKey = button.dataset.eje;
      if (!ejeKey) {
        return;
      }

      if (expandedEjeGroups.has(ejeKey)) {
        expandedEjeGroups.delete(ejeKey);
      } else {
        expandedEjeGroups.add(ejeKey);
      }

      renderRendimientosData(lastRendimientosGroups);
    });
  });
}

function bindCheckboxEvents() {
  if (!rendimientosList) {
    return;
  }

  // Handle individual llanta checkboxes
  rendimientosList.querySelectorAll('.llanta-checkbox').forEach((checkbox) => {
    checkbox.addEventListener('change', (e) => {
      const llantaKey = checkbox.dataset.llanta;
      const ejeKey = checkbox.dataset.eje;
      
      if (e.target.checked) {
        selectedLlantas.add(llantaKey);
      } else {
        selectedLlantas.delete(llantaKey);
      }

      // Update master checkbox state
      updateMasterCheckbox(ejeKey);
      updateCompareButton();
    });
  });

  // Handle master checkboxes for eje
  rendimientosList.querySelectorAll('.eje-checkbox-master').forEach((masterCheckbox) => {
    const ejeKey = masterCheckbox.dataset.eje;
    
    // Set initial state
    updateMasterCheckbox(ejeKey);

    masterCheckbox.addEventListener('change', (e) => {
      const isChecked = e.target.checked;
      const llantaCheckboxes = rendimientosList.querySelectorAll(`.llanta-checkbox[data-eje="${ejeKey}"]`);
      
      llantaCheckboxes.forEach((checkbox) => {
        const llantaKey = checkbox.dataset.llanta;
        if (isChecked) {
          selectedLlantas.add(llantaKey);
          checkbox.checked = true;
        } else {
          selectedLlantas.delete(llantaKey);
          checkbox.checked = false;
        }
      });
      updateCompareButton();
    });
  });
}

function updateMasterCheckbox(ejeKey) {
  const masterCheckbox = rendimientosList.querySelector(`.eje-checkbox-master[data-eje="${ejeKey}"]`);
  if (!masterCheckbox) {
    return;
  }

  const llantaCheckboxes = rendimientosList.querySelectorAll(`.llanta-checkbox[data-eje="${ejeKey}"]`);
  const totalCheckboxes = llantaCheckboxes.length;
  const checkedCheckboxes = Array.from(llantaCheckboxes).filter(cb => cb.checked).length;

  if (checkedCheckboxes === 0) {
    masterCheckbox.checked = false;
    masterCheckbox.indeterminate = false;
  } else if (checkedCheckboxes === totalCheckboxes) {
    masterCheckbox.checked = true;
    masterCheckbox.indeterminate = false;
  } else {
    masterCheckbox.checked = false;
    masterCheckbox.indeterminate = true;
  }
}

function updateCompareButton() {
  const btnComparar = document.getElementById('btnCompararLlantas');
  const countSelected = document.getElementById('countSelected');
  
  if (!btnComparar || !countSelected) {
    return;
  }

  const count = selectedLlantas.size;
  countSelected.textContent = count;
  
  if (count >= 2) {
    btnComparar.style.display = 'flex';
  } else {
    btnComparar.style.display = 'none';
  }
}

function getSelectedLlantasData() {
  const selectedData = [];
  
  lastRendimientosGroups.forEach((group) => {
    group.vehiclesData.forEach((vehicle) => {
      vehicle.ejeGroups.forEach((eje) => {
        eje.rows.forEach((row) => {
          const llantaKey = `${eje.ejeKey}::${row.llantaId}`;
          if (selectedLlantas.has(llantaKey)) {
            selectedData.push({
              llantaKey,
              llantaId: row.llantaId,
              vehiculoNumero: vehicle.vehiculoNumero,
              posicion: row.posicionCorrelativa,
              marca: row.marca,
              modelo: row.modelo,
              medida: row.medida,
              mmInicial: row.mmInicial,
              mmActual: row.mmActual,
              kmRec: row.kmRec,
              kmPorMm: row.kmPorMm,
              ctoKm: row.ctoKm,
              avanceRatio: row.avanceRatio,
              transportadora: group.transportadora
            });
          }
        });
      });
    });
  });
  
  return selectedData;
}

let chartInstance = null;
let chartFlotasPie = null;
let chartMarcasPie = null;

const PIE_BASE_COLORS = [
  'rgba(221, 39, 38, 0.82)',
  'rgba(29, 78, 216, 0.82)',
  'rgba(15, 118, 110, 0.82)',
  'rgba(217, 119, 6, 0.82)',
  'rgba(124, 58, 237, 0.82)',
  'rgba(5, 150, 105, 0.82)',
  'rgba(185, 28, 28, 0.82)',
  'rgba(8, 145, 178, 0.82)',
  'rgba(202, 138, 4, 0.82)',
  'rgba(100, 116, 139, 0.82)'
];

function getPieBorderColor() {
  return document.documentElement.classList.contains('dark-theme') ? '#0f172a' : '#ffffff';
}

function buildPieColors(count) {
  const colors = [];
  for (let index = 0; index < count; index += 1) {
    colors.push(PIE_BASE_COLORS[index % PIE_BASE_COLORS.length]);
  }
  return colors;
}

function summarizePieEntries(entries, maxSlices = 8) {
  const orderedEntries = entries
    .filter((entry) => entry.label && entry.value > 0)
    .sort((a, b) => b.value - a.value || a.label.localeCompare(b.label, 'es-MX'));

  if (orderedEntries.length <= maxSlices) {
    return orderedEntries;
  }

  const slicedEntries = orderedEntries.slice(0, maxSlices - 1);
  const remainingValue = orderedEntries.slice(maxSlices - 1).reduce((sum, entry) => sum + entry.value, 0);

  return [
    ...slicedEntries,
    {
      label: 'Otros',
      value: remainingValue
    }
  ];
}

function renderPieChart(canvasId, existingChart, labels, values) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) {
    return null;
  }

  if (existingChart) {
    existingChart.destroy();
  }

  if (!labels.length || !values.length) {
    return null;
  }

  const colors = buildPieColors(labels.length);
  const borderColor = getPieBorderColor();
  const textColor = getCurrentChartTextColor();
  const total = values.reduce((sum, value) => sum + value, 0);

  return new Chart(canvas, {
    type: 'pie',
    data: {
      labels,
      datasets: [{
        data: values,
        backgroundColor: colors,
        borderColor,
        borderWidth: 2,
        hoverOffset: 10
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: textColor,
            boxWidth: 12,
            usePointStyle: true,
            pointStyle: 'circle'
          }
        },
        tooltip: {
          callbacks: {
            label(context) {
              const rawValue = toNumber(context.raw, 0);
              const percentage = total > 0 ? formatDecimal((rawValue / total) * 100, 1) : '0.0';
              return `${context.label}: ${formatInteger(rawValue)} (${percentage}%)`;
            }
          }
        }
      }
    }
  });
}

function renderInsightsCharts(groups) {
  if (insightsPanel?.classList.contains('is-collapsed')) {
    return;
  }

  const flotaEntries = [];
  const marcaCounts = new Map();

  groups.forEach((group) => {
    flotaEntries.push({
      label: String(group.transportadora || 'Sin flota').trim(),
      value: toNumber(group.totalLlantas, 0)
    });

    (group.rows || []).forEach((row) => {
      const marca = String(row.marca || 'Sin marca').trim();
      marcaCounts.set(marca, (marcaCounts.get(marca) || 0) + 1);
    });
  });

  const flotaSummary = summarizePieEntries(flotaEntries, 8);
  const marcaSummary = summarizePieEntries(
    Array.from(marcaCounts.entries()).map(([label, value]) => ({ label, value })),
    8
  );

  chartFlotasPie = renderPieChart(
    'chartFlotasPie',
    chartFlotasPie,
    flotaSummary.map((entry) => entry.label),
    flotaSummary.map((entry) => entry.value)
  );

  chartMarcasPie = renderPieChart(
    'chartMarcasPie',
    chartMarcasPie,
    marcaSummary.map((entry) => entry.label),
    marcaSummary.map((entry) => entry.value)
  );
}

function formatComparacionValor(datasetLabel, value) {
  if (datasetLabel === 'Cto / km') {
    return formatDecimal(value, 4);
  }
  if (datasetLabel === 'mm Actual') {
    return formatDecimal(value, 1);
  }
  return formatDecimal(value, 0);
}

const barInnerLabelsPlugin = {
  id: 'barInnerLabels',
  afterDatasetsDraw(chart) {
    const { ctx } = chart;

    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '800 16px Arial';

    chart.data.datasets.forEach((dataset, datasetIndex) => {
      const meta = chart.getDatasetMeta(datasetIndex);
      if (!meta || meta.hidden || dataset.hidden) {
        return;
      }

      meta.data.forEach((element, index) => {
        const value = toNumber(dataset.data[index], NaN);
        if (!Number.isFinite(value)) {
          return;
        }

        const labelName = dataset.label;
        const labelValue = formatComparacionValor(dataset.label, value);
        const x = element.x;
        const y = (element.y + element.base) / 2;
        const barHeight = Math.abs(element.base - element.y);

        if (barHeight < 36) {
          return;
        }

        ctx.fillStyle = '#ffffff';
        ctx.strokeStyle = 'rgba(0, 0, 0, 0.35)';
        ctx.lineWidth = 3;

        ctx.strokeText(labelName, x, y - 10);
        ctx.fillText(labelName, x, y - 10);
        ctx.strokeText(labelValue, x, y + 10);
        ctx.fillText(labelValue, x, y + 10);
      });
    });

    ctx.restore();
  }
};

function getCurrentChartTextColor() {
  return getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000';
}

function setComparacionChartTextColor(color) {
  if (!chartInstance?.options) {
    return;
  }

  const options = chartInstance.options;
  if (options.plugins?.legend?.labels) {
    options.plugins.legend.labels.color = color;
  }
  if (options.plugins?.title) {
    options.plugins.title.color = color;
  }

  const scales = options.scales || {};
  if (scales.y?.title) scales.y.title.color = color;
  if (scales.y?.ticks) scales.y.ticks.color = color;

  if (scales.y1?.title) scales.y1.title.color = color;
  if (scales.y1?.ticks) scales.y1.ticks.color = color;

  if (scales.x?.ticks) scales.x.ticks.color = color;
}

function descargarComparacionPdf() {
  if (!chartInstance) {
    alert('Primero genere una comparación para descargar el PDF');
    return;
  }

  const jsPdfLib = window.jspdf?.jsPDF;
  if (!jsPdfLib) {
    alert('No se pudo cargar la librería de PDF');
    return;
  }

  const canvas = document.getElementById('chartComparacion');
  if (!canvas) {
    alert('No se encontró la gráfica de comparación');
    return;
  }

  const previousText = btnDescargarComparacionPdf?.textContent;
  if (btnDescargarComparacionPdf) {
    btnDescargarComparacionPdf.disabled = true;
    btnDescargarComparacionPdf.textContent = 'Generando...';
  }

  try {
    const originalTextColor = getCurrentChartTextColor();
    setComparacionChartTextColor('#000000');
    chartInstance.update('none');

    const pdf = new jsPdfLib({
      orientation: 'landscape',
      unit: 'mm',
      format: 'a4'
    });

    const pageWidth = pdf.internal.pageSize.getWidth();
    const pageHeight = pdf.internal.pageSize.getHeight();
    const margin = 12;

    const title = 'Comparación de Llantas';
    const subtitle = `Generado: ${new Date().toLocaleString('es-MX')}`;
    pdf.setFont('helvetica', 'bold');
    pdf.setFontSize(16);
    pdf.text(title, margin, 12);
    pdf.setFont('helvetica', 'normal');
    pdf.setFontSize(10);
    pdf.text(subtitle, margin, 18);

    const exportCanvas = document.createElement('canvas');
    exportCanvas.width = canvas.width;
    exportCanvas.height = canvas.height;
    const exportCtx = exportCanvas.getContext('2d');
    if (!exportCtx) {
      throw new Error('No se pudo crear el contexto de exportación');
    }

    exportCtx.fillStyle = '#ffffff';
    exportCtx.fillRect(0, 0, exportCanvas.width, exportCanvas.height);
    exportCtx.drawImage(canvas, 0, 0);

    const imageData = exportCanvas.toDataURL('image/png', 1.0);
    const availableWidth = pageWidth - (margin * 2);
    const availableHeight = pageHeight - 28;
    const canvasRatio = canvas.width / Math.max(1, canvas.height);

    let imageWidth = availableWidth;
    let imageHeight = imageWidth / canvasRatio;

    if (imageHeight > availableHeight) {
      imageHeight = availableHeight;
      imageWidth = imageHeight * canvasRatio;
    }

    const imageX = (pageWidth - imageWidth) / 2;
    const imageY = 24;

    pdf.addImage(imageData, 'PNG', imageX, imageY, imageWidth, imageHeight, undefined, 'FAST');

    const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
    pdf.save(`comparacion_llantas_${stamp}.pdf`);

    setComparacionChartTextColor(originalTextColor);
    chartInstance.update('none');
  } catch (error) {
    console.error('Error generando PDF de comparación:', error);
    alert('No se pudo generar el PDF de la gráfica');
    setComparacionChartTextColor(getCurrentChartTextColor());
    chartInstance.update('none');
  } finally {
    if (btnDescargarComparacionPdf) {
      btnDescargarComparacionPdf.disabled = false;
      btnDescargarComparacionPdf.textContent = previousText || 'Descargar PDF';
    }
  }
}

function agruparComparacionPorTipoLlanta(selectedData) {
  const groupsByType = new Map();

  selectedData.forEach((item) => {
    const marca = String(item.marca || 'Sin marca').trim();
    const modelo = String(item.modelo || 'Sin modelo').trim();
    const key = `${marca}||${modelo}`;

    if (!groupsByType.has(key)) {
      groupsByType.set(key, {
        marca,
        modelo,
        count: 0,
        sumKmPorMm: 0,
        sumMmActual: 0,
        sumKmRec: 0,
        sumCtoKm: 0
      });
    }

    const group = groupsByType.get(key);
    group.count += 1;
    group.sumKmPorMm += toNumber(item.kmPorMm, 0);
    group.sumMmActual += toNumber(item.mmActual, 0);
    group.sumKmRec += toNumber(item.kmRec, 0);
    group.sumCtoKm += toNumber(item.ctoKm, 0);
  });

  return Array.from(groupsByType.values())
    .map((group) => ({
      tipo: `${group.marca} ${group.modelo}`.trim(),
      count: group.count,
      kmPorMmPromedio: group.count > 0 ? group.sumKmPorMm / group.count : 0,
      mmActualPromedio: group.count > 0 ? group.sumMmActual / group.count : 0,
      kmRecPromedio: group.count > 0 ? group.sumKmRec / group.count : 0,
      ctoKmPromedio: group.count > 0 ? group.sumCtoKm / group.count : 0
    }))
    .sort((a, b) => a.tipo.localeCompare(b.tipo, 'es-MX'));
}

function mostrarGraficoComparacion() {
  const selectedData = getSelectedLlantasData();
  
  if (selectedData.length < 2) {
    alert('Debe seleccionar al menos 2 llantas para comparar');
    return;
  }

  const modal = document.getElementById('modalComparacion');
  if (!modal) {
    return;
  }

  modal.style.display = 'flex';

  const groupedData = agruparComparacionPorTipoLlanta(selectedData);

  // Prepare chart data
  const labels = groupedData.map((d) => 
    `${d.tipo} (${d.count})`
  );

  const datasets = [
    {
      label: 'Km / mm',
      data: groupedData.map((d) => d.kmPorMmPromedio),
      backgroundColor: 'rgba(54, 162, 235, 0.6)',
      borderColor: 'rgba(54, 162, 235, 1)',
      borderWidth: 2,
      yAxisID: 'y'
    },
    {
      label: 'mm Actual',
      data: groupedData.map((d) => d.mmActualPromedio),
      backgroundColor: 'rgba(255, 99, 132, 0.6)',
      borderColor: 'rgba(255, 99, 132, 1)',
      borderWidth: 2,
      yAxisID: 'y1'
    },
    {
      label: 'Km Recorridos',
      data: groupedData.map((d) => d.kmRecPromedio),
      backgroundColor: 'rgba(75, 192, 192, 0.6)',
      borderColor: 'rgba(75, 192, 192, 1)',
      borderWidth: 2,
      yAxisID: 'y2',
      hidden: true
    }
  ];

  const ctx = document.getElementById('chartComparacion');
  if (!ctx) {
    return;
  }

  // Destroy previous chart instance
  if (chartInstance) {
    chartInstance.destroy();
  }

  chartInstance = new Chart(ctx, {
    type: 'bar',
    plugins: [barInnerLabelsPlugin],
    data: {
      labels: labels,
      datasets: datasets
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      aspectRatio: 2,
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000'
          }
        },
        title: {
          display: true,
          text: 'Comparación Promedio por Tipo de Llanta (Marca + Modelo)',
          color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000',
          font: {
            size: 16,
            weight: 'bold'
          }
        }
      },
      scales: {
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Km / mm',
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000'
          },
          ticks: {
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000'
          },
          grid: {
            color: 'rgba(128, 128, 128, 0.2)'
          }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'mm Actual',
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000'
          },
          ticks: {
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000'
          },
          grid: {
            drawOnChartArea: false
          }
        },
        y2: {
          type: 'linear',
          display: false,
          position: 'right',
          title: {
            display: true,
            text: 'Km Recorridos'
          }
        },
        x: {
          ticks: {
            color: getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000',
            maxRotation: 45,
            minRotation: 45
          },
          grid: {
            color: 'rgba(128, 128, 128, 0.2)'
          }
        }
      }
    }
  });
}

function cerrarModalComparacion() {
  const modal = document.getElementById('modalComparacion');
  if (modal) {
    modal.style.display = 'none';
  }
}

function abrirModalEnvioEmail() {
  const modal = document.getElementById('modalEnviarEmail');
  const input = document.getElementById('emailsInput');
  if (modal) {
    modal.style.display = 'flex';
    if (input) {
      input.value = '';
      input.focus();
    }
  }
}

function cerrarModalEnvioEmail() {
  const modal = document.getElementById('modalEnviarEmail');
  if (modal) {
    modal.style.display = 'none';
  }
  const input = document.getElementById('emailsInput');
  if (input) {
    input.value = '';
  }
}

async function generarPdfComparacionBase64() {
  if (!chartInstance) {
    throw new Error('No hay gráfica de comparación para enviar');
  }

  const jsPdfLib = window.jspdf?.jsPDF;
  if (!jsPdfLib) {
    throw new Error('No se pudo cargar la librería de PDF');
  }

  const canvas = document.getElementById('chartComparacion');
  if (!canvas) {
    throw new Error('No se encontró la gráfica de comparación');
  }

  return new Promise((resolve, reject) => {
    try {
      const originalTextColor = getCurrentChartTextColor();
      setComparacionChartTextColor('#000000');
      chartInstance.update('none');

      const pdf = new jsPdfLib({
        orientation: 'landscape',
        unit: 'mm',
        format: 'a4'
      });

      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const margin = 12;

      const title = 'Comparación de Llantas';
      const subtitle = `Generado: ${new Date().toLocaleString('es-MX')}`;
      pdf.setFont('helvetica', 'bold');
      pdf.setFontSize(16);
      pdf.text(title, margin, 12);
      pdf.setFont('helvetica', 'normal');
      pdf.setFontSize(10);
      pdf.text(subtitle, margin, 18);

      const exportCanvas = document.createElement('canvas');
      exportCanvas.width = canvas.width;
      exportCanvas.height = canvas.height;
      const exportCtx = exportCanvas.getContext('2d');
      if (!exportCtx) {
        throw new Error('No se pudo crear el contexto de exportación');
      }

      exportCtx.fillStyle = '#ffffff';
      exportCtx.fillRect(0, 0, exportCanvas.width, exportCanvas.height);
      exportCtx.drawImage(canvas, 0, 0);

      const imageData = exportCanvas.toDataURL('image/png', 1.0);
      const availableWidth = pageWidth - (margin * 2);
      const availableHeight = pageHeight - 28;
      const canvasRatio = canvas.width / Math.max(1, canvas.height);

      let imageWidth = availableWidth;
      let imageHeight = imageWidth / canvasRatio;

      if (imageHeight > availableHeight) {
        imageHeight = availableHeight;
        imageWidth = imageHeight * canvasRatio;
      }

      const imageX = (pageWidth - imageWidth) / 2;
      const imageY = 24;

      pdf.addImage(imageData, 'PNG', imageX, imageY, imageWidth, imageHeight, undefined, 'FAST');

      // Obtener el PDF como base64
      const pdfBase64 = pdf.output('dataurlstring').split(',')[1];

      setComparacionChartTextColor(originalTextColor);
      chartInstance.update('none');

      resolve(pdfBase64);
    } catch (error) {
      setComparacionChartTextColor(getCurrentChartTextColor());
      chartInstance?.update('none');
      reject(error);
    }
  });
}

async function confirmarEnvioComparacionPorEmail() {
  const emailsInput = document.getElementById('emailsInput');
  const btnConfirmarEnvio = document.getElementById('btnConfirmarEnvio');
  const rawEmails = (emailsInput?.value || '').trim();

  if (!rawEmails) {
    alert('Por favor ingresa al menos un correo');
    return;
  }

  const emails = rawEmails
    .split(',')
    .map((email) => email.trim())
    .filter(Boolean);

  if (!Array.isArray(emails) || emails.length === 0) {
    alert('Ingresa correos válidos separados por comas');
    return;
  }

  const previousText = btnConfirmarEnvio?.textContent;
  if (btnConfirmarEnvio) {
    btnConfirmarEnvio.disabled = true;
    btnConfirmarEnvio.textContent = 'Enviando...';
  }

  try {
    const pdfBase64 = await generarPdfComparacionBase64();

    const response = await fetch('/api/reports/send-chart-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        emails,
        subject: 'Comparación de Llantas',
        chartName: 'Comparación de Llantas',
        pdfBase64
      })
    });

    const result = await response.json();
    if (!response.ok) {
      throw new Error(result.message || 'No se pudo enviar el correo');
    }

    alert('Correo enviado correctamente');
    cerrarModalEnvioEmail();
  } catch (error) {
    console.error('Error enviando correo:', error);
    alert('Error al enviar correo: ' + (error.message || 'Error desconocido'));
  } finally {
    if (btnConfirmarEnvio) {
      btnConfirmarEnvio.disabled = false;
      btnConfirmarEnvio.textContent = previousText || 'Enviar';
    }
  }
}

function base64ToBlob(base64, contentType) {
  const byteCharacters = atob(base64);
  const byteNumbers = new Array(byteCharacters.length);
  for (let i = 0; i < byteCharacters.length; i += 1) {
    byteNumbers[i] = byteCharacters.charCodeAt(i);
  }
  const byteArray = new Uint8Array(byteNumbers);
  return new Blob([byteArray], { type: contentType || 'application/octet-stream' });
}

async function enviarComparacionPorWhatsapp() {
  if (!chartInstance) {
    alert('Primero genere una comparación');
    return;
  }

  const previousText = btnEnviarComparacionWhatsapp?.innerHTML;
  if (btnEnviarComparacionWhatsapp) {
    btnEnviarComparacionWhatsapp.disabled = true;
    btnEnviarComparacionWhatsapp.innerHTML = '...';
  }

  try {
    const pdfBase64 = await generarPdfComparacionBase64();
    const pdfBlob = base64ToBlob(pdfBase64, 'application/pdf');
    const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
    const fileName = `comparacion_llantas_${stamp}.pdf`;
    const message = 'Comparación de llantas generada desde Yokohama.';

    // En móvil, intenta compartir el archivo PDF directamente.
    if (window.isSecureContext && navigator.share) {
      const file = new File([pdfBlob], fileName, { type: 'application/pdf' });
      const canShareFiles = !navigator.canShare || navigator.canShare({ files: [file] });
      if (canShareFiles) {
        try {
          await navigator.share({
            title: 'Comparación de Llantas',
            text: message,
            files: [file]
          });
          return;
        } catch (shareError) {
          if (shareError?.name === 'AbortError') {
            return;
          }
        }
      }
    }

    // Fallback escritorio: descarga el PDF y abre WhatsApp con mensaje precargado.
    const downloadUrl = URL.createObjectURL(pdfBlob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000);

    const rawPhone = window.prompt('Opcional: Ingresa número de WhatsApp con código de país (ej. 5215512345678)');
    const phone = String(rawPhone || '').replace(/\D/g, '');
    const waText = encodeURIComponent(`${message}\n\nAdjunto el PDF descargado automáticamente.`);
    const waUrl = phone
      ? `https://wa.me/${phone}?text=${waText}`
      : `https://wa.me/?text=${waText}`;

    window.open(waUrl, '_blank', 'noopener');
    alert('Se descargó el PDF. Solo adjúntalo en WhatsApp y envíalo.');
  } catch (error) {
    console.error('Error enviando por WhatsApp:', error);
    alert('No se pudo preparar el envío por WhatsApp');
  } finally {
    if (btnEnviarComparacionWhatsapp) {
      btnEnviarComparacionWhatsapp.disabled = false;
      btnEnviarComparacionWhatsapp.innerHTML = previousText || btnEnviarComparacionWhatsapp.innerHTML;
    }
  }
}

function buildVehicleGroups(rows, groupKey) {
  const vehiclesByNumber = new Map();

  rows.forEach((row) => {
    const vehiculoNumero = String(row.vehiculoNumero || '-');
    if (!vehiclesByNumber.has(vehiculoNumero)) {
      vehiclesByNumber.set(vehiculoNumero, {
        vehicleKey: `${groupKey}::${vehiculoNumero}`,
        vehiculoNumero,
        rows: []
      });
    }

    vehiclesByNumber.get(vehiculoNumero).rows.push(row);
  });

  return Array.from(vehiclesByNumber.values())
    .map((vehicle) => {
      // Sort and assign sequential positions
      const sortedRows = vehicle.rows
        .sort((a, b) => toNumber(a.llantaId, 0) - toNumber(b.llantaId, 0))
        .map((row, index) => ({
          ...row,
          posicionCorrelativa: index + 1
        }));

      // Separate into eje de dirección (positions 1, 2) and ejes libres
      const ejeDireccion = sortedRows.filter(row => row.posicionCorrelativa === 1 || row.posicionCorrelativa === 2);
      const ejesLibres = sortedRows.filter(row => row.posicionCorrelativa !== 1 && row.posicionCorrelativa !== 2);

      // Create eje groups
      const ejeGroups = [];
      if (ejeDireccion.length > 0) {
        const atrasoVehicleCount = ejeDireccion.some((row) => row.atrasoVehiculo) ? 1 : 0;
        ejeGroups.push({
          ejeKey: `${vehicle.vehicleKey}::direccion`,
          ejeNombre: 'Eje de dirección',
          rows: ejeDireccion,
          totalLlantas: ejeDireccion.length,
          totalAtraso: atrasoVehicleCount,
          promedioKmMm: average(ejeDireccion.map(row => row.kmPorMm)),
          promedioCtoKm: average(ejeDireccion.map(row => row.ctoKm))
        });
      }
      if (ejesLibres.length > 0) {
        const atrasoVehicleCount = ejesLibres.some((row) => row.atrasoVehiculo) ? 1 : 0;
        ejeGroups.push({
          ejeKey: `${vehicle.vehicleKey}::libres`,
          ejeNombre: 'Ejes libres',
          rows: ejesLibres,
          totalLlantas: ejesLibres.length,
          totalAtraso: atrasoVehicleCount,
          promedioKmMm: average(ejesLibres.map(row => row.kmPorMm)),
          promedioCtoKm: average(ejesLibres.map(row => row.ctoKm))
        });
      }

      const atrasoVehiculo = sortedRows.some((row) => row.atrasoVehiculo);
      const atrasoTexto = sortedRows[0]?.atrasoTexto || 'Sin prueba';
      const atrasoClase = sortedRows[0]?.atrasoClase || 'atraso-rojo';

      return {
        vehicleKey: vehicle.vehicleKey,
        vehiculoNumero: vehicle.vehiculoNumero,
        rows: sortedRows,
        ejeGroups,
        totalLlantas: sortedRows.length,
        totalAtraso: atrasoVehiculo ? 1 : 0,
        atrasoVehiculo,
        atrasoTexto,
        atrasoClase,
        promedioKmMm: average(sortedRows.map((row) => row.kmPorMm)),
        promedioCtoKm: average(sortedRows.map((row) => row.ctoKm))
      };
    })
    .sort((a, b) => a.vehiculoNumero.localeCompare(b.vehiculoNumero));
}

function renderRendimientosData(groups) {
  if (!rendimientosList) {
    return;
  }

  lastRendimientosGroups = groups;

  if (!groups.length) {
    rendimientosList.innerHTML = '<p class="loading-text">No hay información de rendimientos disponible</p>';
    return;
  }

  rendimientosList.innerHTML = groups.map((group) => {
    const isExpanded = expandedRendimientoGroups.has(group.groupKey);
    const toggleSymbol = isExpanded ? '−' : '+';

    const vehiclesHtml = group.vehiclesData.map((vehicle) => {
      const vehicleExpanded = expandedVehicleGroups.has(vehicle.vehicleKey);
      const vehicleToggleSymbol = vehicleExpanded ? '−' : '+';

      const ejesHtml = vehicle.ejeGroups.map((eje) => {
        const ejeExpanded = expandedEjeGroups.has(eje.ejeKey);
        const ejeToggleSymbol = ejeExpanded ? '−' : '+';

        const rowsHtml = eje.rows.map((row) => {
          const avanceWidth = `${Math.max(6, Math.min(100, Math.round(row.avanceRatio * 100)))}%`;
          const llantaKey = `${eje.ejeKey}::${row.llantaId}`;
          const isChecked = selectedLlantas.has(llantaKey) ? 'checked' : '';
          const atrasoClass = row.atrasoClase || 'atraso-rojo';
          return `
            <tr>
              <td>Posición ${escapeHtml(row.posicionCorrelativa)}</td>
              <td><input type="checkbox" class="llanta-checkbox" data-llanta="${escapeHtml(llantaKey)}" data-eje="${escapeHtml(eje.ejeKey)}" ${isChecked}></td>
              <td>${escapeHtml(row.marca)}</td>
              <td>${escapeHtml(row.modelo)}</td>
              <td>${escapeHtml(row.medida)}</td>
              <td>${formatInteger(row.kmRec)}</td>
              <td>${formatDecimal(row.mmInicial, 1)} mm</td>
              <td>${formatDecimal(row.mmActual, 1)} mm</td>
              <td><span class="metric-highlight">${formatDecimal(row.kmPorMm, 0)} Km / mm</span></td>
              <td><span class="metric-highlight">${formatDecimal(row.ctoKm, 4)} Cto / km</span></td>
              <td><span class="avance-indicator" aria-label="Avance"><span style="width: ${avanceWidth}"></span></span></td>
              <td><span class="atraso-text ${atrasoClass}">${escapeHtml(row.atrasoTexto)}</span></td>
            </tr>
          `;
        }).join('');

        return `
          <div class="eje-group">
            <div class="eje-summary">
              <button type="button" class="toggle-eje-btn" data-eje="${escapeHtml(eje.ejeKey)}" aria-label="Expandir eje">${ejeToggleSymbol}</button>
              <div class="eje-summary-main">${escapeHtml(eje.ejeNombre)}</div>
              <div class="eje-summary-meta">${formatInteger(eje.totalLlantas)} llantas</div>
              <div class="eje-summary-meta">Prom. <span class="metric-highlight">${formatDecimal(eje.promedioKmMm, 0)} Km/mm</span></div>
              <div class="eje-summary-meta">Prom. <span class="metric-highlight">${formatDecimal(eje.promedioCtoKm, 4)} Cto/km</span></div>
              <div class="eje-summary-meta">Veh. atraso ${formatInteger(eje.totalAtraso)}</div>
            </div>
            <div class="eje-detail" ${ejeExpanded ? '' : 'hidden'}>
              <table class="detail-table">
                <thead>
                  <tr>
                    <th>Posición</th>
                    <th><input type="checkbox" class="eje-checkbox-master" data-eje="${escapeHtml(eje.ejeKey)}" title="Seleccionar todas"></th>
                    <th>Marca</th>
                    <th>Modelo</th>
                    <th>Medida</th>
                    <th>km Rec</th>
                    <th>mm inc.</th>
                    <th>mm act</th>
                    <th>Km / mm</th>
                    <th>Cto / Km</th>
                    <th>Avance</th>
                    <th>Atraso</th>
                  </tr>
                </thead>
                <tbody>
                  ${rowsHtml}
                </tbody>
              </table>
            </div>
          </div>
        `;
      }).join('');

      return `
        <section class="vehicle-group">
          <div class="vehicle-summary">
            <button type="button" class="toggle-vehicle-btn" data-vehicle="${escapeHtml(vehicle.vehicleKey)}" aria-label="Expandir vehículo">${vehicleToggleSymbol}</button>
            <div class="vehicle-summary-main">Vehículo ${escapeHtml(vehicle.vehiculoNumero)}</div>
            <div class="vehicle-summary-meta">${formatInteger(vehicle.totalLlantas)} llantas</div>
            <div class="vehicle-summary-meta">Prom. <span class="metric-highlight">${formatDecimal(vehicle.promedioKmMm, 0)} Km/mm</span></div>
            <div class="vehicle-summary-meta">Prom. <span class="metric-highlight">${formatDecimal(vehicle.promedioCtoKm, 4)} Cto/km</span></div>
            <div class="vehicle-summary-meta">Últ. prueba <span class="atraso-text ${escapeHtml(vehicle.atrasoClase)}">${escapeHtml(vehicle.atrasoTexto)}</span></div>
          </div>
          <div class="vehicle-detail" ${vehicleExpanded ? '' : 'hidden'}>
            ${ejesHtml}
          </div>
        </section>
      `;
    }).join('');

    return `
      <article class="rendimientos-group">
        <div class="rendimientos-summary">
          <button type="button" class="toggle-group-btn" data-group="${escapeHtml(group.groupKey)}" aria-label="Expandir grupo">${toggleSymbol}</button>
          <div>
            <div class="summary-head">Nombre Transportadora</div>
            <div class="summary-value">${escapeHtml(group.transportadora)}</div>
          </div>
          <div>
            <div class="summary-head">Vehículos/ llantas</div>
            <div class="summary-value">${formatInteger(group.totalVehiculos)} / ${formatInteger(group.totalLlantas)}</div>
          </div>
          <div>
            <div class="summary-head">Giro</div>
            <div class="summary-value">${escapeHtml(group.giro)}</div>
          </div>
          <div>
            <div class="summary-head">Técnico Responsable</div>
            <div class="summary-value">${escapeHtml(group.responsable)}</div>
          </div>
          <div>
            <div class="summary-head">Promedio km/mm</div>
            <div class="summary-value"><span class="metric-highlight">${formatDecimal(group.promedioKmMm, 0)} Km / mm</span></div>
          </div>
          <div>
            <div class="summary-head">Promedio Cto/km</div>
            <div class="summary-value"><span class="metric-highlight">${formatDecimal(group.promedioCtoKm, 4)} Cto / km</span></div>
          </div>
        </div>
        <div class="group-detail" ${isExpanded ? '' : 'hidden'}>
          ${vehiclesHtml}
        </div>
      </article>
    `;
  }).join('');

  renderInsightsCharts(groups);
  bindRendimientoToggleEvents();
  bindCheckboxEvents();
}

function poblarFiltros(groups) {
  // Extract unique values
  const flotas = new Set();
  const giros = new Set();
  const responsables = new Set();

  groups.forEach((group) => {
    flotas.add(group.transportadora);
    if (group.giro) giros.add(group.giro);
    if (group.responsable) responsables.add(group.responsable);
  });

  // Populate Flota datalist
  const listaFlotas = document.getElementById('listaFlotas');
  if (listaFlotas) {
    const flotaOptions = Array.from(flotas).sort();
    listaFlotas.innerHTML = flotaOptions.map(flota => `<option value="${escapeHtml(flota)}">`).join('');
  }

  // Populate Giro datalist
  const listaGiros = document.getElementById('listaGiros');
  if (listaGiros) {
    const giroOptions = Array.from(giros).sort();
    listaGiros.innerHTML = giroOptions.map(giro => `<option value="${escapeHtml(giro)}">`).join('');
  }

  // Populate Responsable datalist
  const listaResponsables = document.getElementById('listaResponsables');
  if (listaResponsables) {
    const responsableOptions = Array.from(responsables).sort();
    listaResponsables.innerHTML = responsableOptions.map(resp => `<option value="${escapeHtml(resp)}">`).join('');
  }
}

function aplicarFiltros() {
  const flotasSeleccionadas = selectedFlotas.map((item) => normalizeFilterToken(item));
  const giroSeleccionado = (filtroGiro?.value || '').trim().toLowerCase();
  const responsableSeleccionado = (filtroResponsable?.value || '').trim().toLowerCase();

  const groupsFiltrados = allRendimientosGroups.filter((group) => {
    const transportadora = (group.transportadora || '').toLowerCase();
    if (flotasSeleccionadas.length > 0) {
      const coincideConAlgunaFlota = flotasSeleccionadas.some((flota) => transportadora.includes(flota));
      if (!coincideConAlgunaFlota) {
        return false;
      }
    }
    if (giroSeleccionado && !group.giro?.toLowerCase().includes(giroSeleccionado)) {
      return false;
    }
    if (responsableSeleccionado && !group.responsable?.toLowerCase().includes(responsableSeleccionado)) {
      return false;
    }
    return true;
  });

  renderRendimientosData(groupsFiltrados);
}

function limpiarFiltros() {
  if (filtroFlota) filtroFlota.value = '';
  if (filtroGiro) filtroGiro.value = '';
  if (filtroResponsable) filtroResponsable.value = '';
  selectedFlotas.length = 0;
  renderFlotaChips();
  aplicarFiltros();
}

filtroFlota?.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    event.preventDefault();
    addFlotasFromInput();
  }
});
filtroFlota?.addEventListener('change', addFlotasFromInput);
filtroFlota?.addEventListener('blur', addFlotasFromInput);
filtroGiro?.addEventListener('input', aplicarFiltros);
filtroResponsable?.addEventListener('input', aplicarFiltros);
btnLimpiarFiltros?.addEventListener('click', limpiarFiltros);

flotaChips?.addEventListener('click', (event) => {
  const removeButton = event.target.closest('.flota-chip-remove');
  if (!removeButton) {
    return;
  }

  const flotaValue = removeButton.dataset.flota;
  removeFlotaFilter(flotaValue);
});

async function fetchAndRenderRendimientos() {
  if (!rendimientosList) {
    return;
  }

  rendimientosList.innerHTML = '<p class="loading-text">Cargando rendimientos...</p>';

  try {
    const [flotas, vehiculos, llantasVehiculos, llantasRendimiento] = await Promise.all([
      fetchJsonArray('/api/flotas'),
      fetchJsonArray('/api/vehiculos'),
      fetchJsonArray('/api/llantas-vehiculos'),
      fetchJsonArray('/api/llantas-rendimiento/ultimos')
    ]);

    const flotasByName = new Map(flotas.map((item) => [item.FlotasNombre, item]));
    const vehiculosById = new Map(vehiculos.map((item) => [item.idVehiculos, item]));
    const rendimientoByLlanta = new Map(
      llantasRendimiento.map((item) => [item.LlantasVehiculos_idLlantasVehiculos, item])
    );

    const latestTestDateByVehiculo = new Map();
    llantasVehiculos.forEach((llanta) => {
      const vehiculoId = llanta.Vehiculos_idVehiculos;
      const rendimiento = rendimientoByLlanta.get(llanta.idLlantasVehiculos);
      const testDate = parseDateSafe(rendimiento?.PruebaRendimientoFecha);
      if (!testDate) {
        return;
      }

      const prev = latestTestDateByVehiculo.get(vehiculoId);
      if (!prev || testDate.getTime() > prev.getTime()) {
        latestTestDateByVehiculo.set(vehiculoId, testDate);
      }
    });

    const atrasoInfoByVehiculo = new Map();
    vehiculos.forEach((vehiculo) => {
      const latestDate = latestTestDateByVehiculo.get(vehiculo.idVehiculos);
      const daysSince = latestDate ? getDaysSince(latestDate) : null;
      atrasoInfoByVehiculo.set(vehiculo.idVehiculos, buildAtrasoInfo(daysSince));
    });

    const groupsByFlota = new Map();

    llantasVehiculos.forEach((llanta) => {
      const vehiculo = vehiculosById.get(llanta.Vehiculos_idVehiculos);
      const transportadora = vehiculo?.FlotasNombre || 'Sin transportadora';

      if (!groupsByFlota.has(transportadora)) {
        const flotaInfo = flotasByName.get(transportadora);
        groupsByFlota.set(transportadora, {
          groupKey: transportadora,
          transportadora,
          giro: flotaInfo?.FlotasClasificacion || 'Carga en general',
          responsable: 'Nombre técnico',
          vehiculos: new Set(),
          rows: []
        });
      }

      const group = groupsByFlota.get(transportadora);
      group.vehiculos.add(llanta.Vehiculos_idVehiculos);

      const rendimiento = rendimientoByLlanta.get(llanta.idLlantasVehiculos);
      const mmInicialCandidates = [
        toNumber(llanta.LlantasMm, NaN),
        toNumber(llanta.LlantasVehiculosMM1, NaN),
        toNumber(llanta.LlantasVehiculosMM2, NaN),
        toNumber(llanta.LlantasVehiculosMM3, NaN),
        toNumber(llanta.LlantasVehiculosMM4, NaN)
      ];
      const mmInicial = mmInicialCandidates.find((value) => Number.isFinite(value) && value > 0) || 0;

      const mmActual = average([
        toNumber(rendimiento?.LlantasRendimientoMm1, NaN),
        toNumber(rendimiento?.LlantasRendimientoMm2, NaN),
        toNumber(rendimiento?.LlantasRendimientoMm3, NaN),
        toNumber(rendimiento?.LlantasRendimientoMm4, NaN)
      ]) || average([
        toNumber(llanta.LlantasVehiculosMM1, NaN),
        toNumber(llanta.LlantasVehiculosMM2, NaN),
        toNumber(llanta.LlantasVehiculosMM3, NaN),
        toNumber(llanta.LlantasVehiculosMM4, NaN)
      ]);

      const kmRec = toNumber(
        rendimiento?.KmDesdeUltimaPrueba ?? rendimiento?.kmRecorrido ?? rendimiento?.kmRec,
        0
      );
      const desgaste = Math.max(mmInicial - mmActual, 0);
      const kmPorMm = desgaste > 0 ? kmRec / desgaste : kmRec;
      const costoLlanta = toNumber(llanta.LlantasVehiculosPrecio || llanta.LlantasPrecio, 0);
      const ctoKm = kmRec > 0 ? costoLlanta / kmRec : 0;
      const avanceRatio = mmInicial > 0 ? Math.max(0, Math.min(1, mmActual / mmInicial)) : 0;
      const atrasoInfo = atrasoInfoByVehiculo.get(llanta.Vehiculos_idVehiculos) || buildAtrasoInfo(null);

      group.rows.push({
        llantaId: llanta.idLlantasVehiculos,
        vehiculoNumero: vehiculo?.VehiculosNumero || '-',
        posicion: llanta.LlantasVehiculosNoQuemado || '-',
        marca: llanta.LlantasMarca || '-',
        modelo: llanta.LlantasModelo || '-',
        medida: llanta.LlantasMedida || '-',
        mmInicial,
        mmActual,
        kmRec,
        kmPorMm,
        ctoKm,
        avanceRatio,
        atrasoDias: atrasoInfo.dias,
        atrasoNivel: atrasoInfo.nivel,
        atrasoTexto: atrasoInfo.texto,
        atrasoClase: atrasoInfo.clase,
        atrasoVehiculo: atrasoInfo.enAtraso
      });
    });

    const groups = Array.from(groupsByFlota.values())
      .map((group) => {
        const totalLlantas = group.rows.length;
        const promedioKmMm = average(group.rows.map((row) => row.kmPorMm));
        const promedioCtoKm = average(group.rows.map((row) => row.ctoKm));
        const vehiclesData = buildVehicleGroups(group.rows, group.groupKey);
        const totalAtraso = vehiclesData.filter((vehicle) => vehicle.atrasoVehiculo).length;

        return {
          groupKey: group.groupKey,
          transportadora: group.transportadora,
          giro: group.giro,
          responsable: group.responsable,
          totalVehiculos: group.vehiculos.size,
          totalLlantas,
          promedioKmMm,
          promedioCtoKm,
          totalAtraso,
          rows: group.rows.sort((a, b) => String(a.vehiculoNumero).localeCompare(String(b.vehiculoNumero))),
          vehiclesData
        };
      })
      .sort((a, b) => a.transportadora.localeCompare(b.transportadora));

    // Store all groups for filtering
    allRendimientosGroups = groups;
    
    // Populate filter dropdowns
    poblarFiltros(groups);

    // Initial state: keep every level collapsed on first load.
    expandedRendimientoGroups.clear();
    expandedVehicleGroups.clear();
    expandedEjeGroups.clear();

    // Apply saved flota filter if exists
    const savedFlota = localStorage.getItem('selectedFlotaForRendimientos') || localStorage.getItem('selectedFlotaContext');
    if (savedFlota) {
      addFlotaFilter(savedFlota);
      localStorage.setItem('selectedFlotaContext', savedFlota);
      localStorage.removeItem('selectedFlotaForRendimientos');
      aplicarFiltros();
    } else {
      renderRendimientosData(groups);
    }
  } catch (error) {
    console.error('Error al cargar administración de rendimientos:', error);
    rendimientosList.innerHTML = '<p class="loading-text">Error al cargar rendimientos</p>';
  }
}

fetchAndRenderRendimientos();
