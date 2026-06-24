const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all vehiculos semaforo with related data
router.get('/', async (req, res) => {
  try {
    // Use the new table VehiculoPruebaSemaforo but alias columns to keep the API shape
    const [rows] = await db.query(`
      SELECT
        v.idVehiculoPruebaSemaforo AS idVehiculoSemaforo,
        v.PruebasSemaforo_idPruebasSemaforo,
        v.TipoVehiculos_idTipoVehiculos,
        v.VehiculoPruebaSemaforoNo AS VehiculoSemaforoNo,
        -- Include stored coordinates when available
        v.VehiculoPruebaSemaforoLat AS latitude,
        v.VehiculoPruebaSemaforoLon AS longitude,
        v.Usuarios_idUsuarios AS Usuarios_idUsuarios,
        ps.PruebasSemaforoFecha,
        t.TipoVehiculosNombre
      FROM vehiculopruebasemaforo v
      JOIN pruebassemaforo ps ON v.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
      JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener los vehículos semáforo', error);
  }
});

// GET a specific vehiculo semaforo by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT
        v.idVehiculoPruebaSemaforo AS idVehiculoSemaforo,
        v.PruebasSemaforo_idPruebasSemaforo,
        v.TipoVehiculos_idTipoVehiculos,
        v.VehiculoPruebaSemaforoNo AS VehiculoSemaforoNo,
        -- Include stored coordinates when available
        v.VehiculoPruebaSemaforoLat AS latitude,
        v.VehiculoPruebaSemaforoLon AS longitude,
        v.Usuarios_idUsuarios AS Usuarios_idUsuarios,
        ps.PruebasSemaforoFecha,
        t.TipoVehiculosNombre
      FROM vehiculopruebasemaforo v
      JOIN pruebassemaforo ps ON v.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
      JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
      WHERE v.idVehiculoPruebaSemaforo = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Vehículo semáforo no encontrado' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener el vehículo semáforo', error);
  }
});

// POST a new vehiculo semaforo
router.post('/', async (req, res) => {
  try {
    const { 
      PruebasSemaforo_idPruebasSemaforo,
      TipoVehiculos_idTipoVehiculos,
      VehiculoSemaforoNo,
      Usuarios_idUsuarios,
      latitude,
      longitude
    } = req.body;
    const creatorId = (req.body && (req.body.Usuarios_idUsuarios || req.body.UsuariosId)) || (req.user && (req.user.id || req.user.idUsuarios)) || null;
    
    // Prevent duplicate vehicle number within the same prueba semáforo
    const [existing] = await db.query(
      'SELECT idVehiculoPruebaSemaforo FROM vehiculopruebasemaforo WHERE PruebasSemaforo_idPruebasSemaforo = ? AND VehiculoPruebaSemaforoNo = ? LIMIT 1',
      [PruebasSemaforo_idPruebasSemaforo, VehiculoSemaforoNo]
    );
    if (existing && existing.length > 0) {
      const msg = 'Vehiculo semaforo con ese numero ya existe en la prueba';
      // Preserve a machine-readable message in a header so clients behind hosting
      // frontends that rewrite bodies can still read the server error text.
      res.set('X-Error-Message', msg);
      return res.status(409).json({ message: msg });
    }

    const [result] = await db.query(
      'INSERT INTO vehiculopruebasemaforo (PruebasSemaforo_idPruebasSemaforo, TipoVehiculos_idTipoVehiculos, VehiculoPruebaSemaforoNo, Usuarios_idUsuarios, VehiculoPruebaSemaforoLat, VehiculoPruebaSemaforoLon) VALUES (?, ?, ?, ?, ?, ?)',
      [PruebasSemaforo_idPruebasSemaforo, TipoVehiculos_idTipoVehiculos, VehiculoSemaforoNo, creatorId || Usuarios_idUsuarios || null, latitude || null, longitude || null]
    );
    
    // Return the created vehiculo semaforo
    const [newVehiculo] = await db.query(
      `SELECT
         idVehiculoPruebaSemaforo AS idVehiculoSemaforo,
         PruebasSemaforo_idPruebasSemaforo,
         TipoVehiculos_idTipoVehiculos,
         VehiculoPruebaSemaforoNo AS VehiculoSemaforoNo,
         VehiculoPruebaSemaforoLat AS latitude,
         VehiculoPruebaSemaforoLon AS longitude,
         Usuarios_idUsuarios AS Usuarios_idUsuarios
      FROM vehiculopruebasemaforo WHERE idVehiculoPruebaSemaforo = ?`,
      [result.insertId]
    );
    
    // Also try to update the master Vehiculos row (if exists) matching this number + type
    try {
      if (latitude || longitude) {
        await db.query(
          'UPDATE vehiculos SET VehiculosLatitude = ?, VehiculosLongitude = ? WHERE VehiculosNumero = ? AND TipoVehiculos_idTipoVehiculos = ? LIMIT 1',
          [latitude || null, longitude || null, VehiculoSemaforoNo, TipoVehiculos_idTipoVehiculos]
        );
      }
    } catch (e) {
      // ignore: column may not exist or update may fail
    }

    res.status(201).json(newVehiculo[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear el vehículo semáforo', error);
  }
});

// PUT/UPDATE a vehiculo semaforo
router.put('/:id', async (req, res) => {
  try {
    const { 
      PruebasSemaforo_idPruebasSemaforo,
      TipoVehiculos_idTipoVehiculos,
      VehiculoSemaforoNo,
      latitude,
      longitude
    } = req.body;
    
    const [result] = await db.query(
      'UPDATE vehiculopruebasemaforo SET PruebasSemaforo_idPruebasSemaforo = ?, TipoVehiculos_idTipoVehiculos = ?, VehiculoPruebaSemaforoNo = ?, VehiculoPruebaSemaforoLat = ?, VehiculoPruebaSemaforoLon = ? WHERE idVehiculoPruebaSemaforo = ?',
      [PruebasSemaforo_idPruebasSemaforo, TipoVehiculos_idTipoVehiculos, VehiculoSemaforoNo, latitude || null, longitude || null, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Vehículo semáforo no encontrado' });
    }
    
    // Also try to update the master Vehiculos row (if exists) matching this number + type
    try {
      if (latitude || longitude) {
        await db.query(
          'UPDATE vehiculos SET VehiculosLatitude = ?, VehiculosLongitude = ? WHERE VehiculosNumero = ? AND TipoVehiculos_idTipoVehiculos = ? LIMIT 1',
          [latitude || null, longitude || null, VehiculoSemaforoNo, TipoVehiculos_idTipoVehiculos]
        );
      }
    } catch (e) {
      // ignore
    }

    res.json({ message: 'Vehículo semáforo actualizado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar el vehículo semáforo', error);
  }
});

// DELETE a vehiculo semaforo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM vehiculopruebasemaforo WHERE idVehiculoPruebaSemaforo = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Vehículo semáforo no encontrado' });
    }
    
    res.json({ message: 'Vehículo semáforo eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el vehículo semáforo', error);
  }
});

module.exports = router;