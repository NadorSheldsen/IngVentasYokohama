const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

function resolveCreatorId(req) {
  const fromBody = req.body && (req.body.Usuarios_idUsuarios || req.body.UsuariosId || req.body.createdByUsuarioId);
  if (fromBody) return Number(fromBody);

  const fromUser = req.user && (req.user.idUsuarios || req.user.id);
  if (fromUser) return Number(fromUser);

  const rawHeader = req.headers['x-user-id'] || req.headers['x-usuario-id'];
  if (rawHeader) {
    const parsed = Number(Array.isArray(rawHeader) ? rawHeader[0] : rawHeader);
    if (Number.isInteger(parsed) && parsed > 0) return parsed;
  }

  const authHeader = req.headers.authorization;
  if (typeof authHeader === 'string' && authHeader.toLowerCase().startsWith('bearer ')) {
    const token = authHeader.slice(7).trim();
    const tokenAsNumber = Number(token);
    if (Number.isInteger(tokenAsNumber) && tokenAsNumber > 0) return tokenAsNumber;
  }

  return null;
}

// GET all pruebas de rendimiento with related data
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT pr.*, v.VehiculosNumero,
             pr.PruebaRendimientoLat AS latitude,
             pr.PruebaRendimientoLon AS longitude
      FROM pruebarendimiento pr
      JOIN vehiculos v ON pr.Vehiculos_idVehiculos = v.idVehiculos
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las pruebas de rendimiento', error);
  }
});

// GET a specific prueba de rendimiento by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
            SELECT pr.*, v.VehiculosNumero,
              pr.PruebaRendimientoLat AS latitude,
              pr.PruebaRendimientoLon AS longitude
            FROM pruebarendimiento pr
          JOIN vehiculos v ON pr.Vehiculos_idVehiculos = v.idVehiculos
      WHERE pr.idPruebaRendimiento = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Prueba de rendimiento no encontrada' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la prueba de rendimiento', error);
  }
});

// GET latest prueba de rendimiento for a given vehiculo
router.get('/vehiculo/:vehiculoId/ultimo', async (req, res) => {
  try {
    const vehiculoId = req.params.vehiculoId;
    const [rows] = await db.query(
      'SELECT * FROM pruebarendimiento WHERE Vehiculos_idVehiculos = ? ORDER BY idPruebaRendimiento DESC LIMIT 1',
      [vehiculoId]
    );

    if (rows.length === 0) {
      // Return explicit null so client can detect no previous prueba
      return res.json(null);
    }

    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la última prueba de rendimiento para el vehículo', error);
  }
});

// GET km recorridos por vehículo dentro de una flota.
// Usa el historial de pruebas para calcular km = última prueba - primera prueba.
router.get('/flota/:flotaId/km-recorrido', async (req, res) => {
  try {
    const flotaId = req.params.flotaId;
    const [rows] = await db.query(
      `SELECT pr.Vehiculos_idVehiculos AS vehiculoId,
              GREATEST(MAX(pr.PruebaRendimientoOdometro) - MIN(pr.PruebaRendimientoOdometro), 0) AS kmRecorrido
       FROM pruebarendimiento pr
       JOIN vehiculos v ON pr.Vehiculos_idVehiculos = v.idVehiculos
       WHERE v.Flotas_idFlotas = ?
       GROUP BY pr.Vehiculos_idVehiculos`,
      [flotaId]
    );

    const payload = rows.map(row => ({
      vehiculoId: row.vehiculoId,
      kmRecorrido: Number(row.kmRecorrido || 0)
    }));

    res.json(payload);
  } catch (error) {
    handleServerError(res, 'Error al obtener kilómetros recorridos por flota', error);
  }
});

// POST a new prueba de rendimiento
router.post('/', async (req, res) => {
  console.log('Received single prueba de rendimiento request');
  try {
    const { Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro, Usuarios_idUsuarios, latitude, longitude } = req.body;
    const creatorId = resolveCreatorId(req);

    // Insertar la nueva prueba (incluyendo lat/lon si vienen) y registrar el usuario creador
    const [result] = await db.query(
      'INSERT INTO pruebarendimiento (Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro, Usuarios_idUsuarios, PruebaRendimientoLat, PruebaRendimientoLon) VALUES (?, ?, ?, ?, ?, ?)',
      [Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro, creatorId || Usuarios_idUsuarios || null, latitude || null, longitude || null]
    );

    // Consultar el registro recién creado
    const [rows] = await db.query(
      'SELECT * FROM pruebarendimiento WHERE idPruebaRendimiento = ?',
      [result.insertId]
    );

    // Responder con el objeto completo
    res.status(201).json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear la prueba de rendimiento', error);
  }
});

// PUT/UPDATE a prueba de rendimiento
router.put('/:id', async (req, res) => {
  try {
    const { Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro, latitude, longitude } = req.body;
    
    const [result] = await db.query(
      'UPDATE pruebarendimiento SET Vehiculos_idVehiculos = ?, PruebaRendimientoFecha = ?, PruebaRendimientoOdometro = ?, PruebaRendimientoLat = ?, PruebaRendimientoLon = ? WHERE idPruebaRendimiento = ?',
      [Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro, latitude || null, longitude || null, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba de rendimiento no encontrada' });
    }
    
    res.json({ message: 'Prueba de rendimiento actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la prueba de rendimiento', error);
  }
});

// DELETE a prueba de rendimiento
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM pruebarendimiento WHERE idPruebaRendimiento = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba de rendimiento no encontrada' });
    }
    
    res.json({ message: 'Prueba de rendimiento eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la prueba de rendimiento', error);
  }
});

module.exports = router;
