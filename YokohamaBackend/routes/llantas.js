const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all llantas
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM llantas');
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas', error);
  }
});

// GET a specific llanta by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query('SELECT * FROM llantas WHERE idLlantas = ?', [req.params.id]);
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Llanta no encontrada' });
    }
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener la llanta', error);
  }
});

// SEARCH llantas by medida (for autocomplete)
router.get('/search/medida', async (req, res) => {
  try {
    const { q } = req.query;
    
    if (!q) {
      return res.status(400).json({ message: 'Query parameter "q" is required' });
    }
    
    console.log('🔍 Searching llantas by medida:', q);
    
    const [rows] = await db.query(
      'SELECT * FROM llantas WHERE LlantasMedida LIKE ? ORDER BY LlantasMedida, LlantasMarca LIMIT 20',
      [`%${q}%`]
    );
    
    console.log('✅ Found llantas:', rows.length);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al buscar llantas', error);
  }
});

// POST a new llanta
router.post('/', async (req, res) => {
  try {
    const { LlantasMarca, LlantasModelo, LlantasPrecio, LlantasMedida, LlantasMm } = req.body;
    
    const [result] = await db.query(
      'INSERT INTO llantas (LlantasMarca, LlantasModelo, LlantasPrecio, LlantasMedida, LlantasMm) VALUES (?, ?, ?, ?, ?)',
      [LlantasMarca, LlantasModelo, LlantasPrecio, LlantasMedida, LlantasMm]
    );
    
    res.status(201).json({ 
      message: 'Llanta created successfully', 
      id: result.insertId 
    });
  } catch (error) {
    handleServerError(res, 'Error al crear la llanta', error);
  }
});

// PUT/UPDATE a llanta
router.put('/:id', async (req, res) => {
  try {
    const { LlantasMarca, LlantasModelo, LlantasPrecio, LlantasMedida, LlantasMm } = req.body;
    
    const [result] = await db.query(
      'UPDATE llantas SET LlantasMarca = ?, LlantasModelo = ?, LlantasPrecio = ?, LlantasMedida = ?, LlantasMm = ? WHERE idLlantas = ?',
      [LlantasMarca, LlantasModelo, LlantasPrecio, LlantasMedida, LlantasMm, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta not found' });
    }
    
    res.json({ message: 'Llanta updated successfully' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la llanta', error);
  }
});

// DELETE a llanta
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM llantas WHERE idLlantas = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta not found' });
    }
    
    res.json({ message: 'Llanta deleted successfully' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la llanta', error);
  }
});

module.exports = router;
