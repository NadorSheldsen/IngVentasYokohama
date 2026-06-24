const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all perfiles de usuario
router.get('/', async (req, res) => {
  console.log('Received request for all perfiles de usuario');
  try {
    const [rows] = await db.query('SELECT * FROM perfilesusuario');
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener los perfiles de usuario', error);
  }
});

// GET a specific perfil de usuario by ID
router.get('/:id', async (req, res) => {
  console.log('Received request for perfil de usuario with ID:', req.params.id);
  try {
    const [rows] = await db.query('SELECT * FROM perfilesusuario WHERE idPerfilesUsuario = ?', [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Perfil de usuario no encontrado' });
    }
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener el perfil de usuario', error);
  }
});

// POST a new perfil de usuario
router.post('/', async (req, res) => {
  try {
    const { PerfilesUsuarioNombre } = req.body;
    
    const [result] = await db.query(
      'INSERT INTO perfilesusuario (PerfilesUsuarioNombre) VALUES (?)',
      [PerfilesUsuarioNombre]
    );
    
    // Return the created perfil
    const [newPerfil] = await db.query(
      'SELECT * FROM perfilesusuario WHERE idPerfilesUsuario = ?',
      [result.insertId]
    );
    
    res.status(201).json(newPerfil[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear el perfil de usuario', error);
  }
});

// PUT/UPDATE a perfil de usuario
router.put('/:id', async (req, res) => {
  try {
    const { PerfilesUsuarioNombre } = req.body;
    
    const [result] = await db.query(
      'UPDATE perfilesusuario SET PerfilesUsuarioNombre = ? WHERE idPerfilesUsuario = ?',
      [PerfilesUsuarioNombre, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Perfil de usuario no encontrado' });
    }
    
    // Return the updated perfil
    const [updatedPerfil] = await db.query(
      'SELECT * FROM perfilesusuario WHERE idPerfilesUsuario = ?',
      [req.params.id]
    );
    
    res.json(updatedPerfil[0]);
  } catch (error) {
    handleServerError(res, 'Error al actualizar el perfil de usuario', error);
  }
});

// DELETE a perfil de usuario
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM perfilesusuario WHERE idPerfilesUsuario = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Perfil de usuario no encontrado' });
    }
    
    res.json({ message: 'Perfil de usuario eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el perfil de usuario', error);
  }
});

module.exports = router;
