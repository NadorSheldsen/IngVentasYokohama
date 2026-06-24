const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all flotas
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM flotas');
    res.json(rows);
  } catch (error) {
    // Server-side log and user-friendly message
    handleServerError(res, 'Error al obtener las flotas', error);
  }
});

// GET a specific flota by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM flotas WHERE idFlotas = ?', [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Flota no encontrada' });
    }
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la flota', error);
  }
});

// POST a new flota
router.post('/', async (req, res) => {
  try {
    const { FlotasNombre, FlotasClasificacion, FlotasZona, FlotasEstado, FlotasCiudad } = req.body;
    // Check for duplicate name (case-insensitive)
    try {
      const [existing] = await db.query('SELECT idFlotas FROM flotas WHERE LOWER(FlotasNombre) = LOWER(?)', [FlotasNombre]);
      if (existing.length > 0) {
        const dupMsg = `Ya existe una flota con ese nombre ('${FlotasNombre}'). Elige otro nombre o usa la flota existente.`;
        res.setHeader('X-Error-Message', dupMsg);
        return res.status(409).json({ message: dupMsg });
      }
    } catch (dupErr) {
      console.error('Error verificando duplicado de flota:', dupErr);
      // proceed to normal error handling below if check fails
    }
    
    const [result] = await db.query(
      'INSERT INTO flotas (FlotasNombre, FlotasClasificacion, FlotasZona, FlotasEstado, FlotasCiudad) VALUES (?, ?, ?, ?, ?)',
      [FlotasNombre, FlotasClasificacion, FlotasZona, FlotasEstado, FlotasCiudad]
    );

    // Fetch the created flota and return it so clients can deserialize to the Flota model
    try {
      const [rows] = await db.query('SELECT * FROM flotas WHERE idFlotas = ?', [result.insertId]);
      const created = (rows.length === 0) ? { message: 'Flota creada', id: result.insertId } : rows[0];

      // If the request included a Usuarios_idUsuarios field, auto-associate the creator to the flota
      try {
        const creatorId = req.body && (req.body.Usuarios_idUsuarios || req.body.UsuariosId || req.body.createdByUsuarioId);
        if (creatorId) {
          // prevent duplicate
          const [existsAssoc] = await db.query('SELECT idFlotasUsuarios FROM flotasusuarios WHERE Usuarios_idUsuarios = ? AND Flotas_idFlotas = ?', [creatorId, result.insertId]);
          if (existsAssoc.length === 0) {
            await db.query('INSERT INTO flotasusuarios (Usuarios_idUsuarios, Flotas_idFlotas) VALUES (?, ?)', [creatorId, result.insertId]);
            console.log('[flotas] Auto-associated user', creatorId, 'to flota', result.insertId);
          } else {
            console.log('[flotas] Association already exists for user', creatorId, 'and flota', result.insertId);
          }
        }
      } catch (assocErr) {
        console.error('Error auto-asociando usuario a la flota:', assocErr);
        // don't fail the flota creation if association fails; just log
      }

      if (rows.length === 0) {
        return res.status(201).json(created);
      }
      res.status(201).json(created);
    } catch (fetchError) {
      console.error('Error al obtener la flota creada:', fetchError);
      res.status(201).json({ message: 'Flota creada', id: result.insertId });
    }
  } catch (error) {
    handleServerError(res, 'Error al crear la flota', error);
  }
});

// PUT/UPDATE a flota
router.put('/:id', async (req, res) => {
  try {
    const { FlotasNombre, FlotasClasificacion, FlotasZona, FlotasEstado, FlotasCiudad } = req.body;
    // Prevent renaming to a name that already exists on a different flota
    try {
      const [conflict] = await db.query('SELECT idFlotas FROM flotas WHERE LOWER(FlotasNombre) = LOWER(?) AND idFlotas <> ?', [FlotasNombre, req.params.id]);
      if (conflict.length > 0) {
        const dupMsg = `No se puede actualizar: otra flota ya usa el nombre '${FlotasNombre}'. Elige un nombre diferente.`;
        res.setHeader('X-Error-Message', dupMsg);
        return res.status(409).json({ message: dupMsg });
      }
    } catch (dupErr) {
      console.error('Error verificando duplicado al actualizar flota:', dupErr);
      // continue to attempt update; any DB error will be handled below
    }
    
    const [result] = await db.query(
      'UPDATE flotas SET FlotasNombre = ?, FlotasClasificacion = ?, FlotasZona = ?, FlotasEstado = ?, FlotasCiudad = ? WHERE idFlotas = ?',
      [FlotasNombre, FlotasClasificacion, FlotasZona, FlotasEstado, FlotasCiudad, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Flota not found' });
    }
    
    res.json({ message: 'Flota updated successfully' });
  } catch (error) {
    console.error(error);
    res.status(500).json({ message: 'Error updating flota', error: error.message });
  }
});

// DELETE a flota
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM flotas WHERE idFlotas = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Flota not found' });
    }
    
    res.json({ message: 'Flota deleted successfully' });
  } catch (error) {
    console.error(error);
    res.status(500).json({ message: 'Error deleting flota', error: error.message });
  }
});

module.exports = router;
