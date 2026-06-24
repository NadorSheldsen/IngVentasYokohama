const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

console.log('✅ Parametros routes loaded');

// Test route
router.get('/test', (req, res) => {
    console.log('🧪 Test route hit: /api/parametros/test');
    res.json({ message: 'Parametros route is working!' });
});

// Simple route to check database
router.get('/check-table', async (req, res) => {
    try {
        console.log('🔍 Checking Parametros table...');
        const [rows] = await db.query('SELECT COUNT(*) as count FROM parametros');
        console.log('✅ Table exists, count:', rows[0].count);
        res.json({ 
            message: 'Table exists', 
            count: rows[0].count 
        });
    } catch (error) {
        handleServerError(res, 'Error al verificar la tabla de parámetros', error);
    }
});

// Obtener todos los parámetros de una flota
router.get('/flota/:flotaId', async (req, res) => {
    try {
        const { flotaId } = req.params;
        console.log('📋 GET /api/parametros/flota/:flotaId - flotaId:', flotaId);
        
        const query = `
            SELECT 
                p.*,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                l.LlantasMm
            FROM parametros p
            INNER JOIN llantas l ON p.Llantas_idLlantas = l.idLlantas
            WHERE p.Flotas_idFlotas = ?
            ORDER BY l.LlantasMedida
        `;
        
        const [parametros] = await db.query(query, [flotaId]);
        console.log('✅ Parametros encontrados:', parametros.length);
        res.json(parametros);
    } catch (error) {
        handleServerError(res, 'Error al obtener parámetros', error);
    }
});

// Obtener un parámetro específico por ID
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const query = `
            SELECT 
                p.*,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                l.LlantasMm
            FROM parametros p
            INNER JOIN llantas l ON p.Llantas_idLlantas = l.idLlantas
            WHERE p.idParametros = ?
        `;
        
        const [parametros] = await db.query(query, [id]);
        
        if (parametros.length === 0) {
            return res.status(404).json({ message: 'Parámetro no encontrado' });
        }
        
        res.json(parametros[0]);
    } catch (error) {
        handleServerError(res, 'Error al obtener parámetro', error);
    }
});

// Crear un nuevo parámetro
router.post('/', async (req, res) => {
    try {
        const {
            Flotas_idFlotas,
            Llantas_idLlantas,
            ParametrosRC,
            ParametrosPMin,
            ParametrosPSug,
            ParametrosPMax,
            ParametrosProfMin,
            ParametrosProfMax
        } = req.body;

        // Validar campos requeridos
        if (!Flotas_idFlotas || !Llantas_idLlantas || !ParametrosRC || 
            ParametrosPMin === undefined || ParametrosPSug === undefined || 
            ParametrosPMax === undefined || !ParametrosProfMin || !ParametrosProfMax) {
            return res.status(400).json({ message: 'Todos los campos son requeridos' });
        }

        // Verificar si ya existe un parámetro para esta flota y llanta
        const checkQuery = `
            SELECT p.idParametros, l.LlantasMedida
            FROM parametros p
            INNER JOIN llantas l ON p.Llantas_idLlantas = l.idLlantas
            WHERE p.Flotas_idFlotas = ? AND p.Llantas_idLlantas = ?
        `;
        const [existing] = await db.query(checkQuery, [Flotas_idFlotas, Llantas_idLlantas]);

        if (existing.length > 0) {
            return res.status(409).json({ 
                message: `Ya existe un parámetro para la llanta ${existing[0].LlantasMedida} en esta flota` 
            });
        }

        const insertQuery = `
            INSERT INTO parametros (
                Flotas_idFlotas,
                Llantas_idLlantas,
                ParametrosRC,
                ParametrosPMin,
                ParametrosPSug,
                ParametrosPMax,
                ParametrosProfMin,
                ParametrosProfMax
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `;

        const [result] = await db.query(insertQuery, [
            Flotas_idFlotas,
            Llantas_idLlantas,
            ParametrosRC,
            ParametrosPMin,
            ParametrosPSug,
            ParametrosPMax,
            ParametrosProfMin,
            ParametrosProfMax
        ]);

        // Obtener el parámetro creado con información de la llanta
        const selectQuery = `
            SELECT 
                p.*,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                l.LlantasMm
            FROM parametros p
            INNER JOIN llantas l ON p.Llantas_idLlantas = l.idLlantas
            WHERE p.idParametros = ?
        `;
        const [parametro] = await db.query(selectQuery, [result.insertId]);

        res.status(201).json(parametro[0]);
    } catch (error) {
        handleServerError(res, 'Error al crear parámetro', error);
    }
});

// Actualizar un parámetro existente
router.put('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const {
            ParametrosRC,
            ParametrosPMin,
            ParametrosPSug,
            ParametrosPMax,
            ParametrosProfMin,
            ParametrosProfMax
        } = req.body;

        // Validar campos requeridos
        if (!ParametrosRC || ParametrosPMin === undefined || 
            ParametrosPSug === undefined || ParametrosPMax === undefined || 
            !ParametrosProfMin || !ParametrosProfMax) {
            return res.status(400).json({ message: 'Todos los campos son requeridos' });
        }

        const updateQuery = `
            UPDATE parametros 
            SET 
                ParametrosRC = ?,
                ParametrosPMin = ?,
                ParametrosPSug = ?,
                ParametrosPMax = ?,
                ParametrosProfMin = ?,
                ParametrosProfMax = ?
            WHERE idParametros = ?
        `;

        const [result] = await db.query(updateQuery, [
            ParametrosRC,
            ParametrosPMin,
            ParametrosPSug,
            ParametrosPMax,
            ParametrosProfMin,
            ParametrosProfMax,
            id
        ]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Parámetro no encontrado' });
        }

        // Obtener el parámetro actualizado con información de la llanta
        const selectQuery = `
            SELECT 
                p.*,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                l.LlantasMm
            FROM parametros p
            INNER JOIN llantas l ON p.Llantas_idLlantas = l.idLlantas
            WHERE p.idParametros = ?
        `;
        const [parametro] = await db.query(selectQuery, [id]);

        res.json(parametro[0]);
    } catch (error) {
        handleServerError(res, 'Error al actualizar parámetro', error);
    }
});

// Eliminar un parámetro
router.delete('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        
        const deleteQuery = 'DELETE FROM parametros WHERE idParametros = ?';
        const [result] = await db.query(deleteQuery, [id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Parámetro no encontrado' });
        }

        res.json({ message: 'Parámetro eliminado exitosamente' });
    } catch (error) {
        handleServerError(res, 'Error al eliminar parámetro', error);
    }
});

module.exports = router;
