const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
require('dotenv').config();

const app = express();
// Usar PORT en mayúsculas es vital para la compatibilidad con Passenger/cPanel
const PORT = process.env.PORT || 3000;

// Middlewares
app.use(cors({
    origin: '*', 
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(bodyParser.json({ limit: '50mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));

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

// 1. Iniciar el servidor ANTES de cargar todas las rutas pesadas
// Esto ayuda a que cPanel detecte que el proceso está activo rápido
app.listen(PORT, () => {
    console.log(`Servidor Yokohama activo en puerto ${PORT}`);
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error('Error inesperado:', err);
    const userMessage = 'Error interno del servidor';
    if (process.env.NODE_ENV === 'development') {
        res.status(500).json({ message: userMessage, detail: err.message });
    } else {
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
app.use('/api/semaforo-vehiculos', require('./routes/vehiculoSemaforo'));
app.use('/api/pruebas-desecho', require('./routes/pruebasDesecho'));
app.use('/api/llantas-desecho', require('./routes/llantasDesecho'));
app.use('/api/permisos', require('./routes/permisos'));
app.use('/api/perfiles-usuario', require('./routes/perfilesUsuario'));
app.use('/api/parametros', require('./routes/parametros'));
app.use('/api/reports', require('./routes/reports'));
app.use('/api/pruebas-inspeccion', require('./routes/pruebasInspeccion'));
app.use('/api/vehiculos-inspeccion', require('./routes/vehiculosInspeccion'));
app.use('/api/llantas-inspeccion', require('./routes/llantasInspeccion'));
app.use('/api/llantas-flota', require('./routes/llantasFlota'));
app.use('/api/incentivos', require('./routes/incentivos'));
// Alias without /api for hosting setups that rewrite paths differently.
app.use('/incentivos', require('./routes/incentivos'));
// Additional aliases: some hosting setups mount the app under a subfolder like /public/
app.use('/public/api/incentivos', require('./routes/incentivos'));
app.use('/public/incentivos', require('./routes/incentivos'));

console.log('Mounted incentives routes: /api/incentivos, /incentivos, /public/api/incentivos, /public/incentivos');

// Base routes
app.get('/', (req, res) => {
    res.redirect('/index.html');
});

app.get('/a', (req, res) => {
    res.json({ message: 'OK', info: 'Ruta de prueba /a', timestamp: new Date().toISOString() });
});

// Health checks
function healthPayload() {
    return { status: 'ok', uptime: process.uptime(), timestamp: new Date().toISOString() };
}

app.get('/health', (req, res) => res.json(healthPayload()));
app.get('/api/health', (req, res) => res.json(healthPayload()));
app.get('/healthz', (req, res) => res.json(healthPayload()));
app.get('/status', (req, res) => res.json(healthPayload()));

// Catch-all 404
app.use((req, res) => {
    res.status(404).json({ message: 'Recurso no encontrado', path: req.originalUrl });
});

// Manejo de errores no capturados
process.on('uncaughtException', (err) => {
    console.error('Excepción no capturada:', err);
});

module.exports = app;