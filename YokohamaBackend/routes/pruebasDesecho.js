const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all pruebas desecho
router.get('/', async (req, res) => {
  try {
        const flotaQuery = req.query.Flotas_idFlotas || req.query.flotaId || req.query.flotaName || req.query.FlotasNombre || null;
        let sql = `
      SELECT pd.*,
             COALESCE(pd.Flotas_idFlotas, inferred.Flotas_idFlotas) AS Flotas_idFlotas,
             COALESCE(f.FlotasNombre, inferred.FlotasNombre) AS FlotasNombre
      FROM pruebasdesecho pd
      LEFT JOIN flotas f ON pd.Flotas_idFlotas = f.idFlotas
      LEFT JOIN (
        SELECT ld.PruebasDesecho_idPruebasDesecho,
               MIN(v.Flotas_idFlotas) AS Flotas_idFlotas,
               MIN(f2.FlotasNombre) AS FlotasNombre
        FROM llantasdesecho ld
        LEFT JOIN llantasvehiculos lv ON ld.Llantas_idLlantas = lv.Llantas_idLlantas
        LEFT JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
        LEFT JOIN flotas f2 ON v.Flotas_idFlotas = f2.idFlotas
        GROUP BY ld.PruebasDesecho_idPruebasDesecho
      ) inferred ON inferred.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
    `;
    const params = [];
    if (flotaQuery) {
      // If numeric, treat as id else treat as name (partial match)
      if (!isNaN(Number(flotaQuery))) {
        sql += ` WHERE COALESCE(pd.Flotas_idFlotas, inferred.Flotas_idFlotas) = ?`;
        params.push(Number(flotaQuery));
      } else {
        sql += ` WHERE LOWER(COALESCE(f.FlotasNombre, inferred.FlotasNombre, '')) LIKE ?`;
        params.push('%' + String(flotaQuery).trim().toLowerCase() + '%');
      }
    }
    const [rows] = await db.query(sql, params);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las pruebas de desecho', error);
  }
});

// GET a specific prueba desecho by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT pd.*,
             COALESCE(pd.Flotas_idFlotas, inferred.Flotas_idFlotas) AS Flotas_idFlotas,
             COALESCE(f.FlotasNombre, inferred.FlotasNombre) AS FlotasNombre
      FROM pruebasdesecho pd
      LEFT JOIN flotas f ON pd.Flotas_idFlotas = f.idFlotas
      LEFT JOIN (
        SELECT ld.PruebasDesecho_idPruebasDesecho,
               MIN(v.Flotas_idFlotas) AS Flotas_idFlotas,
               MIN(f2.FlotasNombre) AS FlotasNombre
        FROM llantasdesecho ld
        LEFT JOIN llantasvehiculos lv ON ld.Llantas_idLlantas = lv.Llantas_idLlantas
        LEFT JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
        LEFT JOIN flotas f2 ON v.Flotas_idFlotas = f2.idFlotas
        GROUP BY ld.PruebasDesecho_idPruebasDesecho
      ) inferred ON inferred.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
      WHERE pd.idPruebasDesecho = ?
    `, [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Prueba de desecho no encontrada' });
    }
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la prueba de desecho', error);
  }
});

// POST a new prueba desecho
router.post('/', async (req, res) => {
  try {
    const { PruebasDesechoNombre, PruebasDesechoFecha, Flotas_idFlotas, idFlotas } = req.body || {};
    // allow flota via body or querystring
    let flotaId = Flotas_idFlotas || idFlotas || req.query.Flotas_idFlotas || req.query.flotaId || null;
    if (flotaId !== null && flotaId !== undefined && flotaId !== '') {
      const parsed = Number(flotaId);
      if (!isNaN(parsed)) flotaId = parsed;
    } else {
      flotaId = null;
    }

    // Validate presence: DB requires Flotas_idFlotas non-null
    if (flotaId == null) {
      return res.status(400).json({ message: 'Flotas_idFlotas es obligatorio al crear una prueba de desecho' });
    }

    const [result] = await db.query(
      'INSERT INTO pruebasdesecho (PruebasDesechoNombre, PruebasDesechoFecha, Flotas_idFlotas) VALUES (?, ?, ?)',
      [PruebasDesechoNombre, PruebasDesechoFecha, flotaId]
    );

    // Return the newly created row so clients receive the full object
    const [rows] = await db.query(`
      SELECT pd.*, f.FlotasNombre
      FROM pruebasdesecho pd
      LEFT JOIN flotas f ON pd.Flotas_idFlotas = f.idFlotas
      WHERE pd.idPruebasDesecho = ?
    `, [result.insertId]);
    if (rows.length === 0) {
      return res.status(201).json({ message: 'Prueba desecho creada, pero no se pudo recuperar el registro' });
    }
    res.status(201).json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear la prueba de desecho', error);
  }
});

// PUT/UPDATE a prueba desecho
router.put('/:id', async (req, res) => {
  try {
    const { PruebasDesechoNombre, PruebasDesechoFecha, Flotas_idFlotas, idFlotas } = req.body;
    const flotaId = Flotas_idFlotas || idFlotas || null;
    
    const [result] = await db.query(
      'UPDATE pruebasdesecho SET PruebasDesechoNombre = ?, PruebasDesechoFecha = ?, Flotas_idFlotas = ? WHERE idPruebasDesecho = ?',
      [PruebasDesechoNombre, PruebasDesechoFecha, flotaId, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba de desecho no encontrada' });
    }
    
    res.json({ message: 'Prueba de desecho actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la prueba de desecho', error);
  }
});

// DELETE a prueba desecho
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM pruebasdesecho WHERE idPruebasDesecho = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba de desecho no encontrada' });
    }
    
    res.json({ message: 'Prueba de desecho eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la prueba de desecho', error);
  }
});

module.exports = router;
