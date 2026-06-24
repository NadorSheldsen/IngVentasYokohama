// desecho.js - Lógica para la página de Pila de Desecho

// Theme management
function toggleTheme() {
  const isDark = document.documentElement.classList.toggle('dark-theme');
  localStorage.setItem('theme', isDark ? 'dark' : 'light');
  updateThemeIcon();
  updateLogo();
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
  // Header navigation: do NOT persist flota context — open rendimientos sin filtro
  try {
    localStorage.removeItem('selectedFlotaForRendimientos');
    localStorage.removeItem('selectedFlotaContext');
  } catch (e) {}
  window.location.href = 'rendimientos.html';
});

// Data variables
let allLlantasDesecho = [];
let allPruebasDesecho = [];
let filteredLlantasDesecho = [];
let currentView = 'MM_DESECHADOS'; // Default view
let currentCharts = [];
let selectedPruebaId = null; // Track selected prueba for filtering table

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
const desechoListContainer = document.getElementById('desechoList');

function getCurrentFlotaContext() {
  const uniqueFlotas = Array.from(new Set(
    filteredLlantasDesecho
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
    console.log('⚠️ getPhotoSrc: No value provided');
    return '';
  }
  const stringValue = String(value).trim();
  if (!stringValue) {
    console.log('⚠️ getPhotoSrc: Empty string after trim');
    return '';
  }
  
  let result;
  if (stringValue.startsWith('data:image')) {
    result = stringValue;
    console.log('✅ getPhotoSrc: Already has data URI prefix');
  } else {
    // Check if this is double-encoded base64 by trying to decode once
    try {
      const testDecode = atob(stringValue.substring(0, 40));
      console.log('🔍 Test decode first 40 chars:', testDecode);
      
      // If decoded result starts with /9j/ it's a JPEG header in ASCII (meaning double-encoded)
      if (testDecode.startsWith('/9j/')) {
        console.log('⚠️ DOUBLE-ENCODED detected! Decoding once before use...');
        // Decode the entire base64 string once
        const decoded = atob(stringValue);
        result = `data:image/jpeg;base64,${decoded}`;
        console.log('✅ Used decoded base64. New length:', result.length);
      } else {
        // Normal encoding
        result = `data:image/jpeg;base64,${stringValue}`;
        console.log('✅ Normal encoding. Total length:', result.length);
      }
    } catch (e) {
      console.log('ℹ️ Decode test failed, using as-is:', e.message);
      result = `data:image/jpeg;base64,${stringValue}`;
    }
  }
  
  return result;
}

function setModalImage(targetImg, targetEmpty, src) {
  if (!targetImg || !targetEmpty) {
    console.error('❌ setModalImage: Missing img or empty element');
    return;
  }
  if (src) {
    console.log('🖼️ setModalImage: Setting image source', {
      srcLength: src.length,
      srcStart: src.substring(0, 50),
      targetImgId: targetImg.id
    });
    
    targetImg.src = src;
    targetImg.style.display = 'block';
    targetEmpty.style.display = 'none';
    
    // Handle image load errors
    targetImg.onerror = function() {
      console.error('❌ Image failed to load for', targetImg.id);
      targetImg.style.display = 'none';
      targetEmpty.style.display = 'block';
    };
    
    targetImg.onload = function() {
      console.log('✅ Image loaded successfully for', targetImg.id, {
        width: targetImg.naturalWidth,
        height: targetImg.naturalHeight
      });
    };
  } else {
    console.log('ℹ️ setModalImage: No source, showing empty state');
    targetImg.removeAttribute('src');
    targetImg.style.display = 'none';
    targetEmpty.style.display = 'block';
  }
}

function openPhotoModal(foto1, foto2) {
  if (!photoModal) {
    console.error('❌ Photo modal element not found');
    return;
  }
  console.log('📸 Opening photo modal', { 
    foto1Length: foto1 ? foto1.length : 0,
    foto1Type: foto1 ? typeof foto1 : 'null',
    foto2Length: foto2 ? foto2.length : 0,
    foto2Type: foto2 ? typeof foto2 : 'null'
  });
  
  setModalImage(photoModalImg1, photoModalEmpty1, getPhotoSrc(foto1));
  setModalImage(photoModalImg2, photoModalEmpty2, getPhotoSrc(foto2));
  
  photoModal.classList.add('open');
  photoModal.setAttribute('aria-hidden', 'false');
}

function closePhotoModal() {
  if (!photoModal) {
    return;
  }
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

// Fetch data from API
async function fetchLlantasDesecho() {
  try {
    let url = '/api/llantas-desecho';
    const savedFlota = localStorage.getItem('selectedFlotaForDesecho') || localStorage.getItem('selectedFlotaContext');
    if (savedFlota) {
      url += '?flotaName=' + encodeURIComponent(savedFlota);
    }
    const response = await fetch(url);
    if (!response.ok) throw new Error('Error fetching llantas desecho');
    const data = await response.json();
    
    // Log photo data for debugging
    const photoCounts = data.reduce((acc, item) => {
      if (item.LlantasDesechoFoto1) acc.foto1++;
      if (item.LlantasDesechoFoto2) acc.foto2++;
      return acc;
    }, { foto1: 0, foto2: 0 });
    console.log(`Loaded ${data.length} llantas desecho. Photos: ${photoCounts.foto1} foto1, ${photoCounts.foto2} foto2`);
    
    // Find first item with photos
    const sampleWithPhoto = data.find(item => item.LlantasDesechoFoto1 || item.LlantasDesechoFoto2);
    if (sampleWithPhoto) {
      const foto1 = sampleWithPhoto.LlantasDesechoFoto1;
      console.log('🔍 Frontend received photo data:', {
        id: sampleWithPhoto.idLlantasDesecho,
        foto1Exists: !!foto1,
        foto1Type: foto1 ? typeof foto1 : 'null',
        foto1Length: foto1 ? foto1.length : 0,
        foto1Start: foto1 ? foto1.substring(0, 50) : 'null',
        foto1End: foto1 ? '...' + foto1.substring(foto1.length - 50) : 'null',
        foto2Exists: !!sampleWithPhoto.LlantasDesechoFoto2,
        foto2Length: sampleWithPhoto.LlantasDesechoFoto2 ? sampleWithPhoto.LlantasDesechoFoto2.length : 0
      });
      
      // Test if it looks like valid base64
      if (foto1) {
        const isBase64 = /^[A-Za-z0-9+/=]+$/.test(foto1);
        const startsWithDataUri = foto1.startsWith('data:image');
        console.log('🔍 Photo validation:', {
          isBase64: isBase64,
          startsWithDataUri: startsWithDataUri,
          shouldPrependDataUri: !startsWithDataUri && isBase64
        });
      }
    } else {
      console.warn('⚠️ No photos found in any llanta desecho');
    }
    return data;
  } catch (error) {
    console.error('❌ Error fetching data:', error);
    return [];
  }
}

async function fetchPruebasDesecho() {
  try {
    let url = '/api/pruebas-desecho';
    const savedFlota = localStorage.getItem('selectedFlotaForDesecho') || localStorage.getItem('selectedFlotaContext');
    if (savedFlota) {
      url += '?flotaName=' + encodeURIComponent(savedFlota);
    }
    const response = await fetch(url);
    if (!response.ok) throw new Error('Error fetching pruebas desecho');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('Error fetching pruebas desecho:', error);
    return [];
  }
}

// Apply flota filter if available
function applyFlotaFilter() {
  const savedFlota = localStorage.getItem('selectedFlotaForDesecho') || localStorage.getItem('selectedFlotaContext');
  if (savedFlota) {
    console.log('Filtrando por flota:', savedFlota);

    const normalizedSavedFlota = String(savedFlota).trim().toLowerCase();

    // 1) Obtener pruebas de desecho que pertenecen a la flota seleccionada.
    const matchingPruebaIds = new Set(
      allPruebasDesecho
        .filter((prueba) => String(prueba?.FlotasNombre || '').trim().toLowerCase().includes(normalizedSavedFlota))
        .map((prueba) => String(prueba?.idPruebasDesecho || '').trim())
        .filter(Boolean)
    );

    // 2) Filtrar por flota en llanta O por pertenecer a prueba de esa flota.
    filteredLlantasDesecho = allLlantasDesecho.filter((llanta) => {
      const llantaFlota = String(llanta?.FlotasNombre || '').trim().toLowerCase();
      const pruebaId = String(llanta?.PruebasDesecho_idPruebasDesecho || '').trim();
      return (llantaFlota && llantaFlota.includes(normalizedSavedFlota)) || matchingPruebaIds.has(pruebaId);
    });

    console.log('Resultado filtro desecho:', {
      total: allLlantasDesecho.length,
      filtradas: filteredLlantasDesecho.length,
      pruebasRelacionadas: matchingPruebaIds.size
    });

    localStorage.setItem('selectedFlotaContext', savedFlota);
    localStorage.removeItem('selectedFlotaForDesecho');
  } else {
    filteredLlantasDesecho = [...allLlantasDesecho];
  }
}

function getPruebaNombreById(pruebaId) {
  const targetId = String(pruebaId || '').trim();
  if (!targetId) return '';

  const prueba = allPruebasDesecho.find(
    (item) => String(item?.idPruebasDesecho || '').trim() === targetId
  );

  return String(
    prueba?.PruebasDesechoNombre || prueba?.PruebasDesechoTitulo || prueba?.PruebaDesechoNombre || ''
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

// Select a chart (prueba) and filter table accordingly
function selectChart(pruebaId) {
  // Toggle selection: if clicking the same chart, deselect it
  if (selectedPruebaId === pruebaId) {
    selectedPruebaId = null;
  } else {
    selectedPruebaId = pruebaId;
  }
  
  // Update chart visual states
  document.querySelectorAll('.chart-card').forEach(card => {
    if (card.dataset.pruebaId === pruebaId && selectedPruebaId === pruebaId) {
      card.classList.add('selected');
    } else {
      card.classList.remove('selected');
    }
  });
  
  // Re-render table with filtered data
  renderTable();
  updateTotalCount();
}

// Render charts based on current view
function renderCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  if (!chartsContainer) return;

  // Destroy existing charts
  currentCharts.forEach(chart => chart.destroy());
  currentCharts = [];
  chartsContainer.innerHTML = '';

  if (currentView === 'MM_DESECHADOS') {
    renderMMDesechadosCharts();
  } else if (currentView === 'PARTICIPACION_MARCA') {
    renderParticipacionMarcaCharts();
  } else if (currentView === 'TIPO_PISO') {
    renderTipoPisoCharts();
  } else if (currentView === 'CAUSAS_TOP5') {
    renderCausasTop5Charts();
  } else if (currentView === 'CAUSAS_TOTAL') {
    renderCausasTotalCharts();
  } else if (currentView === 'ESTADISTICA_DESECHO') {
    renderEstadisticaDesechoCharts();
  }

  enrichChartTitlesWithPruebaName();
}

function renderMMDesechadosCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  
  // Group data by prueba desecho (PruebasDesecho_idPruebasDesecho)
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        mmCounts: {}
      };
    }
    
    const mm = llanta.LlantasDesechoRemanente || 0;
    dataByPrueba[pruebaId].mmCounts[mm] = (dataByPrueba[pruebaId].mmCounts[mm] || 0) + 1;
  });

  // Get all pruebas sorted by date (most recent first)
  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

  // Render chart for each prueba
  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;
    
    // Add selected class if this chart is selected
    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }
    
    // Add click event to select chart
    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });
    
    const title = document.createElement('h3');
    title.textContent = `MM DESECHADOS\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const mmData = prueba.mmCounts;
    const labels = Object.keys(mmData).sort((a, b) => b - a);
    const data = labels.map(mm => mmData[mm]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    // Generate distinct colors for each bar
    const colors = [
      '#4CAF50', '#2196F3', '#FFC107', '#FF9800', '#F44336',
      '#9C27B0', '#00BCD4', '#8BC34A', '#FF5722', '#607D8B',
      '#E91E63', '#3F51B5', '#009688', '#CDDC39', '#795548'
    ];

    const chart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: labels.map(mm => mm + ' mm'),
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
            ticks: { 
              color: labelColor, 
              stepSize: 1,
              font: { size: 10 }
            },
            grid: {
              color: isDarkTheme ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)'
            }
          },
          x: {
            ticks: { 
              color: labelColor, 
              font: { size: 9 }
            },
            grid: {
              display: false
            }
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
  
  // Group data by prueba desecho
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        marcaCounts: {}
      };
    }
    
    const marca = llanta.LlantasMarca || 'Sin marca';
    dataByPrueba[pruebaId].marcaCounts[marca] = (dataByPrueba[pruebaId].marcaCounts[marca] || 0) + 1;
  });

  // Get all pruebas sorted by date (most recent first)
  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

  // Render chart for each prueba
  sortedPruebas.forEach(pruebaId => {
    const prueba = dataByPrueba[pruebaId];
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.dataset.pruebaId = pruebaId;
    
    // Add selected class if this chart is selected
    if (selectedPruebaId === pruebaId) {
      chartCard.classList.add('selected');
    }
    
    // Add click event to select chart
    chartCard.style.cursor = 'pointer';
    chartCard.addEventListener('click', () => {
      selectChart(pruebaId);
    });
    
    const title = document.createElement('h3');
    title.textContent = `PARTICIPACIÓN POR MARCA\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const marcaData = prueba.marcaCounts;
    const labels = Object.keys(marcaData).sort((a, b) => marcaData[b] - marcaData[a]);
    const data = labels.map(marca => marcaData[marca]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    // Generate distinct colors for each brand
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
                    // Truncate label if longer than 20 characters
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
                return labels[index]; // Muestra la marca completa
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

function renderTipoPisoCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  
  // Group data by prueba desecho
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        pisoCounts: {}
      };
    }
    
    const piso = llanta.LlantasDesechoPiso || 'Sin especificar';
    dataByPrueba[pruebaId].pisoCounts[piso] = (dataByPrueba[pruebaId].pisoCounts[piso] || 0) + 1;
  });

  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

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
    title.textContent = `TIPO DE PISO\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const pisoData = prueba.pisoCounts;
    const labels = Object.keys(pisoData).sort((a, b) => pisoData[b] - pisoData[a]);
    const data = labels.map(piso => pisoData[piso]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const colors = ['#4CAF50', '#2196F3', '#FFC107', '#FF9800', '#F44336'];

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
                    // Truncate label if longer than 20 characters
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
                return labels[index]; // Muestra el tipo de piso completo
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

function renderCausasTop5Charts() {
  const chartsContainer = document.getElementById('chartsContainer');
  
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        causaCounts: {}
      };
    }
    
    const causa = llanta.LlantasDesechoCausaDes || 'Sin especificar';
    dataByPrueba[pruebaId].causaCounts[causa] = (dataByPrueba[pruebaId].causaCounts[causa] || 0) + 1;
  });

  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

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
    title.textContent = `CAUSAS DESECHO TOP 5\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const causaData = prueba.causaCounts;
    const allCausas = Object.keys(causaData).sort((a, b) => causaData[b] - causaData[a]);
    const top5Causas = allCausas.slice(0, 5); // Only top 5
    const labels = top5Causas.map(causa => causa.length > 15 ? causa.substring(0, 15) + '...' : causa);
    const data = top5Causas.map(causa => causaData[causa]);

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
          backgroundColor: colors,
          borderWidth: 1,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { 
            enabled: true,
            callbacks: {
              title: function(context) {
                const index = context[0].dataIndex;
                return top5Causas[index]; // Muestra la causa completa
              }
            }
          },
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

function renderCausasTotalCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        causaCounts: {}
      };
    }
    
    const causa = llanta.LlantasDesechoCausaDes || 'Sin especificar';
    dataByPrueba[pruebaId].causaCounts[causa] = (dataByPrueba[pruebaId].causaCounts[causa] || 0) + 1;
  });

  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

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
    title.textContent = `CAUSAS DESECHO TOTAL\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const canvas = document.createElement('canvas');
    chartCard.appendChild(canvas);
    chartsContainer.appendChild(chartCard);

    const causaData = prueba.causaCounts;
    const allCausas = Object.keys(causaData).sort((a, b) => causaData[b] - causaData[a]);
    const labels = allCausas.map(causa => causa.length > 15 ? causa.substring(0, 15) + '...' : causa);
    const data = allCausas.map(causa => causaData[causa]);

    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const labelColor = isDarkTheme ? '#f5f5f5' : '#111827';

    const colors = [
      '#F44336', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5',
      '#2196F3', '#03A9F4', '#00BCD4', '#009688', '#4CAF50',
      '#8BC34A', '#CDDC39', '#FFC107', '#FF9800', '#FF5722'
    ];

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
          tooltip: { 
            enabled: true,
            callbacks: {
              title: function(context) {
                const index = context[0].dataIndex;
                return allCausas[index]; // Muestra la causa completa
              }
            }
          },
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
            ticks: { color: labelColor, font: { size: 8 } },
            grid: { display: false }
          }
        }
      },
      plugins: [ChartDataLabels]
    });

    currentCharts.push(chart);
  });
}

function renderEstadisticaDesechoCharts() {
  const chartsContainer = document.getElementById('chartsContainer');
  
  const dataByPrueba = {};
  filteredLlantasDesecho.forEach(llanta => {
    const pruebaId = llanta.PruebasDesecho_idPruebasDesecho;
    const pruebaFecha = llanta.PruebasDesechoFecha ? new Date(llanta.PruebasDesechoFecha).toLocaleDateString('es-MX') : 'Sin fecha';
    
    if (!dataByPrueba[pruebaId]) {
      dataByPrueba[pruebaId] = {
        fecha: pruebaFecha,
        totalLlantas: 0,
        mmTotal: 0,
        costoTotal: 0
      };
    }
    
    dataByPrueba[pruebaId].totalLlantas++;
    dataByPrueba[pruebaId].mmTotal += parseFloat(llanta.LlantasDesechoRemanente) || 0;
    const costo = llanta.LlantasVehiculosPrecio ?? llanta.LlantasPrecio ?? llanta.LlantasDesechoCosto ?? 0;
    dataByPrueba[pruebaId].costoTotal += parseFloat(costo);
  });

  const sortedPruebas = Object.keys(dataByPrueba).sort((a, b) => {
    const dateA = new Date(dataByPrueba[a].fecha);
    const dateB = new Date(dataByPrueba[b].fecha);
    return dateB - dateA;
  });

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
    title.textContent = `ESTADÍSTICA DESECHO\n${prueba.fecha}`;
    chartCard.appendChild(title);
    
    const statsDiv = document.createElement('div');
    statsDiv.style.padding = '4px';
    statsDiv.style.flex = '1';
    statsDiv.style.display = 'flex';
    statsDiv.style.flexDirection = 'column';
    statsDiv.style.gap = '5px';
    statsDiv.style.justifyContent = 'center';
    statsDiv.style.overflow = 'hidden';
    
    const mmPromedio = (prueba.mmTotal / prueba.totalLlantas).toFixed(2);
    const costoPromedio = (prueba.costoTotal / prueba.totalLlantas).toFixed(2);
    
    const isDarkTheme = document.documentElement.classList.contains('dark-theme');
    const textColor = isDarkTheme ? '#f5f5f5' : '#111827';
    
    statsDiv.innerHTML = `
      <div style="text-align: center; padding: 6px 8px; background: rgba(239, 68, 68, 0.1); border-radius: 6px; border-left: 3px solid var(--line-red);">
        <div style="font-size: 18px; font-weight: bold; color: var(--line-red); line-height: 1.2;">${prueba.totalLlantas}</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">TOTAL LLANTAS</div>
      </div>
      <div style="text-align: center; padding: 6px 8px; background: rgba(33, 150, 243, 0.1); border-radius: 6px; border-left: 3px solid #2196F3;">
        <div style="font-size: 18px; font-weight: bold; color: #2196F3; line-height: 1.2;">${mmPromedio} mm</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">MM PROMEDIO</div>
      </div>
      <div style="text-align: center; padding: 6px 8px; background: rgba(76, 175, 80, 0.1); border-radius: 6px; border-left: 3px solid #4CAF50;">
        <div style="font-size: 18px; font-weight: bold; color: #4CAF50; line-height: 1.2;">$${costoPromedio}</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">COSTO PROMEDIO</div>
      </div>
      <div style="text-align: center; padding: 6px 8px; background: rgba(255, 152, 0, 0.1); border-radius: 6px; border-left: 3px solid #FF9800;">
        <div style="font-size: 18px; font-weight: bold; color: #FF9800; line-height: 1.2;">$${prueba.costoTotal.toFixed(2)}</div>
        <div style="font-size: 9px; color: ${textColor}; margin-top: 1px; line-height: 1.1;">COSTO TOTAL</div>
      </div>
    `;
    
    chartCard.appendChild(statsDiv);
    chartsContainer.appendChild(chartCard);
  });
}

// Render table
function renderTable() {
  const desechoList = document.getElementById('desechoList');
  if (!desechoList) return;

  // Filter data by selected prueba if one is selected
  let dataToDisplay = filteredLlantasDesecho;
  if (selectedPruebaId !== null) {
    dataToDisplay = filteredLlantasDesecho.filter(llanta => 
      String(llanta.PruebasDesecho_idPruebasDesecho) === String(selectedPruebaId)
    );
  }

  if (dataToDisplay.length === 0) {
    desechoList.innerHTML = '<p class="loading-text">No hay datos disponibles</p>';
    return;
  }

  const table = document.createElement('table');
  table.className = 'desecho-table';
  
  table.innerHTML = `
    <thead>
      <tr>
        <th>#</th>
        <th>NO. LLANTA</th>
        <th>MARCA</th>
        <th>MODELO</th>
        <th>MEDIDA</th>
        <th>CAUSA DESECHO</th>
        <th>TIPO PISO</th>
        <th>MM</th>
        <th>COSTO</th>
        <th>FOTO(S)</th>
        <th>OBSERVACIONES</th>
      </tr>
    </thead>
    <tbody>
      ${dataToDisplay.map((llanta, index) => {
        const hasPhoto = Boolean(llanta.LlantasDesechoFoto1 || llanta.LlantasDesechoFoto2);
        const buttonClass = hasPhoto ? 'foto-btn has-photo' : 'foto-btn';
        const title = hasPhoto ? 'Ver fotos' : 'Sin fotos';
        // Use original index from filteredLlantasDesecho for photo modal
        const originalIndex = filteredLlantasDesecho.indexOf(llanta);
        return `
        <tr>
          <td>${index + 1}</td>
          <td>${llanta.LlantasDesechoNoLlanta || '-'}</td>
          <td>${llanta.LlantasMarca || '-'}</td>
          <td>${llanta.LlantasModelo || '-'}</td>
          <td>${llanta.LlantasMedida || '-'}</td>
          <td>${llanta.LlantasDesechoCausaDes || '-'}</td>
          <td>${llanta.LlantasDesechoPiso || '-'}</td>
          <td>${llanta.LlantasDesechoRemanente || '-'}</td>
          <td>$${llanta.LlantasVehiculosPrecio ?? llanta.LlantasPrecio ?? llanta.LlantasDesechoCosto ?? '0'}</td>
          <td>
            <button class="${buttonClass}" title="${title}" data-index="${originalIndex}" type="button">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
              </svg>
            </button>
          </td>
          <td>${llanta.LlantasDesechoComentarios || '-'}</td>
        </tr>
      `;
      }).join('')}
    </tbody>
  `;
  
  desechoList.innerHTML = '';
  desechoList.appendChild(table);
}

desechoListContainer?.addEventListener('click', (event) => {
  const button = event.target.closest('.foto-btn');
  if (!button) {
    return;
  }
  const index = Number(button.dataset.index);
  if (!Number.isFinite(index)) {
    return;
  }
  const llanta = filteredLlantasDesecho[index];
  if (!llanta) {
    return;
  }
  console.log('Photo button clicked for llanta:', {
    id: llanta.idLlantasDesecho,
    hasFoto1: !!llanta.LlantasDesechoFoto1,
    foto1Type: llanta.LlantasDesechoFoto1 ? typeof llanta.LlantasDesechoFoto1 : 'null',
    foto1Length: llanta.LlantasDesechoFoto1 ? llanta.LlantasDesechoFoto1.length : 0,
    hasFoto2: !!llanta.LlantasDesechoFoto2,
    foto2Type: llanta.LlantasDesechoFoto2 ? typeof llanta.LlantasDesechoFoto2 : 'null'
  });
  const hasPhoto = Boolean(llanta.LlantasDesechoFoto1 || llanta.LlantasDesechoFoto2);
  if (!hasPhoto) {
    console.warn('Button clicked but no photos found');
    return;
  }
  openPhotoModal(llanta.LlantasDesechoFoto1, llanta.LlantasDesechoFoto2);
});

// Update total count
function updateTotalCount() {
  const totalElement = document.getElementById('totalLlantas');
  if (totalElement) {
    // If a prueba is selected, count only those records
    if (selectedPruebaId !== null) {
      const selectedCount = filteredLlantasDesecho.filter(llanta => 
        String(llanta.PruebasDesecho_idPruebasDesecho) === String(selectedPruebaId)
      ).length;
      totalElement.textContent = selectedCount;
    } else {
      totalElement.textContent = filteredLlantasDesecho.length;
    }
  }
}

// Filter button handlers
const btnMMDesechados = document.getElementById('btnMMDesechados');
const btnParticipacionMarca = document.getElementById('btnParticipacionMarca');
const btnTipoPiso = document.getElementById('btnTipoPiso');
const btnCausasTop5 = document.getElementById('btnCausasTop5');
const btnCausasTotal = document.getElementById('btnCausasTotal');
const btnEstadisticaDesecho = document.getElementById('btnEstadisticaDesecho');

btnMMDesechados?.addEventListener('click', () => {
  currentView = 'MM_DESECHADOS';
  selectedPruebaId = null; // Reset selection when changing views
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnMMDesechados.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

btnParticipacionMarca?.addEventListener('click', () => {
  currentView = 'PARTICIPACION_MARCA';
  selectedPruebaId = null; // Reset selection when changing views
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnParticipacionMarca.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

btnTipoPiso?.addEventListener('click', () => {
  currentView = 'TIPO_PISO';
  selectedPruebaId = null;
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnTipoPiso.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

btnCausasTop5?.addEventListener('click', () => {
  currentView = 'CAUSAS_TOP5';
  selectedPruebaId = null;
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnCausasTop5.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

btnCausasTotal?.addEventListener('click', () => {
  currentView = 'CAUSAS_TOTAL';
  selectedPruebaId = null;
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnCausasTotal.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

btnEstadisticaDesecho?.addEventListener('click', () => {
  currentView = 'ESTADISTICA_DESECHO';
  selectedPruebaId = null;
  document.querySelectorAll('.btn-filtro').forEach(btn => btn.classList.remove('active'));
  btnEstadisticaDesecho.classList.add('active');
  renderCharts();
  renderTable();
  updateTotalCount();
});

// Export to Excel
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
    const flota = getCurrentFlotaContext() || localStorage.getItem('selectedFlotaForDesecho') || '';
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

const btnExportarExcel = document.getElementById('btnExportarExcel');
btnExportarExcel?.addEventListener('click', () => {
  exportVisibleTableToExcel('desechoList', 'desecho');
});

// Send email
const btnEnviarEmail = document.getElementById('btnEnviarEmail');
btnEnviarEmail?.addEventListener('click', () => {
  sendVisibleTableByEmail('desechoList', 'Desecho');
});

// Initialize page
async function initPage() {
  const [llantasDesecho, pruebasDesecho] = await Promise.all([
    fetchLlantasDesecho(),
    fetchPruebasDesecho()
  ]);
  allLlantasDesecho = llantasDesecho;
  allPruebasDesecho = pruebasDesecho;
  applyFlotaFilter();
  updateTotalCount();
  renderCharts();
  renderTable();
  
  // Set default active button
  btnMMDesechados?.classList.add('active');
}

initPage();
