function getStoredUser() {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true';
  const rawUser = localStorage.getItem('user');

  if (!isLoggedIn || !rawUser) {
    window.location.href = 'login.html';
    return null;
  }

  try {
    return JSON.parse(rawUser);
  } catch (_error) {
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

  if (typeof renderEficienciaChart === 'function') {
    renderEficienciaChart(currentRows);
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

const btnGoInicio = document.getElementById('btnGoInicio');
btnGoInicio?.addEventListener('click', () => {
  window.location.href = 'index.html';
});

const btnAdminRendimientos = document.getElementById('btnAdminRendimientos');
btnAdminRendimientos?.addEventListener('click', () => {
  try {
    localStorage.removeItem('selectedFlotaForRendimientos');
    localStorage.removeItem('selectedFlotaContext');
  } catch (e) {}
  window.location.href = 'rendimientos.html';
});

const filtroDistribuidor = document.getElementById('filtroDistribuidor');
const periodoMes = document.getElementById('periodoMes');
const periodoAnio = document.getElementById('periodoAnio');
const incentivosRows = document.getElementById('incentivosRows');

const btnConsultar = document.getElementById('btnConsultar');
const btnAgregarDistribuidor = document.getElementById('btnAgregarDistribuidor');
const btnEliminarDistribuidor = document.getElementById('btnEliminarDistribuidor');
const btnReset = document.getElementById('btnReset');
const btnAplicar = document.getElementById('btnAplicar');
const btnGuardar = document.getElementById('btnGuardar');

const btnAbrirGraficaEficiencia = document.getElementById('btnAbrirGraficaEficiencia');
const eficienciaCanvasModal = document.getElementById('eficienciaResponsablesChartModal');

const btnDescargarEficienciaPdfModal = document.getElementById('btnDescargarEficienciaPdfModal');
const btnEnviarEficienciaWhatsappModal = document.getElementById('btnEnviarEficienciaWhatsappModal');
const btnEnviarEficienciaEmailModal = document.getElementById('btnEnviarEficienciaEmailModal');

const modalEficiencia = document.getElementById('modalEficiencia');
const btnCerrarModalEficiencia = document.getElementById('btnCerrarModalEficiencia');
const modalEnviarEficienciaEmail = document.getElementById('modalEnviarEficienciaEmail');
const btnCerrarModalEficienciaEmail = document.getElementById('btnCerrarModalEficienciaEmail');
const btnCancelarEnvioEficiencia = document.getElementById('btnCancelarEnvioEficiencia');
const btnConfirmarEnvioEficiencia = document.getElementById('btnConfirmarEnvioEficiencia');
const emailsInputEficiencia = document.getElementById('emailsInputEficiencia');

const resumenIncentivo = document.getElementById('resumenIncentivo');
const resumenBasesRend = document.getElementById('resumenBasesRend');
const resumenPctRend = document.getElementById('resumenPctRend');
const resumenBasesSem = document.getElementById('resumenBasesSem');
const resumenPctSem = document.getElementById('resumenPctSem');
const resumenBasesDes = document.getElementById('resumenBasesDes');
const resumenPctDes = document.getElementById('resumenPctDes');
const resumenBasesIns = document.getElementById('resumenBasesIns');
const resumenPctIns = document.getElementById('resumenPctIns');

let currentRows = [];
let distribuidorFilterTouched = false;
let eficienciaChart = null;
let eficienciaChartModal = null;
let lastEficienciaData = [];

const debugEnabled = (() => {
  try {
    const queryDebug = new URLSearchParams(window.location.search).get('debug');
    return queryDebug === '1' || localStorage.getItem('debugIncentivos') === '1';
  } catch (_error) {
    return false;
  }
})();

function dlog(...args) {
  if (debugEnabled) {
    console.log('[incentivos-debug]', ...args);
  }
}

function toNumber(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function toInt(value) {
  const n = Number(value);
  return Number.isInteger(n) ? n : 0;
}

function formatPercent(value) {
  return `${toNumber(value).toFixed(2)}%`;
}

function formatMoney(value) {
  return new Intl.NumberFormat('es-MX', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
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

function getChartTextColor() {
  const cssColor = getComputedStyle(document.documentElement).getPropertyValue('--text').trim();
  return cssColor || '#111111';
}

function getChartGridColor() {
  return document.documentElement.classList.contains('dark-theme')
    ? 'rgba(255, 255, 255, 0.14)'
    : 'rgba(17, 17, 17, 0.12)';
}

function getChartSurfaceColor() {
  const cssSurface = getComputedStyle(document.documentElement).getPropertyValue('--surface').trim();
  return cssSurface || '#ffffff';
}

function getCurrentPeriodLabel() {
  return `${toInt(periodoMes?.value)}/${toInt(periodoAnio?.value)}`;
}

function buildEficienciaByResponsable(rows) {
  const grouped = new Map();

  (Array.isArray(rows) ? rows : []).forEach((row) => {
    const responsable = String(row.responsable || 'SIN RESPONSABLE').trim() || 'SIN RESPONSABLE';
    const pctCumplidoRend = getPct(row.realRend, row.basesRend, row.pctRend);
    const pctCumplidoSem = getPct(row.realSem, row.basesSem, row.pctSem);
    const pctCumplidoDes = getPct(row.realDes, row.basesDes, row.pctDes);
    const pctCumplidoIns = getPct(row.realIns, row.basesIns, row.pctIns);

    const pagoTotal =
      getPago(row.baseIncentivo, pctCumplidoRend, row.pctRend) +
      getPago(row.baseIncentivo, pctCumplidoSem, row.pctSem) +
      getPago(row.baseIncentivo, pctCumplidoDes, row.pctDes) +
      getPago(row.baseIncentivo, pctCumplidoIns, row.pctIns);

    const incentivoBase = toNumber(row.baseIncentivo);

    if (!grouped.has(responsable)) {
      grouped.set(responsable, {
        responsable,
        incentivoBase: 0,
        pagoTotal: 0
      });
    }

    const item = grouped.get(responsable);
    item.incentivoBase += incentivoBase;
    item.pagoTotal += pagoTotal;
  });

  return Array.from(grouped.values())
    .map((item) => ({
      ...item,
      eficienciaPct: item.incentivoBase > 0 ? (item.pagoTotal / item.incentivoBase) * 100 : 0
    }))
    .sort((a, b) => b.eficienciaPct - a.eficienciaPct);
}

function renderEficienciaChartModal() {
  if (!eficienciaCanvasModal || !window.Chart) {
    return;
  }

  if (eficienciaChartModal) {
    eficienciaChartModal.destroy();
    eficienciaChartModal = null;
  }

  if (!lastEficienciaData.length) {
    return;
  }

  const labels = lastEficienciaData.map((item) => item.responsable);
  const values = lastEficienciaData.map((item) => Number(item.eficienciaPct.toFixed(2)));
  const barColors = values.map((value) => (value >= 80 ? '#16a34a' : value >= 55 ? '#f59e0b' : '#dc2626'));

  eficienciaChartModal = new Chart(eficienciaCanvasModal, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Eficiencia %',
          data: values,
          backgroundColor: barColors,
          borderColor: barColors,
          borderWidth: 1,
          borderRadius: 4
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (context) => `${context.parsed.x.toFixed(2)}%`
          }
        }
      },
      scales: {
        x: {
          min: 0,
          max: 100,
          ticks: {
            color: getChartTextColor(),
            callback: (value) => `${value}%`
          },
          grid: { color: getChartGridColor() }
        },
        y: {
          ticks: { color: getChartTextColor() },
          grid: { display: false }
        }
      }
    }
  });
}

function renderEficienciaChart(rows) {
  lastEficienciaData = buildEficienciaByResponsable(rows);

  if (!lastEficienciaData.length) {
    if (eficienciaChartModal) {
      eficienciaChartModal.destroy();
      eficienciaChartModal = null;
    }
    return;
  }

  if (!eficienciaCanvasModal || !window.Chart) {
    return;
  }

  if (eficienciaChartModal) {
    eficienciaChartModal.destroy();
    eficienciaChartModal = null;
  }

  const labels = lastEficienciaData.map((item) => item.responsable);
  const values = lastEficienciaData.map((item) => Number(item.eficienciaPct.toFixed(2)));
  const barColors = values.map((value) => (value >= 80 ? '#16a34a' : value >= 55 ? '#f59e0b' : '#dc2626'));

  eficienciaChartModal = new Chart(eficienciaCanvasModal, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Eficiencia %',
          data: values,
          backgroundColor: barColors,
          borderColor: barColors,
          borderWidth: 1,
          borderRadius: 4
        }
      ]
    },
    options: {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false
        }
      },
      scales: {
        x: {
          beginAtZero: true,
          max: 150,
          ticks: {
            callback: (value) => value.toFixed(0) + '%'
          }
        }
      }
    }
  });
}

function openEficienciaModal() {
  if (!modalEficiencia) {
    return;
  }

  if (!lastEficienciaData.length) {
    alert('No hay datos para mostrar en la gráfica.');
    return;
  }

  modalEficiencia.style.display = 'flex';
  renderEficienciaChartModal();
}

function closeEficienciaModal() {
  if (!modalEficiencia) {
    return;
  }

  modalEficiencia.style.display = 'none';

  if (eficienciaChartModal) {
    eficienciaChartModal.destroy();
    eficienciaChartModal = null;
  }
}

function getEficienciaCanvasForExport() {
  return eficienciaCanvasModal;
}

function base64ToBlob(base64, contentType) {
  const byteCharacters = atob(base64);
  const byteNumbers = new Array(byteCharacters.length);
  for (let i = 0; i < byteCharacters.length; i += 1) {
    byteNumbers[i] = byteCharacters.charCodeAt(i);
  }
  return new Blob([new Uint8Array(byteNumbers)], { type: contentType || 'application/octet-stream' });
}

async function generarPdfEficienciaBase64() {
  const jsPdfLib = window.jspdf?.jsPDF;
  if (!jsPdfLib) {
    throw new Error('No se pudo cargar la librería de PDF');
  }

  const sourceCanvas = getEficienciaCanvasForExport();
  if (!sourceCanvas || !lastEficienciaData.length) {
    throw new Error('No hay gráfica de eficiencia para exportar');
  }

  const pdf = new jsPdfLib({ orientation: 'landscape', unit: 'mm', format: 'a4' });
  const margin = 12;
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  const chartTitle = `Eficiencia por Responsable (${getCurrentPeriodLabel()})`;

  pdf.setFont('helvetica', 'bold');
  pdf.setFontSize(16);
  pdf.text(chartTitle, margin, 12);
  pdf.setFont('helvetica', 'normal');
  pdf.setFontSize(10);
  pdf.text(`Generado: ${new Date().toLocaleString('es-MX')}`, margin, 18);

  const exportCanvas = document.createElement('canvas');
  exportCanvas.width = sourceCanvas.width;
  exportCanvas.height = sourceCanvas.height;
  const exportCtx = exportCanvas.getContext('2d');
  if (!exportCtx) {
    throw new Error('No se pudo preparar la exportación de la gráfica');
  }

  exportCtx.fillStyle = getChartSurfaceColor();
  exportCtx.fillRect(0, 0, exportCanvas.width, exportCanvas.height);
  exportCtx.drawImage(sourceCanvas, 0, 0);

  const imageData = exportCanvas.toDataURL('image/png', 1.0);
  const availableWidth = pageWidth - margin * 2;
  const availableHeight = pageHeight - 28;
  const chartRatio = sourceCanvas.width / Math.max(1, sourceCanvas.height);

  let imageWidth = availableWidth;
  let imageHeight = imageWidth / chartRatio;
  if (imageHeight > availableHeight) {
    imageHeight = availableHeight;
    imageWidth = imageHeight * chartRatio;
  }

  const imageX = (pageWidth - imageWidth) / 2;
  const imageY = 24;
  pdf.addImage(imageData, 'PNG', imageX, imageY, imageWidth, imageHeight, undefined, 'FAST');

  return pdf.output('dataurlstring').split(',')[1];
}

async function descargarEficienciaPdf() {
  const pdfBase64 = await generarPdfEficienciaBase64();
  const pdfBlob = base64ToBlob(pdfBase64, 'application/pdf');
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
  const fileName = `eficiencia_responsables_${stamp}.pdf`;

  const downloadUrl = URL.createObjectURL(pdfBlob);
  const link = document.createElement('a');
  link.href = downloadUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000);
}

function abrirModalEnvioEficienciaEmail() {
  if (!modalEnviarEficienciaEmail) {
    return;
  }
  modalEnviarEficienciaEmail.style.display = 'flex';
  if (emailsInputEficiencia) {
    emailsInputEficiencia.value = '';
    emailsInputEficiencia.focus();
  }
}

function cerrarModalEnvioEficienciaEmail() {
  if (modalEnviarEficienciaEmail) {
    modalEnviarEficienciaEmail.style.display = 'none';
  }
  if (emailsInputEficiencia) {
    emailsInputEficiencia.value = '';
  }
}

async function confirmarEnvioEficienciaPorEmail() {
  const rawEmails = String(emailsInputEficiencia?.value || '').trim();
  if (!rawEmails) {
    alert('Por favor ingresa al menos un correo');
    return;
  }

  const emails = rawEmails
    .split(',')
    .map((email) => email.trim())
    .filter(Boolean);

  if (!emails.length) {
    alert('Ingresa correos válidos separados por comas');
    return;
  }

  const previousText = btnConfirmarEnvioEficiencia?.textContent;
  if (btnConfirmarEnvioEficiencia) {
    btnConfirmarEnvioEficiencia.disabled = true;
    btnConfirmarEnvioEficiencia.textContent = 'Enviando...';
  }

  try {
    const pdfBase64 = await generarPdfEficienciaBase64();
    const response = await fetch('/api/reports/send-chart-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        emails,
        subject: 'Eficiencia por Responsable - Incentivos',
        chartName: `Eficiencia por Responsable (${getCurrentPeriodLabel()})`,
        pdfBase64
      })
    });

    const result = await response.json();
    if (!response.ok) {
      throw new Error(result.message || 'No se pudo enviar el correo');
    }

    alert('Correo enviado correctamente');
    cerrarModalEnvioEficienciaEmail();
  } catch (error) {
    console.error('Error enviando correo de eficiencia:', error);
    alert('Error al enviar correo: ' + (error.message || 'Error desconocido'));
  } finally {
    if (btnConfirmarEnvioEficiencia) {
      btnConfirmarEnvioEficiencia.disabled = false;
      btnConfirmarEnvioEficiencia.textContent = previousText || 'Enviar';
    }
  }
}

async function enviarEficienciaPorWhatsapp(triggerButton) {
  if (!lastEficienciaData.length) {
    alert('Primero consulta los incentivos para generar la gráfica.');
    return;
  }

  const previousHtml = triggerButton?.innerHTML;
  if (triggerButton) {
    triggerButton.disabled = true;
    triggerButton.innerHTML = '...';
  }

  try {
    const pdfBase64 = await generarPdfEficienciaBase64();
    const pdfBlob = base64ToBlob(pdfBase64, 'application/pdf');
    const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
    const fileName = `eficiencia_responsables_${stamp}.pdf`;
    const message = 'Eficiencia por responsable generada desde Incentivos Yokohama.';

    if (window.isSecureContext && navigator.share) {
      const file = new File([pdfBlob], fileName, { type: 'application/pdf' });
      const canShareFiles = !navigator.canShare || navigator.canShare({ files: [file] });
      if (canShareFiles) {
        try {
          await navigator.share({
            title: 'Eficiencia por Responsable',
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
    const waUrl = phone ? `https://wa.me/${phone}?text=${waText}` : `https://wa.me/?text=${waText}`;
    window.open(waUrl, '_blank', 'noopener');
    alert('Se descargó el PDF. Solo adjúntalo en WhatsApp y envíalo.');
  } catch (error) {
    console.error('Error enviando gráfica de eficiencia por WhatsApp:', error);
    alert('No se pudo preparar el envío por WhatsApp');
  } finally {
    if (triggerButton) {
      triggerButton.disabled = false;
      triggerButton.innerHTML = previousHtml || triggerButton.innerHTML;
    }
  }
}

function metricLabel(metric) {
  if (metric === 'rend') return 'RENDIMIENTOS';
  if (metric === 'sem') return 'SEMAFOROS';
  if (metric === 'des') return 'PILAS DESECHO';
  if (metric === 'ins') return 'INSPECCIONES';
  return metric;
}

function openMapLocation(latValue, lonValue) {
  const lat = Number(latValue);
  const lon = Number(lonValue);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return false;
  }
  const url = `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`;
  window.open(url, '_blank', 'noopener,noreferrer');
  return true;
}

let realDetailModal = null;

function ensureRealDetailModal() {
  if (realDetailModal) {
    return realDetailModal;
  }

  const container = document.createElement('div');
  container.className = 'real-detail-modal';
  container.innerHTML = `
    <div class="real-detail-modal__backdrop" data-close="1"></div>
    <div class="real-detail-modal__dialog" role="dialog" aria-modal="true" aria-label="Detalle de REAL">
      <div class="real-detail-modal__header">
        <div>
          <h3 id="realDetailTitle">Detalle REAL</h3>
        </div>
        <button type="button" class="real-detail-modal__close" data-close="1" aria-label="Cerrar">x</button>
      </div>
      <div class="real-detail-modal__body" id="realDetailBody"></div>
    </div>
  `;

  container.addEventListener('click', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && target.getAttribute('data-close') === '1') {
      closeRealDetailModal();
      return;
    }

    if (target instanceof HTMLElement) {
      const locationButton = target.closest('.real-wheel-card__location');
      if (locationButton) {
        const lat = locationButton.getAttribute('data-lat');
        const lon = locationButton.getAttribute('data-lon');
        const opened = openMapLocation(lat, lon);
        if (!opened) {
          alert('Esta prueba no tiene coordenadas de ubicacion disponibles.');
        }
      }
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && realDetailModal?.classList.contains('open')) {
      closeRealDetailModal();
    }
  });

  document.body.appendChild(container);

  realDetailModal = {
    root: container,
    title: container.querySelector('#realDetailTitle'),
    body: container.querySelector('#realDetailBody')
  };

  return realDetailModal;
}

function closeRealDetailModal() {
  const modal = ensureRealDetailModal();
  modal.root.classList.remove('open');
}

function openRealDetailModal({ title, html }) {
  const modal = ensureRealDetailModal();
  modal.title.textContent = title;
  modal.body.innerHTML = html;
  modal.root.classList.add('open');
}

async function showRealVehiclesDetail(usuarioId, metric, responsable) {
  const mes = toInt(periodoMes?.value);
  const anio = toInt(periodoAnio?.value);

  if (!mes || !anio || !usuarioId || !metric) {
    return;
  }

  const url = `/api/incentivos/detalle-real?mes=${mes}&anio=${anio}&usuarioId=${usuarioId}&tipo=${encodeURIComponent(metric)}`;
  const detail = await fetchJsonFromEndpoint(url);
  const items = Array.isArray(detail.items) ? detail.items : [];

  const title = `${responsable} - ${metricLabel(metric)} (${mes}/${anio})`;

  if (!items.length) {
    openRealDetailModal({
      title,
      html: metric === 'des'
        ? '<p class="real-detail-empty">No se encontraron llantas de desecho para este usuario en el periodo seleccionado.</p>'
        : '<p class="real-detail-empty">No se encontraron vehiculos para este usuario en el periodo seleccionado.</p>'
    });
    return;
  }

  // 🔧 SOLUCIÓN PARA PILAS DESECHO: Filtrar para mostrar SOLO las llantas que se cuentan
  if (metric === 'des') {
    // Agrupar por número de llanta y tomar solo un registro por llanta
    const uniqueItemsMap = new Map();
    
    for (const item of items) {
      const llantaNumber = item.LlantasDesechoNoLlanta;
      
      // Si no existe esta llanta en el mapa, la agregamos
      if (!uniqueItemsMap.has(llantaNumber)) {
        uniqueItemsMap.set(llantaNumber, item);
      }
      // Si ya existe, NO la agregamos de nuevo (solo queremos una por llanta)
    }
    
    // Convertir el mapa a un array
    const uniqueItems = Array.from(uniqueItemsMap.values());
    
    // Debug: mostrar cuántos duplicados se eliminaron
    if (debugEnabled && items.length !== uniqueItems.length) {
      console.log(`[DEBUG] ${responsable}: ${items.length} registros totales, ${uniqueItems.length} llantas únicas (${items.length - uniqueItems.length} duplicados eliminados)`);
    }
    
    const desWheelCardsHtml = uniqueItems
      .map((entry) => {
        const llantaNumber = entry.LlantasDesechoNoLlanta;
        const llantaTitle = escapeHtml(llantaNumber != null && llantaNumber !== '' ? `Llanta #${llantaNumber}` : 'Llanta #N/D');
        const llantaSubtitle = escapeHtml([entry.llantaMarca, entry.llantaModelo].filter(Boolean).join(' ').trim() || 'Sin marca/modelo');
        const detailLine1 = escapeHtml(entry.detailLine1 || 'Sin detalle');
        const detailLine2 = escapeHtml(entry.detailLine2 || '');
        const statusColor = String(entry.statusColor || 'verde').toLowerCase();
        const dotClass = statusColor.includes('rojo') ? 'dot-red' : statusColor.includes('amar') ? 'dot-yellow' : statusColor.includes('gris') ? 'dot-gray' : 'dot-green';

        return `
          <div class="real-wheel-card">
            <div class="real-wheel-card__left">
              <span class="real-wheel-card__dot ${dotClass}" aria-hidden="true"></span>
              <div>
                <div class="real-wheel-card__title">${llantaTitle}</div>
                <div class="real-wheel-card__subtitle">${llantaSubtitle}</div>
                <div class="real-wheel-card__subtitle">${detailLine1}</div>
                ${detailLine2 ? `<div class="real-wheel-card__subtitle">${detailLine2}</div>` : ''}
              </div>
            </div>
            <div class="real-wheel-card__actions">
              <div class="real-wheel-card__alert" aria-hidden="true">${statusColor.includes('verde') ? '&#10003;' : '&#9888;'}</div>
            </div>
          </div>
        `;
      })
      .join('');

    openRealDetailModal({
      title,
      html: `
        <div class="real-detail-summary">
          Llantas contadas: ${uniqueItems.length}
        <div class="real-wheel-list">${desWheelCardsHtml}</div>
      `
    });
    return;
  }

  const groupedByVehicle = items.reduce((acc, item) => {
    const vehicleKey = String(item.vehiculoId || item.vehiculo || 'SIN VEHICULO').trim() || 'SIN VEHICULO';
    if (!acc.has(vehicleKey)) {
      acc.set(vehicleKey, []);
    }
    acc.get(vehicleKey).push(item);
    return acc;
  }, new Map());

  const vehicleCardsHtml = Array.from(groupedByVehicle.entries())
    .map(([vehicleName, entries], index) => {
      const detailItemsHtml = entries
        .map((entry, entryIndex) => {
          const llantaTitle = escapeHtml([entry.llantaMarca, entry.llantaModelo].filter(Boolean).join(' ').trim() || `Llanta ${entryIndex + 1}`);
          const detailLine1 = escapeHtml(entry.detailLine1 || 'Sin detalle');
          const detailLine2 = escapeHtml(entry.detailLine2 || '');
          const lat = Number(entry.latitude);
          const lon = Number(entry.longitude);
          const hasLocation = Number.isFinite(lat) && Number.isFinite(lon);
          const statusColor = String(entry.statusColor || 'verde').toLowerCase();
          const dotClass = statusColor.includes('rojo') ? 'dot-red' : statusColor.includes('amar') ? 'dot-yellow' : statusColor.includes('gris') ? 'dot-gray' : 'dot-green';
          return `
            <div class="real-wheel-card">
              <div class="real-wheel-card__left">
                <span class="real-wheel-card__dot ${dotClass}" aria-hidden="true"></span>
                <div>
                  <div class="real-wheel-card__title">${llantaTitle}</div>
                  <div class="real-wheel-card__subtitle">${detailLine1}</div>
                  ${detailLine2 ? `<div class="real-wheel-card__subtitle">${detailLine2}</div>` : ''}
                </div>
              </div>
              <div class="real-wheel-card__actions">
                <button type="button" class="real-wheel-card__location ${hasLocation ? '' : 'is-disabled'}" data-lat="${hasLocation ? lat : ''}" data-lon="${hasLocation ? lon : ''}" ${hasLocation ? '' : 'disabled'} title="${hasLocation ? 'Ver ubicacion' : 'Sin ubicacion registrada'}" aria-label="${hasLocation ? 'Ver ubicacion' : 'Sin ubicacion registrada'}">
                  <svg class="real-wheel-card__location-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M12 2C8.14 2 5 5.14 5 9c0 4.88 7 13 7 13s7-8.12 7-13c0-3.86-3.14-7-7-7zm0 9.5A2.5 2.5 0 1 1 12 6a2.5 2.5 0 0 1 0 5.5z"></path>
                  </svg>
                </button>
                <div class="real-wheel-card__alert" aria-hidden="true">${statusColor.includes('verde') ? '&#10003;' : '&#9888;'}</div>
              </div>
            </div>
          `;
        })
        .join('');

      const firstEntry = entries[0] || {};
      const vehicleTitle = escapeHtml(String(firstEntry.vehiculo || vehicleName || 'AUTO'));

      return `
        <details class="real-vehicle-card" ${index === 0 ? 'open' : ''}>
          <summary class="real-vehicle-card__summary">
            <div>
              <div class="real-vehicle-card__title">${vehicleTitle}</div>
              <div class="real-vehicle-card__subtitle">AUTO</div>
            </div>
            <div class="real-vehicle-card__meta">${entries.length} llantas</div>
          </summary>
          <div class="real-vehicle-card__content">
            ${detailItemsHtml}
          </div>
        </details>
      `;
    })
    .join('');

  openRealDetailModal({
    title,
    html: `
      <div class="real-detail-summary">Autos: ${groupedByVehicle.size} | Llantas: ${items.length}</div>
      <div class="real-vehicle-list">${vehicleCardsHtml}</div>
    `
  });
}

function getPct(real, bases, pctIncentivo) {
  if (!toNumber(bases)) {
    return 0;
  }
  // Calcula REAL como porcentaje de BASES (BASES = 100%), máximo 100%
  return Math.min((toNumber(real) / toNumber(bases)) * 100, 100);
}

function getPago(baseIncentivo, pctCumplido, pctIncentivo) {
  const pctPago = (toNumber(pctCumplido) / 100) * toNumber(pctIncentivo);
  return toNumber(baseIncentivo) * (pctPago / 100);
}

function getSelectedDistribuidorIds() {
  if (!filtroDistribuidor) {
    return [];
  }

  if (!distribuidorFilterTouched) {
    return [];
  }

  return Array.from(filtroDistribuidor.selectedOptions)
    .map((option) => toInt(option.value))
    .filter((id) => id > 0);
}

function setCurrentPeriod() {
  const now = new Date();
  if (periodoMes) {
    periodoMes.value = String(now.getMonth() + 1);
  }
  if (periodoAnio) {
    periodoAnio.value = String(now.getFullYear());
  }
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`Error ${response.status} en ${url}`);
  }
  return response.json();
}

function getAppBasePrefix() {
  const pathname = String(window.location.pathname || '');
  const marker = '/public/';
  const idx = pathname.toLowerCase().indexOf(marker);
  if (idx > 0) {
    return pathname.slice(0, idx);
  }
  return '';
}

const APP_BASE_PREFIX = getAppBasePrefix();

function resolveEndpointVariants(endpoint) {
  const normalized = String(endpoint || '').trim();
  if (!normalized) {
    return [];
  }

  const variants = [];
  const add = (value) => {
    const candidate = String(value || '').trim();
    if (candidate && !variants.includes(candidate)) {
      variants.push(candidate);
    }
  };

  add(normalized);

  if (normalized.startsWith('/')) {
    add(normalized.slice(1));
  }

  if (normalized.startsWith('/api/')) {
    const withoutApi = normalized.replace(/^\/api/, '');
    add(withoutApi);
    if (withoutApi.startsWith('/')) {
      add(withoutApi.slice(1));
    }
  }

  if (normalized.startsWith('/incentivos/')) {
    add(`/api${normalized}`);
  }

  if (APP_BASE_PREFIX && normalized.startsWith('/')) {
    add(`${APP_BASE_PREFIX}${normalized}`);

    if (normalized.startsWith('/api/')) {
      const withoutApi = normalized.replace(/^\/api/, '');
      add(`${APP_BASE_PREFIX}${withoutApi}`);
    }

    if (normalized.startsWith('/incentivos/')) {
      add(`${APP_BASE_PREFIX}/api${normalized}`);
    }
  }

  return variants;
}

async function fetchJsonFromEndpoint(endpoint, options) {
  const variants = resolveEndpointVariants(endpoint);
  let lastError = null;

  for (const variant of variants) {
    try {
      return await fetchJson(variant, options);
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError || new Error(`No se pudo resolver endpoint para ${endpoint}`);
}

async function loadDistribuidores() {
  if (!filtroDistribuidor) {
    return;
  }

  const endpoints = ['/api/incentivos/distribuidores', '/incentivos/distribuidores'];
  let distribuidores = [];
  let lastError = null;

  for (const endpoint of endpoints.flatMap(resolveEndpointVariants)) {
    try {
      distribuidores = await fetchJson(endpoint);
      break;
    } catch (error) {
      lastError = error;
    }
  }

  if (!Array.isArray(distribuidores)) {
    throw lastError || new Error('No se pudo cargar distribuidores.');
  }

  dlog('Distribuidores recibidos:', Array.isArray(distribuidores) ? distribuidores.length : 'payload no-array', distribuidores);
  filtroDistribuidor.innerHTML = (Array.isArray(distribuidores) ? distribuidores : [])
    .map(
      (item) =>
        `<option value="${toInt(item.idDistribuidor)}">${escapeHtml(item.DistribuidorNombre || 'Sin nombre')}</option>`
    )
    .join('');

  Array.from(filtroDistribuidor.options).forEach((option) => {
    option.selected = false;
  });
  distribuidorFilterTouched = false;
}

async function createDistribuidor(nombre) {
  const createEndpoints = [
    '/api/incentivos/distribuidores',
    '/api/incentivos/distribuidor',
    '/incentivos/distribuidores',
    '/incentivos/distribuidor'
  ];
  let lastError = null;

  for (const endpoint of createEndpoints.flatMap(resolveEndpointVariants)) {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nombre })
    });

    let payload = null;
    try {
      payload = await response.json();
    } catch (_error) {
      payload = null;
    }

    if (response.ok) {
      return payload;
    }

    if (response.status !== 404) {
      const message = payload?.message || `Error ${response.status} al agregar distribuidor.`;
      throw new Error(message);
    }

    lastError = payload?.message || `Recurso no encontrado en ${endpoint}`;
  }

  throw new Error(lastError || 'No se encontro una ruta valida para agregar distribuidor.');
}

async function deleteDistribuidor(distribuidorId) {
  const deleteRequests = [
    {
      endpoint: `/api/incentivos/distribuidores/${distribuidorId}`,
      options: { method: 'DELETE' }
    },
    {
      endpoint: `/api/incentivos/distribuidor/${distribuidorId}`,
      options: { method: 'DELETE' }
    },
    {
      endpoint: `/api/incentivos/distribuidores/${distribuidorId}/eliminar`,
      options: { method: 'POST' }
    },
    {
      endpoint: `/api/incentivos/distribuidor/${distribuidorId}/eliminar`,
      options: { method: 'POST' }
    },
    {
      endpoint: '/api/incentivos/eliminar-distribuidor',
      options: {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ distribuidorId })
      }
    }
  ];
  let lastError = null;

  const expandedDeleteRequests = deleteRequests.flatMap((request) =>
    resolveEndpointVariants(request.endpoint).map((endpoint) => ({ endpoint, options: request.options }))
  );

  for (const request of expandedDeleteRequests) {
    const response = await fetch(request.endpoint, request.options);

    let payload = null;
    try {
      payload = await response.json();
    } catch (_error) {
      payload = null;
    }

    if (response.ok) {
      return payload;
    }

    if (response.status !== 404) {
      const message = payload?.message || `Error ${response.status} al eliminar distribuidor.`;
      throw new Error(message);
    }

    lastError = payload?.message || `Recurso no encontrado en ${request.endpoint}`;
  }

  throw new Error(lastError || 'No se encontro una ruta valida para eliminar distribuidor.');
}

let deleteDistribuidorModal = null;

function ensureDeleteDistribuidorModal() {
  if (deleteDistribuidorModal) {
    return deleteDistribuidorModal;
  }

  const container = document.createElement('div');
  container.className = 'delete-distribuidor-modal';
  container.innerHTML = `
    <div class="delete-distribuidor-modal__backdrop" data-close="1"></div>
    <div class="delete-distribuidor-modal__dialog" role="dialog" aria-modal="true" aria-label="Eliminar distribuidor">
      <div class="delete-distribuidor-modal__header">
        <h3>Eliminar distribuidor</h3>
      </div>
      <div class="delete-distribuidor-modal__body">
        <label for="deleteDistribuidorSelect">Selecciona un distribuidor:</label>
        <select id="deleteDistribuidorSelect" class="field-control"></select>
      </div>
      <div class="delete-distribuidor-modal__actions">
        <button type="button" class="btn-action" id="btnDeleteDistribuidorCancel">Cancelar</button>
        <button type="button" class="btn-action btn-remove-distribuidor btn-icon-trash" id="btnDeleteDistribuidorConfirm" title="Eliminar distribuidor" aria-label="Eliminar distribuidor">
          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M9 3h6l1 2h4v2H4V5h4l1-2zm1 6h2v9h-2V9zm4 0h2v9h-2V9zM7 9h2v9H7V9z"></path>
          </svg>
        </button>
      </div>
    </div>
  `;

  document.body.appendChild(container);

  deleteDistribuidorModal = {
    root: container,
    select: container.querySelector('#deleteDistribuidorSelect'),
    cancelButton: container.querySelector('#btnDeleteDistribuidorCancel'),
    confirmButton: container.querySelector('#btnDeleteDistribuidorConfirm')
  };

  return deleteDistribuidorModal;
}

function closeDeleteDistribuidorModal() {
  const modal = ensureDeleteDistribuidorModal();
  modal.root.classList.remove('open');
}

function openDeleteDistribuidorModal() {
  const modal = ensureDeleteDistribuidorModal();

  if (!filtroDistribuidor || !filtroDistribuidor.options.length) {
    alert('No hay distribuidores disponibles para borrar.');
    return Promise.resolve(null);
  }

  modal.select.innerHTML = Array.from(filtroDistribuidor.options)
    .map((option) => `<option value="${escapeHtml(option.value)}">${escapeHtml(option.textContent || '')}</option>`)
    .join('');

  modal.root.classList.add('open');

  return new Promise((resolve) => {
    const onCancel = () => {
      cleanup();
      closeDeleteDistribuidorModal();
      resolve(null);
    };

    const onConfirm = () => {
      const selectedOption = modal.select.options[modal.select.selectedIndex];
      if (!selectedOption) {
        alert('Selecciona un distribuidor.');
        return;
      }

      cleanup();
      closeDeleteDistribuidorModal();
      resolve({
        id: toInt(selectedOption.value, 0),
        nombre: String(selectedOption.textContent || '').trim()
      });
    };

    const onBackdrop = (event) => {
      const target = event.target;
      if (target instanceof HTMLElement && target.getAttribute('data-close') === '1') {
        onCancel();
      }
    };

    const onEscape = (event) => {
      if (event.key === 'Escape') {
        onCancel();
      }
    };

    function cleanup() {
      modal.cancelButton.removeEventListener('click', onCancel);
      modal.confirmButton.removeEventListener('click', onConfirm);
      modal.root.removeEventListener('click', onBackdrop);
      document.removeEventListener('keydown', onEscape);
    }

    modal.cancelButton.addEventListener('click', onCancel);
    modal.confirmButton.addEventListener('click', onConfirm);
    modal.root.addEventListener('click', onBackdrop);
    document.addEventListener('keydown', onEscape);
  });
}

async function handleAgregarDistribuidor() {
  const nombre = prompt('Nombre del distribuidor nuevo:');
  if (nombre === null) {
    return;
  }

  const nombreLimpio = String(nombre).trim();
  if (!nombreLimpio) {
    alert('Ingresa un nombre de distribuidor valido.');
    return;
  }

  const created = await createDistribuidor(nombreLimpio);
  await loadDistribuidores();

  const createdId = toInt(created?.idDistribuidor, 0);
  if (filtroDistribuidor && createdId > 0) {
    const option = Array.from(filtroDistribuidor.options).find((item) => toInt(item.value) === createdId);
    if (option) {
      option.selected = true;
      distribuidorFilterTouched = true;
    }
  }

  await loadPreview();
  alert('Distribuidor agregado correctamente.');
}

function setSummaryFromConfig(config) {
  if (!config) {
    dlog('No existe config previa para el periodo/distribuidor; se reinicia el resumen.');
    if (resumenIncentivo) resumenIncentivo.value = '0';
    if (resumenBasesRend) resumenBasesRend.value = '0';
    if (resumenPctRend) resumenPctRend.value = '0';
    if (resumenBasesSem) resumenBasesSem.value = '0';
    if (resumenPctSem) resumenPctSem.value = '0';
    if (resumenBasesDes) resumenBasesDes.value = '0';
    if (resumenPctDes) resumenPctDes.value = '0';
    if (resumenBasesIns) resumenBasesIns.value = '0';
    if (resumenPctIns) resumenPctIns.value = '0';
    return;
  }

  dlog('Config previa aplicada:', config);

  if (resumenIncentivo) resumenIncentivo.value = toNumber(config.TotalIncentivo).toFixed(2);
  if (resumenBasesRend) resumenBasesRend.value = String(toInt(config.RBaseIncentivo));
  if (resumenPctRend) resumenPctRend.value = toNumber(config.RPorcenIncentivo).toFixed(2);
  if (resumenBasesSem) resumenBasesSem.value = String(toInt(config.SBaseIncentivo));
  if (resumenPctSem) resumenPctSem.value = toNumber(config.SPorcenIncentivo).toFixed(2);
  if (resumenBasesDes) resumenBasesDes.value = String(toInt(config.PBaseIncentivo));
  if (resumenPctDes) resumenPctDes.value = toNumber(config.PPorcenIncentivo).toFixed(2);
  if (resumenBasesIns) resumenBasesIns.value = String(toInt(config.IBaseIncentivo));
  if (resumenPctIns) resumenPctIns.value = toNumber(config.IPorcenIncentivo).toFixed(2);
}

function getSummaryValues() {
  return {
    baseIncentivo: toNumber(resumenIncentivo?.value),
    basesRend: toInt(resumenBasesRend?.value),
    pctRend: toNumber(resumenPctRend?.value),
    basesSem: toInt(resumenBasesSem?.value),
    pctSem: toNumber(resumenPctSem?.value),
    basesDes: toInt(resumenBasesDes?.value),
    pctDes: toNumber(resumenPctDes?.value),
    basesIns: toInt(resumenBasesIns?.value),
    pctIns: toNumber(resumenPctIns?.value)
  };
}

function applySummaryToRows() {
  const summary = getSummaryValues();

  currentRows = currentRows.map((row) => {
    if (row.isCustomized) {
      return row;
    }
    return {
      ...row,
      baseIncentivo: summary.baseIncentivo,
      basesRend: summary.basesRend,
      pctRend: summary.pctRend,
      basesSem: summary.basesSem,
      pctSem: summary.pctSem,
      basesDes: summary.basesDes,
      pctDes: summary.pctDes,
      basesIns: summary.basesIns,
      pctIns: summary.pctIns
    };
  });
}

function renderSummaryBases(rows) {
  const bases = rows.reduce(
    (acc, row) => {
      acc.rend += toNumber(row.basesRend);
      acc.sem += toNumber(row.basesSem);
      acc.des += toNumber(row.basesDes);
      acc.ins += toNumber(row.basesIns);
      return acc;
    },
    { rend: 0, sem: 0, des: 0, ins: 0 }
  );

  const setText = (id, value) => {
    const element = document.getElementById(id);
    if (element) {
      if ('value' in element) {
        element.value = String(value);
      } else {
        element.textContent = String(value);
      }
    }
  };

  setText('resumenBasesRend', bases.rend);
  setText('resumenBasesSem', bases.sem);
  setText('resumenBasesDes', bases.des);
  setText('resumenBasesIns', bases.ins);
}

function renderTotals(rows) {
  const summary = getSummaryValues();
  
  const totals = rows.reduce(
    (acc, row) => {
      const pctCumplidoRend = getPct(row.realRend, row.basesRend, row.pctRend);
      const pctCumplidoSem = getPct(row.realSem, row.basesSem, row.pctSem);
      const pctCumplidoDes = getPct(row.realDes, row.basesDes, row.pctDes);
      const pctCumplidoIns = getPct(row.realIns, row.basesIns, row.pctIns);

      const pagoRend = getPago(row.baseIncentivo, pctCumplidoRend, row.pctRend);
      const pagoSem = getPago(row.baseIncentivo, pctCumplidoSem, row.pctSem);
      const pagoDes = getPago(row.baseIncentivo, pctCumplidoDes, row.pctDes);
      const pagoIns = getPago(row.baseIncentivo, pctCumplidoIns, row.pctIns);

      acc.flotas += toNumber(row.flotas);
      acc.baseIncentivo += toNumber(row.baseIncentivo);

      acc.basesRend += toNumber(row.basesRend);
      acc.realRend += toNumber(row.realRend);
      acc.pagoRend += pagoRend;

      acc.basesSem += toNumber(row.basesSem);
      acc.realSem += toNumber(row.realSem);
      acc.pagoSem += pagoSem;

      acc.basesDes += toNumber(row.basesDes);
      acc.realDes += toNumber(row.realDes);
      acc.pagoDes += pagoDes;

      acc.basesIns += toNumber(row.basesIns);
      acc.realIns += toNumber(row.realIns);
      acc.pagoIns += pagoIns;
      
      acc.pagoTotal += pagoRend + pagoSem + pagoDes + pagoIns;
      return acc;
    },
    {
      flotas: 0,
      baseIncentivo: 0,
      basesRend: 0,
      realRend: 0,
      pagoRend: 0,
      basesSem: 0,
      realSem: 0,
      pagoSem: 0,
      basesDes: 0,
      realDes: 0,
      pagoDes: 0,
      basesIns: 0,
      realIns: 0,
      pagoIns: 0,
      pagoTotal: 0
    }
  );

  const setText = (id, value) => {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value;
    }
  };

  setText('totFlotas', String(totals.flotas));
  setText('totIncentivoBase', formatMoney(totals.baseIncentivo));
  setText('totBasesRend', String(totals.basesRend));
  setText('totRealRend', String(totals.realRend));
  setText('totPctRend', formatPercent(getPct(totals.realRend, totals.basesRend, summary.pctRend)));
  setText('totPagoRend', formatMoney(totals.pagoRend));

  setText('totBasesSem', String(totals.basesSem));
  setText('totRealSem', String(totals.realSem));
  setText('totPctSem', formatPercent(getPct(totals.realSem, totals.basesSem, summary.pctSem)));
  setText('totPagoSem', formatMoney(totals.pagoSem));

  setText('totBasesDes', String(totals.basesDes));
  setText('totRealDes', String(totals.realDes));
  setText('totPctDes', formatPercent(getPct(totals.realDes, totals.basesDes, summary.pctDes)));
  setText('totPagoDes', formatMoney(totals.pagoDes));

  setText('totBasesIns', String(totals.basesIns));
  setText('totRealIns', String(totals.realIns));
  setText('totPctIns', formatPercent(getPct(totals.realIns, totals.basesIns, summary.pctIns)));
  setText('totPagoIns', formatMoney(totals.pagoIns));
  setText('totPagoTotal', formatMoney(totals.pagoTotal));
}

function renderTable(rows) {
  if (!incentivosRows) {
    return;
  }

  if (!rows.length) {
    incentivosRows.innerHTML = '<tr><td colspan="26" class="loading-cell">No hay datos para los filtros seleccionados.</td></tr>';
    renderTotals([]);
    return;
  }

  incentivosRows.innerHTML = rows
    .map((row, index) => {
      const pctCumplidoRend = getPct(row.realRend, row.basesRend, row.pctRend);
      const pctCumplidoSem = getPct(row.realSem, row.basesSem, row.pctSem);
      const pctCumplidoDes = getPct(row.realDes, row.basesDes, row.pctDes);
      const pctCumplidoIns = getPct(row.realIns, row.basesIns, row.pctIns);

      const pagoRend = getPago(row.baseIncentivo, pctCumplidoRend, row.pctRend);
      const pagoSem = getPago(row.baseIncentivo, pctCumplidoSem, row.pctSem);
      const pagoDes = getPago(row.baseIncentivo, pctCumplidoDes, row.pctDes);
      const pagoIns = getPago(row.baseIncentivo, pctCumplidoIns, row.pctIns);
      const pagoTotal = pagoRend + pagoSem + pagoDes + pagoIns;

      const isZeroRow = toNumber(row.baseIncentivo) === 0 && 
                        toNumber(row.basesRend) === 0 && 
                        toNumber(row.basesSem) === 0 && 
                        toNumber(row.basesDes) === 0 && 
                        toNumber(row.basesIns) === 0;
      const rowStyle = isZeroRow ? 'style="opacity: 0.3;"' : '';

      return `
      <tr data-row-index="${index}" ${rowStyle}>
        <td>${escapeHtml(row.responsable)}</td>
        <td>${escapeHtml(row.perfil)}</td>
        <td>${escapeHtml(row.zona)}</td>
        <td>${toNumber(row.flotas)}</td>
        <td><input class="table-input row-base" type="number" min="0" step="0.01" value="${toNumber(row.baseIncentivo).toFixed(2)}" /></td>

        <td class="bg-rend"><input class="table-input row-bases-rend" type="number" min="0" step="1" value="${toNumber(row.basesRend)}" /></td>
        <td class="bg-rend"><button type="button" class="real-link" data-usuario-id="${toInt(row.usuarioId)}" data-metric="rend" data-responsable="${escapeHtml(row.responsable)}">${toNumber(row.realRend)}</button></td>
        <td class="bg-rend">${formatPercent(pctCumplidoRend)}</td>
        <td class="bg-rend"><input class="table-input row-pct-rend" type="number" min="0" step="0.01" value="${toNumber(row.pctRend).toFixed(2)}" /></td>
        <td class="bg-rend">${formatMoney(pagoRend)}</td>

        <td class="bg-sem"><input class="table-input row-bases-sem" type="number" min="0" step="1" value="${toNumber(row.basesSem)}" /></td>
        <td class="bg-sem"><button type="button" class="real-link" data-usuario-id="${toInt(row.usuarioId)}" data-metric="sem" data-responsable="${escapeHtml(row.responsable)}">${toNumber(row.realSem)}</button></td>
        <td class="bg-sem">${formatPercent(pctCumplidoSem)}</td>
        <td class="bg-sem"><input class="table-input row-pct-sem" type="number" min="0" step="0.01" value="${toNumber(row.pctSem).toFixed(2)}" /></td>
        <td class="bg-sem">${formatMoney(pagoSem)}</td>

        <td class="bg-des"><input class="table-input row-bases-des" type="number" min="0" step="1" value="${toNumber(row.basesDes)}" /></td>
        <td class="bg-des"><button type="button" class="real-link" data-usuario-id="${toInt(row.usuarioId)}" data-metric="des" data-responsable="${escapeHtml(row.responsable)}">${toNumber(row.realDes)}</button></td>
        <td class="bg-des">${formatPercent(pctCumplidoDes)}</td>
        <td class="bg-des"><input class="table-input row-pct-des" type="number" min="0" step="0.01" value="${toNumber(row.pctDes).toFixed(2)}" /></td>
        <td class="bg-des">${formatMoney(pagoDes)}</td>

        <td class="bg-ins"><input class="table-input row-bases-ins" type="number" min="0" step="1" value="${toNumber(row.basesIns)}" /></td>
        <td class="bg-ins"><button type="button" class="real-link" data-usuario-id="${toInt(row.usuarioId)}" data-metric="ins" data-responsable="${escapeHtml(row.responsable)}">${toNumber(row.realIns)}</button></td>
        <td class="bg-ins">${formatPercent(pctCumplidoIns)}</td>
        <td class="bg-ins"><input class="table-input row-pct-ins" type="number" min="0" step="0.01" value="${toNumber(row.pctIns).toFixed(2)}" /></td>
        <td class="bg-ins">${formatMoney(pagoIns)}</td>
        <td><strong>${formatMoney(pagoTotal)}</strong></td>
       </tr>`;
    })
    .join('');

  renderTotals(rows);
}

function recalculateAndRender() {
  renderTable(currentRows);
  renderEficienciaChart(currentRows);
}

function isRowDifferentFromSummary(row, summary) {
  return (
    toNumber(row.baseIncentivo) !== summary.baseIncentivo ||
    toInt(row.basesRend) !== summary.basesRend ||
    toNumber(row.pctRend) !== summary.pctRend ||
    toInt(row.basesSem) !== summary.basesSem ||
    toNumber(row.pctSem) !== summary.pctSem ||
    toInt(row.basesDes) !== summary.basesDes ||
    toNumber(row.pctDes) !== summary.pctDes ||
    toInt(row.basesIns) !== summary.basesIns ||
    toNumber(row.pctIns) !== summary.pctIns
  );
}

function updateRowFromInput(rowIndex, selector, value) {
  const row = currentRows[rowIndex];
  if (!row) {
    return;
  }

  const numericValue = toNumber(value);

  if (selector === 'row-base') row.baseIncentivo = numericValue;
  if (selector === 'row-bases-rend') row.basesRend = numericValue;
  if (selector === 'row-bases-sem') row.basesSem = numericValue;
  if (selector === 'row-bases-des') row.basesDes = numericValue;
  if (selector === 'row-bases-ins') row.basesIns = numericValue;
  if (selector === 'row-pct-rend') row.pctRend = numericValue;
  if (selector === 'row-pct-sem') row.pctSem = numericValue;
  if (selector === 'row-pct-des') row.pctDes = numericValue;
  if (selector === 'row-pct-ins') row.pctIns = numericValue;

  const summary = getSummaryValues();
  row.isCustomized = isRowDifferentFromSummary(row, summary);

  recalculateAndRender();
}

function buildRowsForSave() {
  return currentRows.map((row) => {
    const pctCumplidoRend = getPct(row.realRend, row.basesRend, row.pctRend);
    const pctCumplidoSem = getPct(row.realSem, row.basesSem, row.pctSem);
    const pctCumplidoDes = getPct(row.realDes, row.basesDes, row.pctDes);
    const pctCumplidoIns = getPct(row.realIns, row.basesIns, row.pctIns);

    const pagoRend = getPago(row.baseIncentivo, pctCumplidoRend, row.pctRend);
    const pagoSem = getPago(row.baseIncentivo, pctCumplidoSem, row.pctSem);
    const pagoDes = getPago(row.baseIncentivo, pctCumplidoDes, row.pctDes);
    const pagoIns = getPago(row.baseIncentivo, pctCumplidoIns, row.pctIns);

    return {
      usuarioId: row.usuarioId,
      responsable: row.responsable,
      distribuidorId: row.distribuidorId,
      baseIncentivo: toNumber(row.baseIncentivo),
      basesRend: toInt(row.basesRend),
      pctRend: toNumber(row.pctRend),
      pctCumplidoRend,
      pagoRend,
      basesSem: toInt(row.basesSem),
      pctSem: toNumber(row.pctSem),
      pctCumplidoSem,
      pagoSem,
      basesDes: toInt(row.basesDes),
      pctDes: toNumber(row.pctDes),
      pctCumplidoDes,
      pagoDes,
      basesIns: toInt(row.basesIns),
      pctIns: toNumber(row.pctIns),
      pctCumplidoIns,
      pagoIns,
      pagoTotal: pagoRend + pagoSem + pagoDes + pagoIns
    };
  });
}

async function loadConfigIfSingleDistribuidor() {
  const selectedIds = getSelectedDistribuidorIds();
  dlog('loadConfigIfSingleDistribuidor selectedIds=', selectedIds);
  if (selectedIds.length !== 1) {
    dlog('No se carga config porque no hay exactamente un distribuidor seleccionado.');
    return;
  }

  const mes = toInt(periodoMes?.value);
  const anio = toInt(periodoAnio?.value);
  if (!mes || !anio) {
    return;
  }

  const url = `/api/incentivos/config?mes=${mes}&anio=${anio}&distribuidorId=${selectedIds[0]}`;
  dlog('Consultando config URL:', url);
  const config = await fetchJsonFromEndpoint(url);
  setSummaryFromConfig(config);
}

async function loadPreview() {
  const mes = toInt(periodoMes?.value);
  const anio = toInt(periodoAnio?.value);
  const selectedDistribuidores = getSelectedDistribuidorIds();

  if (!mes || !anio) {
    alert('Selecciona un período válido.');
    return;
  }

  dlog('loadPreview filtros', { mes, anio, selectedDistribuidores });

  if (selectedDistribuidores.length === 1) {
    try {
      const configUrl = `/api/incentivos/config?mes=${mes}&anio=${anio}&distribuidorId=${selectedDistribuidores[0]}`;
      dlog('Cargando config del header:', configUrl);
      const config = await fetchJsonFromEndpoint(configUrl);
      setSummaryFromConfig(config);
    } catch (error) {
      dlog('No se pudo cargar config previa, usando valores actuales:', error);
    }
  } else {
    setSummaryFromConfig(null);
  }

  const params = new URLSearchParams({ mes: String(mes), anio: String(anio) });
  if (selectedDistribuidores.length) {
    params.set('distribuidorIds', selectedDistribuidores.join(','));
  }

  const previewUrl = `/api/incentivos/preview?${params.toString()}${debugEnabled ? '&debug=1' : ''}`;
  dlog('Consultando preview URL:', previewUrl);
  const previewRows = await fetchJsonFromEndpoint(previewUrl);
  dlog('Preview rows recibidas:', Array.isArray(previewRows) ? previewRows.length : 'payload no-array', previewRows);

  const summary = getSummaryValues();
  currentRows = (Array.isArray(previewRows) ? previewRows : []).map((row) => {
    const mappedRow = {
      ...row,
      baseIncentivo: row.baseIncentivo !== undefined && row.baseIncentivo !== null ? toNumber(row.baseIncentivo) : summary.baseIncentivo,
      basesRend: row.basesRend !== undefined && row.basesRend !== null ? toInt(row.basesRend) : summary.basesRend,
      pctRend: row.pctRend !== undefined && row.pctRend !== null ? toNumber(row.pctRend) : summary.pctRend,
      basesSem: row.basesSem !== undefined && row.basesSem !== null ? toInt(row.basesSem) : summary.basesSem,
      pctSem: row.pctSem !== undefined && row.pctSem !== null ? toNumber(row.pctSem) : summary.pctSem,
      basesDes: row.basesDes !== undefined && row.basesDes !== null ? toInt(row.basesDes) : summary.basesDes,
      pctDes: row.pctDes !== undefined && row.pctDes !== null ? toNumber(row.pctDes) : summary.pctDes,
      basesIns: row.basesIns !== undefined && row.basesIns !== null ? toInt(row.basesIns) : summary.basesIns,
      pctIns: row.pctIns !== undefined && row.pctIns !== null ? toNumber(row.pctIns) : summary.pctIns
    };
    mappedRow.isCustomized = isRowDifferentFromSummary(mappedRow, summary);
    return mappedRow;
  });

  const defaultsInfo = {
    resumenIncentivo: toNumber(resumenIncentivo?.value),
    resumenPctRend: toNumber(resumenPctRend?.value),
    resumenPctSem: toNumber(resumenPctSem?.value),
    resumenPctDes: toNumber(resumenPctDes?.value),
    resumenPctIns: toNumber(resumenPctIns?.value)
  };
  dlog('Valores de resumen usados para mapear filas (si están en 0, verás defaults):', defaultsInfo);

  recalculateAndRender();
}

function validatePercentagesTotal() {
  const rend = toNumber(resumenPctRend?.value);
  const sem = toNumber(resumenPctSem?.value);
  const des = toNumber(resumenPctDes?.value);
  const ins = toNumber(resumenPctIns?.value);

  const total = rend + sem + des + ins;
  const tolerance = 0.01;

  if (Math.abs(total - 100) > tolerance) {
    alert(`Los porcentajes de incentivo no suman 100%.\nActualmente suman: ${total.toFixed(2)}%\n\nPor favor ajusta los valores.`);
    return false;
  }

  return true;
}

async function guardarIncentivos(options = {}) {
  const { showSuccessAlert = true } = options;

  applySummaryToRows();

  if (!validatePercentagesTotal()) {
    return;
  }

  const selectedDistribuidores = getSelectedDistribuidorIds();

  if (selectedDistribuidores.length > 1) {
    alert('Para guardar, selecciona un solo distribuidor o ninguno para guardar todos.');
    return;
  }

  if (!currentRows.length) {
    alert('No hay filas para guardar.');
    return;
  }

  const mes = toInt(periodoMes?.value);
  const anio = toInt(periodoAnio?.value);

  const payload = {
    mes,
    anio,
    distribuidorId: selectedDistribuidores.length === 1 ? selectedDistribuidores[0] : null,
    totalIncentivo: toNumber(resumenIncentivo?.value),
    resumenBasesRend: toInt(resumenBasesRend?.value),
    resumenPctRend: toNumber(resumenPctRend?.value),
    resumenBasesSem: toInt(resumenBasesSem?.value),
    resumenPctSem: toNumber(resumenPctSem?.value),
    resumenBasesDes: toInt(resumenBasesDes?.value),
    resumenPctDes: toNumber(resumenPctDes?.value),
    resumenBasesIns: toInt(resumenBasesIns?.value),
    resumenPctIns: toNumber(resumenPctIns?.value),
    rows: buildRowsForSave()
  };

  await fetchJsonFromEndpoint('/api/incentivos/guardar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (showSuccessAlert) {
    alert('Incentivos guardados correctamente.');
  }
}

btnAbrirGraficaEficiencia?.addEventListener('click', () => {
  openEficienciaModal();
});

btnCerrarModalEficiencia?.addEventListener('click', closeEficienciaModal);

modalEficiencia?.addEventListener('click', (event) => {
  if (event.target === modalEficiencia) {
    closeEficienciaModal();
  }
});

btnEnviarEficienciaWhatsappModal?.addEventListener('click', async () => {
  await enviarEficienciaPorWhatsapp(btnEnviarEficienciaWhatsappModal);
});

btnEnviarEficienciaEmailModal?.addEventListener('click', () => {
  abrirModalEnvioEficienciaEmail();
});

btnDescargarEficienciaPdfModal?.addEventListener('click', async () => {
  try {
    await descargarEficienciaPdf();
  } catch (error) {
    console.error(error);
    alert('No se pudo descargar la gráfica de eficiencia.');
  }
});

btnCerrarModalEficienciaEmail?.addEventListener('click', cerrarModalEnvioEficienciaEmail);
btnCancelarEnvioEficiencia?.addEventListener('click', cerrarModalEnvioEficienciaEmail);
btnConfirmarEnvioEficiencia?.addEventListener('click', confirmarEnvioEficienciaPorEmail);

modalEnviarEficienciaEmail?.addEventListener('click', (event) => {
  if (event.target === modalEnviarEficienciaEmail) {
    cerrarModalEnvioEficienciaEmail();
  }
});

btnConsultar?.addEventListener('click', async () => {
  try {
    await loadConfigIfSingleDistribuidor();
    await loadPreview();
  } catch (error) {
    console.error(error);
    alert('No se pudieron consultar los incentivos.');
  }
});

btnAgregarDistribuidor?.addEventListener('click', async () => {
  try {
    await handleAgregarDistribuidor();
  } catch (error) {
    console.error(error);
    alert(error?.message || 'No se pudo agregar el distribuidor.');
  }
});

btnEliminarDistribuidor?.addEventListener('click', async () => {
  try {
    const selected = await openDeleteDistribuidorModal();
    if (!selected) {
      return;
    }

    if (!selected.id) {
      alert('No se pudo identificar el distribuidor seleccionado.');
      return;
    }

    const confirmDelete = confirm(`Estas seguro de que quieres borrar el distribuidor "${selected.nombre}"?`);
    if (!confirmDelete) {
      return;
    }

    await deleteDistribuidor(selected.id);
    await loadDistribuidores();
    await loadPreview();
    alert('Distribuidor eliminado correctamente.');
  } catch (error) {
    console.error(error);
    alert(error?.message || 'No se pudo eliminar el distribuidor.');
  }
});

btnReset?.addEventListener('click', async () => {
  if (filtroDistribuidor) {
    Array.from(filtroDistribuidor.options).forEach((option) => {
      option.selected = false;
    });
  }
  distribuidorFilterTouched = false;

  setCurrentPeriod();
  if (resumenIncentivo) resumenIncentivo.value = '0';
  if (resumenPctRend) resumenPctRend.value = '0';
  if (resumenPctSem) resumenPctSem.value = '0';
  if (resumenPctDes) resumenPctDes.value = '0';
  if (resumenPctIns) resumenPctIns.value = '0';

  try {
    await loadPreview();
  } catch (error) {
    console.error(error);
    currentRows = [];
    recalculateAndRender();
  }
});

filtroDistribuidor?.addEventListener('change', () => {
  distribuidorFilterTouched = true;
});

periodoMes?.addEventListener('change', async () => {
  try {
    await loadPreview();
  } catch (error) {
    console.error(error);
  }
});

periodoAnio?.addEventListener('change', async () => {
  try {
    await loadPreview();
  } catch (error) {
    console.error(error);
  }
});

btnAplicar?.addEventListener('click', async () => {
  applySummaryToRows();
  recalculateAndRender();

  const selectedDistribuidores = getSelectedDistribuidorIds();
  if (selectedDistribuidores.length === 1) {
    try {
      await guardarIncentivos({ showSuccessAlert: false });
      dlog('Aplicar: guardado automático correcto para distribuidor', selectedDistribuidores[0]);
    } catch (error) {
      console.error(error);
      alert('Se aplicaron los cambios en pantalla, pero no se pudieron guardar en la base de datos.');
    }
  }
});

btnGuardar?.addEventListener('click', async () => {
  try {
    await guardarIncentivos();
  } catch (error) {
    console.error(error);
    alert('No se pudieron guardar los incentivos.');
  }
});

incentivosRows?.addEventListener('input', (event) => {
  const target = event.target;
  if (!(target instanceof HTMLInputElement)) {
    return;
  }

  const rowElement = target.closest('tr[data-row-index]');
  if (!rowElement) {
    return;
  }

  const rowIndex = toInt(rowElement.getAttribute('data-row-index'));

  if (target.classList.contains('row-base')) {
    updateRowFromInput(rowIndex, 'row-base', target.value);
  }
  if (target.classList.contains('row-bases-rend')) {
    updateRowFromInput(rowIndex, 'row-bases-rend', target.value);
  }
  if (target.classList.contains('row-bases-sem')) {
    updateRowFromInput(rowIndex, 'row-bases-sem', target.value);
  }
  if (target.classList.contains('row-bases-des')) {
    updateRowFromInput(rowIndex, 'row-bases-des', target.value);
  }
  if (target.classList.contains('row-bases-ins')) {
    updateRowFromInput(rowIndex, 'row-bases-ins', target.value);
  }
  if (target.classList.contains('row-pct-rend')) {
    updateRowFromInput(rowIndex, 'row-pct-rend', target.value);
  }
  if (target.classList.contains('row-pct-sem')) {
    updateRowFromInput(rowIndex, 'row-pct-sem', target.value);
  }
  if (target.classList.contains('row-pct-des')) {
    updateRowFromInput(rowIndex, 'row-pct-des', target.value);
  }
  if (target.classList.contains('row-pct-ins')) {
    updateRowFromInput(rowIndex, 'row-pct-ins', target.value);
  }
});

incentivosRows?.addEventListener('click', async (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) {
    return;
  }

  const realButton = target.closest('.real-link');
  if (!realButton) {
    return;
  }

  const usuarioId = toInt(realButton.getAttribute('data-usuario-id'));
  const metric = String(realButton.getAttribute('data-metric') || '').trim();
  const responsable = String(realButton.getAttribute('data-responsable') || 'usuario').trim();

  try {
    await showRealVehiclesDetail(usuarioId, metric, responsable);
  } catch (error) {
    console.error(error);
    alert('No se pudo obtener el detalle de vehiculos para este REAL.');
  }
});

function updateSummaryPercentages() {
  if (!resumenPctRend || !resumenPctSem || !resumenPctDes || !resumenPctIns) {
    return;
  }

  const rend = toNumber(resumenPctRend.value);
  const sem = toNumber(resumenPctSem.value);
  const des = toNumber(resumenPctDes.value);

  const sum = rend + sem + des;
  const remaining = Math.max(0, 100 - sum);

  resumenPctIns.value = remaining.toFixed(2);
}

resumenPctRend?.addEventListener('change', updateSummaryPercentages);
resumenPctRend?.addEventListener('input', updateSummaryPercentages);

resumenPctSem?.addEventListener('change', updateSummaryPercentages);
resumenPctSem?.addEventListener('input', updateSummaryPercentages);

resumenPctDes?.addEventListener('change', updateSummaryPercentages);
resumenPctDes?.addEventListener('input', updateSummaryPercentages);

async function initialize() {
  try {
    dlog('Debug incentivos activo. Usa ?debug=1 o localStorage.debugIncentivos=1');
    setCurrentPeriod();
    await loadDistribuidores();
    await loadPreview();
    updateSummaryPercentages();
  } catch (error) {
    console.error('Error loading incentivos page:', error);
    if (incentivosRows) {
      incentivosRows.innerHTML = '<tr><td colspan="25" class="loading-cell">Error al cargar datos de incentivos.</td></tr>';
    }
  }
}

initialize();