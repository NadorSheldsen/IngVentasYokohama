// Configuración de la aplicación
const config = {
  // URL del API - cambia según el entorno
  apiUrl: window.location.hostname === 'localhost' 
    ? 'http://localhost:3000/api'  // Desarrollo
    : '/api',  // Producción (mismo servidor)
  
  // Otras configuraciones
  appName: 'Yokohama',
  version: '1.0.0',
};

// Exportar configuración (compatible con módulos y scripts normales)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = config;
}
