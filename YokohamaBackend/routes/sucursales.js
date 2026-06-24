const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all sucursales
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT s.*, f.FlotasNombre 
      FROM sucursales s
      JOIN flotas f ON s.Flotas_idFlotas = f.idFlotas
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las sucursales', error);
  }
});

// GET a specific sucursal by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT s.*, f.FlotasNombre 
      FROM sucursales s
      JOIN flotas f ON s.Flotas_idFlotas = f.idFlotas
      WHERE s.idSucursales = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Sucursal no encontrada' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la sucursal', error);
  }
});

// POST a new sucursal
router.post('/', async (req, res) => {
  try {
    const { Flotas_idFlotas, SucursalesNombre } = req.body;
    
    const [result] = await db.query(
      'INSERT INTO sucursales (Flotas_idFlotas, SucursalesNombre) VALUES (?, ?)',
      [Flotas_idFlotas, SucursalesNombre]
    );
    
    res.status(201).json({ 
      message: 'Sucursal created successfully', 
      id: result.insertId 
    });
  } catch (error) {
    handleServerError(res, 'Error al crear la sucursal', error);
  }
});

// PUT/UPDATE a sucursal
router.put('/:id', async (req, res) => {
  try {
    const { Flotas_idFlotas, SucursalesNombre } = req.body;
    
    const [result] = await db.query(
      'UPDATE sucursales SET Flotas_idFlotas = ?, SucursalesNombre = ? WHERE idSucursales = ?',
      [Flotas_idFlotas, SucursalesNombre, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Sucursal no encontrada' });
    }
    
    res.json({ message: 'Sucursal actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la sucursal', error);
  }
});

// DELETE a sucursal
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM sucursales WHERE idSucursales = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Sucursal no encontrada' });
    }
    
    res.json({ message: 'Sucursal eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la sucursal', error);
  }
});

module.exports = router;
