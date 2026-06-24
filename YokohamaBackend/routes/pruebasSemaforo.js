const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all pruebas semaforo
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM pruebassemaforo');
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las pruebas semáforo', error);
  }
});

// GET a specific prueba semaforo by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM pruebassemaforo WHERE idPruebasSemaforo = ?', [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Prueba semáforo no encontrada' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la prueba semáforo', error);
  }
});

// GET pruebas semaforo by flota ID
router.get('/flota/:flotaId', async (req, res) => {
  console.log(req.params.flotaId);
  console.log("entro");
  try {
    const [rows] = await db.query(
      'SELECT * FROM pruebassemaforo WHERE Flotas_idFlotas = ? ORDER BY PruebasSemaforoFecha DESC', 
      [req.params.flotaId]
    );
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener pruebas semáforo por flota', error);
  }
});

// POST a new prueba semaforo
router.post('/', async (req, res) => {
  try {
    const { PruebasSemaforoTitulo, Flotas_idFlotas, latitude, longitude } = req.body;
    const currentDate = new Date().toISOString().split('T')[0]; // YYYY-MM-DD format

    const lat = latitude !== undefined ? Number(latitude) : null;
    const lon = longitude !== undefined ? Number(longitude) : null;

    const [result] = await db.query(
      'INSERT INTO pruebassemaforo (PruebasSemaforoFecha, PruebasSemaforoTitulo, Flotas_idFlotas, latitude, longitude) VALUES (?, ?, ?, ?, ?)',
      [currentDate, PruebasSemaforoTitulo, Flotas_idFlotas, lat, lon]
    );
    
    // Try to return the created prueba. Add logging and fallback so the client always
    // receives a predictable JSON shape (either the full row or at least the id).
    try {
      const [newPrueba] = await db.query(
        'SELECT * FROM pruebassemaforo WHERE idPruebasSemaforo = ?',
        [result.insertId]
      );

      if (newPrueba && newPrueba.length > 0) {
        const responseObj = newPrueba[0];
        console.log('Created PruebasSemaforo:', responseObj);
        return res.status(201).json(responseObj);
      }
    } catch (selectError) {
      console.error('Error fetching created PruebasSemaforo:', selectError);
    }

    // Fallback: return the insertId so the client can GET the resource if needed
    return res.status(201).json({ idPruebasSemaforo: result.insertId });
  } catch (error) {
    handleServerError(res, 'Error al crear la prueba semáforo', error);
  }
});

// PUT/UPDATE a prueba semaforo
router.put('/:id', async (req, res) => {
  try {
    const { PruebasSemaforoFecha, latitude, longitude } = req.body;

    const lat = latitude !== undefined ? Number(latitude) : null;
    const lon = longitude !== undefined ? Number(longitude) : null;

    const [result] = await db.query(
      'UPDATE pruebassemaforo SET PruebasSemaforoFecha = ?, latitude = ?, longitude = ? WHERE idPruebasSemaforo = ?',
      [PruebasSemaforoFecha, lat, lon, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba semáforo no encontrada' });
    }
    
    res.json({ message: 'Prueba semáforo actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la prueba semáforo', error);
  }
});

// DELETE a prueba semaforo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM pruebassemaforo WHERE idPruebasSemaforo = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Prueba semáforo no encontrada' });
    }
    
    res.json({ message: 'Prueba semáforo eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la prueba semáforo', error);
  }
});

module.exports = router;