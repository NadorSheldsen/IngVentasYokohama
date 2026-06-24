const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET - Obtener todas las pruebas de inspección
router.get('/', async (req, res) => {
    try {
        const [rows] = await db.execute(`
            SELECT 
                pi.idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                pi.Flotas_idFlotas,
                pi.latitude,
                pi.longitude,
                f.FlotasNombre
            FROM pruebasinspeccion pi
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            ORDER BY pi.PruebaInspeccionFecha DESC
        `);
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener las pruebas de inspección', error);
    }
});

// GET - Obtener pruebas de inspección por flota
router.get('/flota/:flotaId', async (req, res) => {
    try {
        const { flotaId } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                pi.idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                pi.Flotas_idFlotas,
                pi.latitude,
                pi.longitude,
                f.FlotasNombre
            FROM pruebasinspeccion pi
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            WHERE pi.Flotas_idFlotas = ?
            ORDER BY pi.PruebaInspeccionFecha DESC
        `, [flotaId]);
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener las pruebas de inspección', error);
    }
});

// GET - Obtener una prueba de inspección específica
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                pi.idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                pi.Flotas_idFlotas,
                pi.latitude,
                pi.longitude,
                f.FlotasNombre
            FROM pruebasinspeccion pi
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            WHERE pi.idPruebaInspeccion = ?
        `, [id]);
        
        if (rows.length === 0) {
            return res.status(404).json({ message: 'Prueba de inspección no encontrada' });
        }
        
        res.json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al obtener la prueba de inspección', error);
    }
});

// POST - Crear nueva prueba de inspección
router.post('/', async (req, res) => {
    try {
        console.log('Datos recibidos en /api/pruebas-inspeccion:', req.body);
        const { PruebaInspeccionTitulo, Flotas_idFlotas, latitude, longitude } = req.body;

        if (!PruebaInspeccionTitulo || !Flotas_idFlotas) {
            return res.status(400).json({ message: 'Título y flota son requeridos' });
        }

        const fechaActual = new Date().toISOString().split('T')[0];

        const lat = latitude !== undefined ? Number(latitude) : null;
        const lon = longitude !== undefined ? Number(longitude) : null;

        const [result] = await db.execute(`
            INSERT INTO pruebasinspeccion (PruebaInspeccionTitulo, PruebaInspeccionFecha, Flotas_idFlotas, latitude, longitude)
            VALUES (?, ?, ?, ?, ?)
        `, [PruebaInspeccionTitulo, fechaActual, Flotas_idFlotas, lat, lon]);

        // Obtener la prueba creada
        const [rows] = await db.execute(`
            SELECT 
                pi.idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                pi.Flotas_idFlotas,
                pi.latitude,
                pi.longitude,
                f.FlotasNombre
            FROM pruebasinspeccion pi
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            WHERE pi.idPruebaInspeccion = ?
        `, [result.insertId]);

        res.status(201).json(rows[0]);
    } catch (error) {
        console.error('Error al crear prueba de inspección:', error);
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'Ya existe una prueba con ese título' });
        }
        handleServerError(res, 'Error al crear la prueba de inspección', error);
    }
});

// PUT - Actualizar prueba de inspección
router.put('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const { PruebaInspeccionTitulo, latitude, longitude } = req.body;

        if (!PruebaInspeccionTitulo) {
            return res.status(400).json({ message: 'Título es requerido' });
        }

        const lat = latitude !== undefined ? Number(latitude) : null;
        const lon = longitude !== undefined ? Number(longitude) : null;

        const [result] = await db.execute(`
            UPDATE pruebasinspeccion 
            SET PruebaInspeccionTitulo = ?, latitude = ?, longitude = ?
            WHERE idPruebaInspeccion = ?
        `, [PruebaInspeccionTitulo, lat, lon, id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Prueba de inspección no encontrada' });
        }

        // Obtener la prueba actualizada
        const [rows] = await db.execute(`
            SELECT 
                pi.idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                pi.Flotas_idFlotas,
                pi.latitude,
                pi.longitude,
                f.FlotasNombre
            FROM pruebasinspeccion pi
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            WHERE pi.idPruebaInspeccion = ?
        `, [id]);

        res.json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al actualizar la prueba de inspección', error);
    }
});

// DELETE - Eliminar prueba de inspección
router.delete('/:id', async (req, res) => {
    try {
        const { id } = req.params;

        const [result] = await db.execute(`
            DELETE FROM pruebasinspeccion WHERE idPruebaInspeccion = ?
        `, [id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Prueba de inspección no encontrada' });
        }

        res.json({ message: 'Prueba de inspección eliminada correctamente' });
    } catch (error) {
        handleServerError(res, 'Error al eliminar la prueba de inspección', error);
    }
});

module.exports = router;
