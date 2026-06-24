const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all tipos de vehiculos
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM tipovehiculos');
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener los tipos de vehículo', error);
  }
});

// GET a specific tipo de vehiculo by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM tipovehiculos WHERE idTipoVehiculos = ?', [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Tipo de vehículo no encontrado' });
    }
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener el tipo de vehículo', error);
  }
});

// POST a new tipo de vehiculo
router.post('/', async (req, res) => {
  try {
    const { TipoVehiculosNombre, TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp } = req.body;
    
    const [result] = await db.query(
      'INSERT INTO tipovehiculos (TipoVehiculosNombre, TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp) VALUES (?, ?, ?)',
      [TipoVehiculosNombre, TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp]
    );
    
    res.status(201).json({ 
      message: 'Tipo de vehiculo created successfully', 
      id: result.insertId 
    });
  } catch (error) {
    handleServerError(res, 'Error al crear el tipo de vehículo', error);
  }
});

// PUT/UPDATE a tipo de vehiculo
router.put('/:id', async (req, res) => {
  try {
    const { TipoVehiculosNombre, TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp } = req.body;
    
    const [result] = await db.query(
      'UPDATE tipovehiculos SET TipoVehiculosNombre = ?, TipoVehiculosCantLlantas = ?, TipoVehiculosLlantasEmp = ? WHERE idTipoVehiculos = ?',
      [TipoVehiculosNombre, TipoVehiculosCantLlantas, TipoVehiculosLlantasEmp, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Tipo de vehículo no encontrado' });
    }
    
    res.json({ message: 'Tipo de vehículo actualizado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar el tipo de vehículo', error);
  }
});

// DELETE a tipo de vehiculo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM tipovehiculos WHERE idTipoVehiculos = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Tipo de vehículo no encontrado' });
    }
    
    res.json({ message: 'Tipo de vehículo eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el tipo de vehículo', error);
  }
});

module.exports = router;
