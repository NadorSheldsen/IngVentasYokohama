const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all permisos with related data
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT p.*, pu.PerfilesUsuarioNombre
      FROM permisos p
      JOIN perfilesusuario pu ON p.PerfilesUsuario_idPerfilesUsuario = pu.idPerfilesUsuario
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener los permisos', error);
  }
});

// GET a specific permiso by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT p.*, pu.PerfilesUsuarioNombre
      FROM permisos p
      JOIN perfilesusuario pu ON p.PerfilesUsuario_idPerfilesUsuario = pu.idPerfilesUsuario
      WHERE p.idPermisos = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Permiso no encontrado' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener el permiso', error);
  }
});

// GET permisos by perfil de usuario ID
router.get('/perfil/:perfilId', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT p.*, pu.PerfilesUsuarioNombre
      FROM permisos p
      JOIN perfilesusuario pu ON p.PerfilesUsuario_idPerfilesUsuario = pu.idPerfilesUsuario
      WHERE p.PerfilesUsuario_idPerfilesUsuario = ?
    `, [req.params.perfilId]);
    
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener permisos del perfil', error);
  }
});

// POST a new permiso
router.post('/', async (req, res) => {
  try {
    const { PerfilesUsuario_idPerfilesUsuario, PermisosNombre } = req.body;
    
    const [result] = await db.query(
      'INSERT INTO permisos (PerfilesUsuario_idPerfilesUsuario, PermisosNombre) VALUES (?, ?)',
      [PerfilesUsuario_idPerfilesUsuario, PermisosNombre]
    );
    
    // Return the created permiso
    const [newPermiso] = await db.query(
      'SELECT * FROM permisos WHERE idPermisos = ?',
      [result.insertId]
    );
    
    res.status(201).json(newPermiso[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear el permiso', error);
  }
});

// PUT/UPDATE a permiso
router.put('/:id', async (req, res) => {
  try {
    const { PerfilesUsuario_idPerfilesUsuario, PermisosNombre } = req.body;
    
    const [result] = await db.query(
      'UPDATE permisos SET PerfilesUsuario_idPerfilesUsuario = ?, PermisosNombre = ? WHERE idPermisos = ?',
      [PerfilesUsuario_idPerfilesUsuario, PermisosNombre, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Permiso not found' });
    }
    
    res.json({ message: 'Permiso updated successfully' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar el permiso', error);
  }
});

// DELETE a permiso
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM permisos WHERE idPermisos = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Permiso not found' });
    }
    
    res.json({ message: 'Permiso deleted successfully' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el permiso', error);
  }
});

module.exports = router;
