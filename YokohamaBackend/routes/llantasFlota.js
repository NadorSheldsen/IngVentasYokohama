const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
console.log('🔔 llantasFlota route loaded')

// GET - Obtener llantas disponibles (todas) o por flota si flotaId proporcionado
router.get('/flota/:flotaId', async (req, res) => {
    try {
        console.log('GET /api/llantas-flota/flota/:flotaId - params:', req.params)
        const { flotaId } = req.params;
        const [rows] = await db.execute(`
            SELECT l.idLlantas, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio,
                (SELECT COUNT(*) FROM llantasflota lf WHERE lf.Llantas_idLlantas = l.idLlantas AND lf.Flotas_idFlotas = ?) AS asociada
            FROM llantas l
            ORDER BY l.LlantasMarca, l.LlantasModelo
        `, [flotaId]);
        console.log('GET /api/llantas-flota/flota/:flotaId - rows count:', rows.length)
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener llantas', error);
    }
});

// GET - Obtener todas las llantas
router.get('/', async (req, res) => {
    try {
        const [rows] = await db.execute(`SELECT idLlantas, LlantasMarca, LlantasModelo, LlantasMedida FROM llantas ORDER BY LlantasMarca, LlantasModelo`);
        res.json(rows);
    } catch (error) {
        handleServerError(res, 'Error al obtener llantas', error);
    }
});

// POST - Asociar llanta a flota
router.post('/', async (req, res) => {
    try {
        const { Llantas_idLlantas, Flotas_idFlotas } = req.body;
        if (!Llantas_idLlantas || !Flotas_idFlotas) {
            return res.status(400).json({ message: 'Llantas_idLlantas y Flotas_idFlotas son requeridos' });
        }

        // Evitar duplicados
        const [exists] = await db.execute(`SELECT idLlantasFlota FROM llantasflota WHERE Llantas_idLlantas = ? AND Flotas_idFlotas = ?`, [Llantas_idLlantas, Flotas_idFlotas]);
        if (exists.length > 0) {
            return res.status(409).json({ message: 'La llanta ya está asociada a la flota' });
        }

        const [result] = await db.execute(`INSERT INTO llantasflota (Llantas_idLlantas, Flotas_idFlotas) VALUES (?, ?)`, [Llantas_idLlantas, Flotas_idFlotas]);
        // After successfully associating the llanta, ensure there's a Parametros placeholder
        try {
            const [paramExists] = await db.execute(`SELECT idParametros FROM parametros WHERE Flotas_idFlotas = ? AND Llantas_idLlantas = ?`, [Flotas_idFlotas, Llantas_idLlantas]);
            if (!paramExists || paramExists.length === 0) {
                console.log('Inserting placeholder parametro for flota=%s llanta=%s', Flotas_idFlotas, Llantas_idLlantas)
                // Insert a placeholder with NULLs so the UI can mark it as 'Pendiente'
                await db.execute(`INSERT INTO parametros (Flotas_idFlotas, Llantas_idLlantas, ParametrosRC, ParametrosPMin, ParametrosPSug, ParametrosPMax, ParametrosProfMin, ParametrosProfMax) VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, NULL)`, [Flotas_idFlotas, Llantas_idLlantas]);
            }
        } catch (paramErr) {
            // Non-fatal: log and continue. We still return the association result so client can refresh local list.
            console.error('Error inserting placeholder parametro:', paramErr);
        }

        res.status(201).json({ idLlantasFlota: result.insertId });
    } catch (error) {
        handleServerError(res, 'Error al asociar llanta', error);
    }
});

// DELETE - Desasociar llanta de flota (por idLlantasFlota)
// DELETE - Desasociar llanta de flota por llanta y flota
router.delete('/by-llanta-flota', async (req, res) => {
    try {
        console.log('DELETE /api/llantas-flota/by-llanta-flota - raw body:', req.body, 'query:', req.query)
        // Aceptar ids desde body o querystring (fallback)
        let Llantas_idLlantas = (req.body && req.body.Llantas_idLlantas) || req.query.Llantas_idLlantas;
        let Flotas_idFlotas = (req.body && req.body.Flotas_idFlotas) || req.query.Flotas_idFlotas;

        // Intentar parsear a enteros para evitar errores SQL si vienen como strings o valores no válidos
        const llantaId = parseInt(Llantas_idLlantas);
        const flotaId = parseInt(Flotas_idFlotas);
        console.log('Parsed ids - llantaId:', llantaId, 'flotaId:', flotaId)
        if (Number.isNaN(llantaId) || Number.isNaN(flotaId)) {
            return res.status(400).json({ message: 'Llantas_idLlantas y Flotas_idFlotas deben ser enteros válidos', received: { Llantas_idLlantas, Flotas_idFlotas } });
        }

        const [rows] = await db.execute(`SELECT idLlantasFlota FROM llantasflota WHERE Llantas_idLlantas = ? AND Flotas_idFlotas = ?`, [llantaId, flotaId]);
        console.log('Rows returned from SELECT:', rows && rows.length ? rows.length : 0);
        if (!rows || rows.length === 0) return res.status(404).json({ message: 'Asociación no encontrada' });

        const id = rows[0].idLlantasFlota;
        console.log('Deleting idLlantasFlota =', id);
        await db.execute(`DELETE FROM llantasflota WHERE idLlantasFlota = ?`, [id]);
        res.json({ message: 'Desasociada correctamente' });
    } catch (error) {
        handleServerError(res, 'Error al desasociar llanta', error);
    }
});

// DELETE - Desasociar llanta de flota (por idLlantasFlota)
router.delete('/:id', async (req, res) => {
    try {
        console.log('DELETE /api/llantas-flota/:id - params:', req.params)
        const { id } = req.params;
        const [result] = await db.execute(`DELETE FROM llantasflota WHERE idLlantasFlota = ?`, [id]);
        if (result.affectedRows === 0) return res.status(404).json({ message: 'Asociación no encontrada' });
        res.json({ message: 'Desasociada correctamente' });
    } catch (error) {
        handleServerError(res, 'Error al desasociar llanta', error);
    }
});

module.exports = router;
