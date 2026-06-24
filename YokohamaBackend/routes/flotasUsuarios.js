const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all flotasUsuarios with related data
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT fu.*, u.UsuariosNombre, f.FlotasNombre
      FROM flotasusuarios fu
      JOIN usuarios u ON fu.Usuarios_idUsuarios = u.idUsuarios
      JOIN flotas f ON fu.Flotas_idFlotas = f.idFlotas
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las asignaciones de flota', error);
  }
});

// GET a specific flotaUsuario by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT fu.*, u.UsuariosNombre, f.FlotasNombre
      FROM flotasusuarios fu
      JOIN usuarios u ON fu.Usuarios_idUsuarios = u.idUsuarios
      JOIN flotas f ON fu.Flotas_idFlotas = f.idFlotas
      WHERE fu.idFlotasUsuarios = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Asignación de flota no encontrada' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la asignación de flota', error);
  }
});

// POST a new flotaUsuario
router.post('/', async (req, res) => {
  try {
    const { Usuarios_idUsuarios, Flotas_idFlotas } = req.body;
    console.log('[flotasUsuarios] POST payload:', { Usuarios_idUsuarios, Flotas_idFlotas });
    // Validate required references
    if (!Usuarios_idUsuarios || !Flotas_idFlotas) {
      res.setHeader('X-Error-Message', 'Faltan campos requeridos: Usuarios_idUsuarios o Flotas_idFlotas');
      return res.status(400).json({ message: 'Campos requeridos incompletos' });
    }

    // Prevent duplicate assignments
    let [existing] = await db.query(
      'SELECT idFlotasUsuarios FROM flotasusuarios WHERE Usuarios_idUsuarios = ? AND Flotas_idFlotas = ?',
      [Usuarios_idUsuarios, Flotas_idFlotas]
    );
    console.log('[flotasUsuarios] Duplicate check result:', existing);
    if (existing.length > 0) {
      const msg = 'El usuario ya está asignado a esta flota';
      res.setHeader('X-Error-Message', msg);
      return res.status(409).json({ message: msg });
    }

    console.log('[flotasUsuarios] About to INSERT with values:', [Usuarios_idUsuarios, Flotas_idFlotas]);
    let result;
    try {
      [result] = await db.query(
        'INSERT INTO flotasusuarios (Usuarios_idUsuarios, Flotas_idFlotas) VALUES (?, ?)',
        [Usuarios_idUsuarios, Flotas_idFlotas]
      );
      console.log('[flotasUsuarios] Insert result:', result);
      console.log('[flotasUsuarios] Insert ID:', result.insertId);
    } catch (insertError) {
      console.error('[flotasUsuarios] INSERT ERROR:', insertError);
      console.error('[flotasUsuarios] INSERT ERROR code:', insertError.code);
      console.error('[flotasUsuarios] INSERT ERROR sqlMessage:', insertError.sqlMessage);
      throw insertError;
    }
    
    // Return the created flota usuario
    const [newFlotaUsuario] = await db.query(
      'SELECT * FROM flotasusuarios WHERE idFlotasUsuarios = ?',
      [result.insertId]
    );
    console.log('[flotasUsuarios] New flota usuario:', newFlotaUsuario[0]);
    
    res.status(201).json(newFlotaUsuario[0]);
  } catch (error) {
    console.error('[flotasUsuarios] Error in POST /:', error);
    console.error('[flotasUsuarios] Error message:', error.message);
    console.error('[flotasUsuarios] Error stack:', error.stack);
    handleServerError(res, 'Error al crear la asignación de flota', error);
  }
});

// PUT/UPDATE a flotaUsuario
router.put('/:id', async (req, res) => {
  try {
    const { Usuarios_idUsuarios, Flotas_idFlotas } = req.body;
    console.log('[flotasUsuarios] PUT payload:', { id: req.params.id, Usuarios_idUsuarios, Flotas_idFlotas });
    // Validate provided references before update
    if (!Usuarios_idUsuarios || !Flotas_idFlotas) {
      res.setHeader('X-Error-Message', 'Faltan campos requeridos para actualizar la asignación');
      return res.status(400).json({ message: 'Campos requeridos incompletos' });
    }

    // Prevent duplicate assignment to another record (unique constraint semantics)
    let [existingPut] = await db.query(
      'SELECT idFlotasUsuarios FROM flotasusuarios WHERE Usuarios_idUsuarios = ? AND Flotas_idFlotas = ? AND idFlotasUsuarios != ?',
      [Usuarios_idUsuarios, Flotas_idFlotas, req.params.id]
    );
    console.log('[flotasUsuarios] Duplicate check (PUT) result:', existingPut);
    if (existingPut.length > 0) {
      const msg = 'Existe otra asignación con los mismos usuario/flota';
      res.setHeader('X-Error-Message', msg);
      return res.status(409).json({ message: msg });
    }

    const [result] = await db.query(
      'UPDATE flotasusuarios SET Usuarios_idUsuarios = ?, Flotas_idFlotas = ? WHERE idFlotasUsuarios = ?',
      [Usuarios_idUsuarios, Flotas_idFlotas, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Asignación de flota no encontrada' });
    }
    
    res.json({ message: 'Asignación actualizada correctamente' });
  } catch (error) {
    console.error('[flotasUsuarios] Error in PUT /:id', error);
    handleServerError(res, 'Error al actualizar la asignación de flota', error);
  }
});

// DELETE a flotaUsuario
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM flotasusuarios WHERE idFlotasUsuarios = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Asignación de flota no encontrada' });
    }
    
    res.json({ message: 'Asignación eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la asignación de flota', error);
  }
});

// GET usuarios asociados a una flota
router.get('/flota/:flotaId/usuarios', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT u.*, fu.idFlotasUsuarios, p.PerfilesUsuarioNombre
      FROM usuarios u
      JOIN flotasusuarios fu ON u.idUsuarios = fu.Usuarios_idUsuarios
      LEFT JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario
      WHERE fu.Flotas_idFlotas = ?
    `, [req.params.flotaId]);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener usuarios de la flota', error);
  }
});

// GET usuarios no asociados a una flota
router.get('/flota/:flotaId/usuarios-no-asociados', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT u.*, p.PerfilesUsuarioNombre
      FROM usuarios u
      LEFT JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario
      WHERE u.idUsuarios NOT IN (
        SELECT Usuarios_idUsuarios FROM flotasusuarios WHERE Flotas_idFlotas = ?
      )
    `, [req.params.flotaId]);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener usuarios no asociados', error);
  }
});

module.exports = router;
