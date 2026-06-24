const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET - Obtener todos los vehículos de inspección
router.get('/', async (req, res) => {
    try {
        const [rows] = await db.execute(`
            SELECT 
                vi.idVehiculoInspeccion,
                vi.pruebasinspeccion_idPruebaInspeccion,
                vi.TipoVehiculos_idTipoVehiculos,
                vi.VehiculoInspeccionNo,
                -- Include stored coordinates when available
                vi.VehiculosInspeccionLat AS latitude,
                vi.VehiculosInspeccionLon AS longitude,
                vi.Usuarios_idUsuarios AS Usuarios_idUsuarios,
                pi.PruebaInspeccionTitulo,
                tv.TipoVehiculosNombre
            FROM vehiculosinspeccion vi
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            ORDER BY vi.VehiculoInspeccionNo
        `);
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener los vehículos de inspección', error);
    }
});

// GET - Obtener vehículos por prueba de inspección
router.get('/prueba/:pruebaId', async (req, res) => {
    try {
        const { pruebaId } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                vi.idVehiculoInspeccion,
                vi.pruebasinspeccion_idPruebaInspeccion,
                vi.TipoVehiculos_idTipoVehiculos,
                vi.VehiculoInspeccionNo,
                -- Include stored coordinates when available
                vi.VehiculosInspeccionLat AS latitude,
                vi.VehiculosInspeccionLon AS longitude,
                vi.Usuarios_idUsuarios AS Usuarios_idUsuarios,
                pi.PruebaInspeccionTitulo,
                tv.TipoVehiculosNombre
            FROM vehiculosinspeccion vi
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            WHERE vi.pruebasinspeccion_idPruebaInspeccion = ?
            ORDER BY vi.VehiculoInspeccionNo
        `, [pruebaId]);
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener los vehículos de inspección', error);
    }
});

// GET - Obtener un vehículo de inspección específico
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                vi.idVehiculoInspeccion,
                vi.pruebasinspeccion_idPruebaInspeccion,
                vi.TipoVehiculos_idTipoVehiculos,
                vi.VehiculoInspeccionNo,
                -- Include stored coordinates when available
                vi.VehiculosInspeccionLat AS latitude,
                vi.VehiculosInspeccionLon AS longitude,
                vi.Usuarios_idUsuarios AS Usuarios_idUsuarios,
                pi.PruebaInspeccionTitulo,
                tv.TipoVehiculosNombre
            FROM vehiculosinspeccion vi
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            WHERE vi.idVehiculoInspeccion = ?
        `, [id]);
        
        if (rows.length === 0) {
            return res.status(404).json({ message: 'Vehículo de inspección no encontrado' });
        }
        
        res.json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al obtener el vehículo de inspección', error);
    }
});

// POST - Crear nuevo vehículo de inspección
router.post('/', async (req, res) => {
    try {
        const { 
            pruebasinspeccion_idPruebaInspeccion, 
            TipoVehiculos_idTipoVehiculos, 
            VehiculoInspeccionNo,
            Usuarios_idUsuarios,
            latitude,
            longitude
        } = req.body;
        // Try to obtain creator id from body, authenticated user, or headers
        let creatorId = (req.body && (req.body.Usuarios_idUsuarios || req.body.UsuariosId)) || (req.user && (req.user.id || req.user.idUsuarios)) || null;

        // Log body keys and some headers to diagnose missing user id
        try {
            console.log('[vehiculosInspeccion POST] incoming body keys=%o', Object.keys(req.body || {}));
            console.log('[vehiculosInspeccion POST] initial creatorId=%o, Usuarios_idUsuarios(body)=%o', creatorId, Usuarios_idUsuarios);
            console.log('[vehiculosInspeccion POST] headers.authorization=%o, headers[x-user-id]=%o, headers[x-usuario-id]=%o', req.headers && req.headers.authorization, req.headers && req.headers['x-user-id'], req.headers && req.headers['x-usuario-id']);
        } catch (e) { console.log('[vehiculosInspeccion POST] error logging body/headers:', e); }

        // If still null, try to parse user id from common headers (development clients may send it)
        if (!creatorId) {
            try {
                const h = req.headers || {};
                const candidates = [h['x-user-id'], h['x-usuario-id'], h['x-usuarios-id']];
                for (const c of candidates) {
                    if (c) {
                        const parsed = Number(String(c));
                        if (!isNaN(parsed) && parsed > 0) {
                            creatorId = parsed;
                            break;
                        }
                    }
                }

                // Authorization: accept 'Bearer <id>' or raw numeric bearer for backwards compat
                if (!creatorId && h.authorization) {
                    const m = String(h.authorization).match(/Bearer\s+(\d+)/i);
                    if (m && m[1]) creatorId = Number(m[1]);
                    else if (!isNaN(Number(h.authorization))) creatorId = Number(h.authorization);
                }
            } catch (e) { console.log('[vehiculosInspeccion POST] error extracting id from headers:', e); }
        }
        
        if (!pruebasinspeccion_idPruebaInspeccion || !TipoVehiculos_idTipoVehiculos || !VehiculoInspeccionNo) {
            return res.status(400).json({ message: 'Todos los campos son requeridos' });
        }

        // Prevent duplicate vehicle number inside the same prueba de inspección
        const [duplicate] = await db.execute(
            'SELECT idVehiculoInspeccion FROM vehiculosinspeccion WHERE pruebasinspeccion_idPruebaInspeccion = ? AND VehiculoInspeccionNo = ? LIMIT 1',
            [pruebasinspeccion_idPruebaInspeccion, VehiculoInspeccionNo]
        );
        if (duplicate && duplicate.length > 0) {
            const msg = 'Ya existe un vehículo con ese número en la prueba de inspección';
            res.set('X-Error-Message', msg);
            return res.status(409).json({ message: msg });
        }

        const valuesToInsert = [pruebasinspeccion_idPruebaInspeccion, TipoVehiculos_idTipoVehiculos, VehiculoInspeccionNo, creatorId || Usuarios_idUsuarios || null, latitude || null, longitude || null];
        try { console.log('[vehiculosInspeccion POST] INSERT values=%o', valuesToInsert); } catch(_){}

        const [result] = await db.execute(`
            INSERT INTO vehiculosinspeccion (
                pruebasinspeccion_idPruebaInspeccion, 
                TipoVehiculos_idTipoVehiculos, 
                VehiculoInspeccionNo,
                Usuarios_idUsuarios,
                VehiculosInspeccionLat,
                VehiculosInspeccionLon
            )
            VALUES (?, ?, ?, ?, ?, ?)
        `, valuesToInsert);

        // Obtener el vehículo creado
        const [rows] = await db.execute(`
            SELECT 
                vi.idVehiculoInspeccion,
                vi.pruebasinspeccion_idPruebaInspeccion,
                vi.TipoVehiculos_idTipoVehiculos,
                vi.VehiculoInspeccionNo,
                -- Include stored coordinates when available
                vi.VehiculosInspeccionLat AS latitude,
                vi.VehiculosInspeccionLon AS longitude,
                vi.Usuarios_idUsuarios AS Usuarios_idUsuarios,
                pi.PruebaInspeccionTitulo,
                tv.TipoVehiculosNombre
            FROM vehiculosinspeccion vi
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            WHERE vi.idVehiculoInspeccion = ?
        `, [result.insertId]);

        // Also try to update the master Vehiculos row (if exists)
        try {
            if (latitude || longitude) {
                await db.execute('UPDATE vehiculos SET VehiculosLatitude = ?, VehiculosLongitude = ? WHERE VehiculosNumero = ? AND TipoVehiculos_idTipoVehiculos = ? LIMIT 1', [latitude || null, longitude || null, VehiculoInspeccionNo, TipoVehiculos_idTipoVehiculos]);
            }
        } catch (e) {
            // ignore
        }

        res.status(201).json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al crear el vehículo de inspección', error);
    }
});

// PUT - Actualizar vehículo de inspección
router.put('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const { 
            TipoVehiculos_idTipoVehiculos, 
            VehiculoInspeccionNo,
            latitude,
            longitude
        } = req.body;

        if (!TipoVehiculos_idTipoVehiculos || !VehiculoInspeccionNo) {
            return res.status(400).json({ message: 'Tipo de vehículo y número son requeridos' });
        }

        // Prevent changing to a number that already exists in the same prueba
        const [existing] = await db.execute(
            'SELECT idVehiculoInspeccion FROM vehiculosinspeccion WHERE pruebasinspeccion_idPruebaInspeccion = (SELECT pruebasinspeccion_idPruebaInspeccion FROM vehiculosinspeccion WHERE idVehiculoInspeccion = ?) AND VehiculoInspeccionNo = ? AND idVehiculoInspeccion != ? LIMIT 1',
            [id, VehiculoInspeccionNo, id]
        );
        if (existing && existing.length > 0) {
            const msg = 'Ya existe otro vehículo con ese número en la misma prueba de inspección';
            res.set('X-Error-Message', msg);
            return res.status(409).json({ message: msg });
        }

        const [result] = await db.execute(`
            UPDATE vehiculosinspeccion 
            SET TipoVehiculos_idTipoVehiculos = ?, VehiculoInspeccionNo = ?, VehiculosInspeccionLat = ?, VehiculosInspeccionLon = ?
            WHERE idVehiculoInspeccion = ?
        `, [TipoVehiculos_idTipoVehiculos, VehiculoInspeccionNo, latitude || null, longitude || null, id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Vehículo de inspección no encontrado' });
        }

        // Obtener el vehículo actualizado
        const [rows] = await db.execute(`
            SELECT 
                vi.idVehiculoInspeccion,
                vi.pruebasinspeccion_idPruebaInspeccion,
                vi.TipoVehiculos_idTipoVehiculos,
                vi.VehiculoInspeccionNo,
                vi.Usuarios_idUsuarios AS Usuarios_idUsuarios,
                pi.PruebaInspeccionTitulo,
                tv.TipoVehiculosNombre
            FROM vehiculosinspeccion vi
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            WHERE vi.idVehiculoInspeccion = ?
        `, [id]);

        // Also try to update the master Vehiculos row (if exists)
        try {
            if (latitude || longitude) {
                await db.execute('UPDATE vehiculos SET VehiculosLatitude = ?, VehiculosLongitude = ? WHERE VehiculosNumero = ? AND TipoVehiculos_idTipoVehiculos = ? LIMIT 1', [latitude || null, longitude || null, VehiculoInspeccionNo, TipoVehiculos_idTipoVehiculos]);
            }
        } catch (e) {
            // ignore
        }

        res.json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al actualizar el vehículo de inspección', error);
    }
});

// DELETE - Eliminar vehículo de inspección
router.delete('/:id', async (req, res) => {
    try {
        const { id } = req.params;

        const [result] = await db.execute(`
            DELETE FROM vehiculosinspeccion WHERE idVehiculoInspeccion = ?
        `, [id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Vehículo de inspección no encontrado' });
        }

        res.json({ message: 'Vehículo de inspección eliminado correctamente' });
    } catch (error) {
        handleServerError(res, 'Error al eliminar el vehículo de inspección', error);
    }
});

module.exports = router;
