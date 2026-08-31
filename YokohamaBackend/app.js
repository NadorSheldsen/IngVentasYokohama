const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
require('dotenv').config();

// Import routes
const flotasRoutes = require('./routes/flotas');
const perfilesUsuarioRoutes = require('./routes/perfilesUsuario');
const usuariosRoutes = require('./routes/usuarios');
const sucursalesRoutes = require('./routes/sucursales');
const flotasUsuariosRoutes = require('./routes/flotasUsuarios');
const tipoVehiculosRoutes = require('./routes/tipoVehiculos');
const vehiculosRoutes = require('./routes/vehiculos');
const llantasRoutes = require('./routes/llantas');
const llantasVehiculosRoutes = require('./routes/llantasVehiculos');
const pruebaRendimientoRoutes = require('./routes/pruebaRendimiento');
const llantasRendimientoRoutes = require('./routes/llantasRendimiento');
const pruebasSemaforoRoutes = require('./routes/pruebasSemaforo');
const llantasSemaforoRoutes = require('./routes/llantasSemaforo');
const vehiculosSemaforoRoutes = require('./routes/vehiculoSemaforo');
const pruebasDesechoRoutes = require('./routes/pruebasDesecho');
const llantasDesechoRoutes = require('./routes/llantasDesecho');
const permisosRoutes = require('./routes/permisos');
const parametrosRoutes = require('./routes/parametros');
// Add new inspection routes
const pruebasInspeccionRoutes = require('./routes/pruebasInspeccion');
const vehiculosInspeccionRoutes = require('./routes/vehiculosInspeccion');
const llantasInspeccionRoutes = require('./routes/llantasInspeccion');

const app = express();
const PORT = process.env.PORT || 3000;

// Middlewares
app.use(cors({
    origin: '*', // Permitir todas las solicitudes en desarrollo
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(bodyParser.json({ limit: '50mb' })); // Aumentar límite para imágenes
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));

// Middleware para capturar errores de parsing de JSON
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    console.error('[bodyParser ERROR] JSON parse error:', err.message);
    console.error('[bodyParser ERROR] Content-Length:', req.headers['content-length']);
    console.error('[bodyParser ERROR] Content-Type:', req.headers['content-type']);
    return res.status(400).json({ error: 'Invalid JSON: ' + err.message });
  }
  next(err);
});

// Middleware para loguear requests a desecho
app.use((req, res, next) => {
  if (req.path === '/api/llantas-desecho' && req.method === 'POST') {
    console.log('[express] Incoming POST /api/llantas-desecho');
    console.log('[express] Content-Length:', req.headers['content-length']);
    console.log('[express] Content-Type:', req.headers['content-type']);
  }
  next();
});

// Servir archivos estáticos de la carpeta public (CSS, JS, HTML)
app.use(express.static(path.join(__dirname, 'public')));
console.log('Serving static files from:', path.join(__dirname, 'public'));

// Servir imágenes de la carpeta routes/img
app.use('/img', express.static(path.join(__dirname, 'routes/img')));
console.log('Serving images from:', path.join(__dirname, 'routes/img'));

// Error handling middleware
app.use((err, req, res, next) => {
  // Log full error server-side for debugging/alerts (do not expose stack to clients)
  console.error('Error inesperado:', err);
  const userMessage = 'Error interno del servidor';
  if (process.env.NODE_ENV === 'development') {
    // In development include a brief detail (no stack) to aid debugging
    res.status(500).json({ message: userMessage, detail: err.message });
  } else {
    // In production only return a friendly, non-technical message
    res.status(500).json({ message: userMessage });
  }
});

// Routes
app.use('/api/usuarios', require('./routes/usuarios'));
app.use('/api/flotas', require('./routes/flotas'));
app.use('/api/vehiculos', require('./routes/vehiculos'));
app.use('/api/tipo-vehiculos', require('./routes/tipoVehiculos'));
app.use('/api/llantas', require('./routes/llantas'));
app.use('/api/llantas-vehiculos', require('./routes/llantasVehiculos'));
app.use('/api/flotas-usuarios', require('./routes/flotasUsuarios'));
app.use('/api/sucursales', require('./routes/sucursales'));
app.use('/api/prueba-rendimiento', require('./routes/pruebaRendimiento'));
app.use('/api/llantas-rendimiento', require('./routes/llantasRendimiento'));
app.use('/api/pruebas-semaforo', require('./routes/pruebasSemaforo'));
app.use('/api/llantas-semaforo', require('./routes/llantasSemaforo'));
app.use('/api/vehiculo-semaforo', require('./routes/vehiculoSemaforo'));
// Compatibility alias: older clients may call /api/semaforo-vehiculos
app.use('/api/semaforo-vehiculos', require('./routes/vehiculoSemaforo'));
app.use('/api/pruebas-desecho', require('./routes/pruebasDesecho'));
app.use('/api/llantas-desecho', require('./routes/llantasDesecho'));
app.use('/api/permisos', require('./routes/permisos'));
app.use('/api/perfiles-usuario', require('./routes/perfilesUsuario'));
app.use('/api/parametros', require('./routes/parametros'));
console.log('Parametros route registered at /api/parametros');
app.use('/api/reports', require('./routes/reports'));
console.log('Reports route registered at /api/reports');
// Add new inspection routes
app.use('/api/pruebas-inspeccion', require('./routes/pruebasInspeccion'));
app.use('/api/vehiculos-inspeccion', require('./routes/vehiculosInspeccion'));
app.use('/api/llantas-inspeccion', require('./routes/llantasInspeccion'));
app.use('/api/llantas-flota', require('./routes/llantasFlota'));
app.use('/api/incentivos', require('./routes/incentivos'));
// Alias without /api for hosting setups that rewrite paths differently.
app.use('/incentivos', require('./routes/incentivos'));

// Base route
app.get('/', (req, res) => {
  res.send('Yokohama API is running');
});

// Small test alias requested by the user: returns a friendly JSON in Spanish
app.get('/a', (req, res) => {
  res.json({ message: 'OK', info: 'Ruta de prueba /a', timestamp: new Date().toISOString() });
});

// Health check for monitoring/proxies
function healthPayload() {
  return { status: 'ok', uptime: process.uptime(), timestamp: new Date().toISOString() };
}

// Primary health endpoint
app.get('/health', (req, res) => {
  res.json(healthPayload());
});

// Common aliases in case a proxy or monitoring expects a different path
app.get('/api/health', (req, res) => {
  res.json(healthPayload());
});

app.get('/healthz', (req, res) => {
  res.json(healthPayload());
});

app.get('/status', (req, res) => {
  res.json(healthPayload());
});

console.log('Health endpoints registered: /health, /api/health, /healthz, /status');

// Start the server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server is running on port ${PORT}`);
});

// Catch-all to return JSON 404 instead of letting the hosting layer (Plesk) render HTML
app.use((req, res) => {
  res.status(404).json({ message: 'Recurso no encontrado', path: req.originalUrl });
});

// Manejo de errores no capturados
process.on('uncaughtException', (err) => {
    console.error('Excepción no capturada:', err);
});

module.exports = app;
