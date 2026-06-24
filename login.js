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

// Manejar el login
function handleLogin(event) {
  event.preventDefault();
  
  const username = document.getElementById('username').value;
  const password = document.getElementById('password').value;

  // Aquí puedes agregar la lógica de autenticación
  console.log('Login attempt:', { username, password });
  
  // Ejemplo de validación básica (reemplazar con tu lógica real)
  if (username && password) {
    alert('¡Inicio de sesión exitoso!');
    // Aquí redirigirías a la página principal
    // window.location.href = '/dashboard.html';
  }
}

// Inicializar tema al cargar la página
initTheme();

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
