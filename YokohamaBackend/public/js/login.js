// Obtener la URL del API desde la configuración
const API_URL = config.apiUrl;

// Detectar preferencia del sistema y cargar tema guardado
function initTheme() {
  const savedTheme = localStorage.getItem('theme');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  
  if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
    document.body.classList.add('dark-theme');
    document.getElementById('themeIcon').textContent = '☀️';
  } else {
    document.getElementById('themeIcon').textContent = '🌙';
  }
}

// Cambiar tema
function toggleTheme() {
  const body = document.body;
  const isDark = body.classList.toggle('dark-theme');
  const icon = document.getElementById('themeIcon');
  
  icon.textContent = isDark ? '☀️' : '🌙';
  localStorage.setItem('theme', isDark ? 'dark' : 'light');
}

// Mostrar mensaje de error
function showError(message) {
  const errorDiv = document.getElementById('errorMessage');
  errorDiv.textContent = message;
  errorDiv.classList.add('show');
  
  setTimeout(() => {
    errorDiv.classList.remove('show');
  }, 5000);
}

// Ocultar mensaje de error
function hideError() {
  const errorDiv = document.getElementById('errorMessage');
  errorDiv.classList.remove('show');
}

// Deshabilitar formulario durante el login
function setFormLoading(loading) {
  const button = document.getElementById('loginButton');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  
  if (loading) {
    button.disabled = true;
    button.classList.add('loading');
    usernameInput.disabled = true;
    passwordInput.disabled = true;
  } else {
    button.disabled = false;
    button.classList.remove('loading');
    usernameInput.disabled = false;
    passwordInput.disabled = false;
  }
}

// Manejar el login
async function handleLogin(event) {
  event.preventDefault();
  hideError();
  
  const email = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;

  if (!email || !password) {
    showError('Por favor, completa todos los campos');
    return;
  }

  setFormLoading(true);

  try {
    console.log('Intentando login con:', email);
    
    const response = await fetch(`${API_URL}/usuarios/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        UsuariosCorreo: email,
        UsuariosPassword: password
      })
    });

    console.log('Respuesta del servidor:', response.status);

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || 'Error al iniciar sesión');
    }

    const userData = await response.json();
    console.log('Login exitoso:', userData);

    // Guardar datos del usuario en localStorage
    localStorage.setItem('user', JSON.stringify(userData));
    localStorage.setItem('isLoggedIn', 'true');

    // Mostrar mensaje de éxito
    showSuccess('¡Inicio de sesión exitoso!');

    // Redirigir a la página principal después de 1 segundo
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 1000);

  } catch (error) {
    console.error('Error en login:', error);
    showError(error.message || 'Error de conexión con el servidor');
  } finally {
    setFormLoading(false);
  }
}

// Mostrar mensaje de éxito (modificamos el div de error para reutilizarlo)
function showSuccess(message) {
  const errorDiv = document.getElementById('errorMessage');
  errorDiv.textContent = message;
  errorDiv.style.background = 'var(--card-dark)';
  errorDiv.style.color = '#4caf50';
  
  if (document.body.classList.contains('dark-theme')) {
    errorDiv.style.background = '#1b4332';
    errorDiv.style.color = '#81c784';
  } else {
    errorDiv.style.background = '#e8f5e9';
    errorDiv.style.color = '#2e7d32';
  }
  
  errorDiv.classList.add('show');
}

// Verificar si ya está logueado al cargar la página
function checkExistingSession() {
  const isLoggedIn = localStorage.getItem('isLoggedIn');
  if (isLoggedIn === 'true') {
    // Si ya hay sesión, redirigir al inicio
    window.location.href = 'index.html';
  }
}

// Inicializar tema al cargar la página
initTheme();

// Verificar sesión existente
checkExistingSession();

// Escuchar cambios en la preferencia del sistema
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
  if (!localStorage.getItem('theme')) {
    if (e.matches) {
      document.body.classList.add('dark-theme');
      document.getElementById('themeIcon').textContent = '☀️';
    } else {
      document.body.classList.remove('dark-theme');
      document.getElementById('themeIcon').textContent = '🌙';
    }
  }
});
