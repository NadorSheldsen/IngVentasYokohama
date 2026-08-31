const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
const { processImageForStorage } = require('../utils/imageProcessor');

// Helper to convert Buffer photo fields to base64 strings so API always returns strings
function normalizeFotoFields(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    try {
        if (obj.LlantasInspeccionFoto && Buffer.isBuffer(obj.LlantasInspeccionFoto)) {
            obj.LlantasInspeccionFoto = obj.LlantasInspeccionFoto.toString('utf8');
        }
    } catch (_) {}
    try {
        if (obj.LlantasInspeccionFoto2 && Buffer.isBuffer(obj.LlantasInspeccionFoto2)) {
            obj.LlantasInspeccionFoto2 = obj.LlantasInspeccionFoto2.toString('utf8');
        }
    } catch (_) {}
    return obj;
}

// GET - Obtener todas las llantas de inspección
router.get('/', async (req, res) => {
    try {
        const [rows] = await db.execute(`
            SELECT 
                li.idLlantasInspeccion,
                li.vehiculosinspeccion_idVehiculoInspeccion,
                li.Llantas_idLlantas,
                li.LlantasInspeccionMm1,
                li.LlantasInspeccionMm2,
                li.LlantasInspeccionMm3,
                li.LlantasInspeccionMm4,
                li.LlantasInspeccionPresion,
                li.LlantasInspeccionVigia,
                li.LlantasInspeccionCondPel,
                li.LlantasInspeccionObservacion,
                li.LlantasInspeccionComentario,
                li.LlantasInspeccionDOT,
                li.LlantasInspeccionPiso,
                li.LlantasInspeccionDesgaste,
                li.LlantasInspeccionFoto,
                li.LlantasInspeccionFoto2,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                vi.VehiculoInspeccionNo,
                vi.pruebasinspeccion_idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                f.FlotasNombre,
                f.idFlotas as Flotas_idFlotas,
                tv.TipoVehiculosNombre,
                p.ParametrosPMin,
                p.ParametrosPSug,
                p.ParametrosPMax
            FROM llantasinspeccion li
                LEFT JOIN llantas l ON li.Llantas_idLlantas = l.idLlantas
            LEFT JOIN vehiculosinspeccion vi ON li.vehiculosinspeccion_idVehiculoInspeccion = vi.idVehiculoInspeccion
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            LEFT JOIN parametros p ON p.Flotas_idFlotas = pi.Flotas_idFlotas AND p.Llantas_idLlantas = li.Llantas_idLlantas
        `);
        const normalized = rows.map(row => normalizeFotoFields(row));
        res.json(normalized);
        } catch (error) {
            handleServerError(res, 'Error al obtener las llantas de inspección', error);
        }
});

// GET - Obtener llantas por vehículo de inspección
router.get('/vehiculo/:vehiculoId', async (req, res) => {
    try {
        const { vehiculoId } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                li.idLlantasInspeccion,
                li.vehiculosinspeccion_idVehiculoInspeccion,
                li.Llantas_idLlantas,
                li.LlantasInspeccionMm1,
                li.LlantasInspeccionMm2,
                li.LlantasInspeccionMm3,
                li.LlantasInspeccionMm4,
                li.LlantasInspeccionPresion,
                li.LlantasInspeccionVigia,
                li.LlantasInspeccionCondPel,
                li.LlantasInspeccionObservacion,
                li.LlantasInspeccionComentario,
                li.LlantasInspeccionDOT,
                li.LlantasInspeccionPiso,
                li.LlantasInspeccionDesgaste,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                li.LlantasInspeccionFoto,
                li.LlantasInspeccionFoto2,
                vi.pruebasinspeccion_idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                f.FlotasNombre,
                f.idFlotas as Flotas_idFlotas,
                tv.TipoVehiculosNombre,
                p.ParametrosPMin,
                p.ParametrosPSug,
                p.ParametrosPMax
            FROM llantasinspeccion li
                LEFT JOIN llantas l ON li.Llantas_idLlantas = l.idLlantas
            LEFT JOIN vehiculosinspeccion vi ON li.vehiculosinspeccion_idVehiculoInspeccion = vi.idVehiculoInspeccion
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            LEFT JOIN parametros p ON p.Flotas_idFlotas = pi.Flotas_idFlotas AND p.Llantas_idLlantas = li.Llantas_idLlantas
            WHERE li.vehiculosinspeccion_idVehiculoInspeccion = ?
        `, [vehiculoId]);
        const normalized = rows.map(row => normalizeFotoFields(row));
        res.json(normalized);
    } catch (error) {
        handleServerError(res, 'Error al obtener las llantas de inspección', error);
    }
});

// GET - Obtener una llanta de inspección específica
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const [rows] = await db.execute(`
            SELECT 
                li.idLlantasInspeccion,
                li.vehiculosinspeccion_idVehiculoInspeccion,
                li.Llantas_idLlantas,
                li.LlantasInspeccionMm1,
                li.LlantasInspeccionMm2,
                li.LlantasInspeccionMm3,
                li.LlantasInspeccionMm4,
                li.LlantasInspeccionPresion,
                li.LlantasInspeccionVigia,
                li.LlantasInspeccionCondPel,
                li.LlantasInspeccionObservacion,
                li.LlantasInspeccionComentario,
                li.LlantasInspeccionDOT,
                li.LlantasInspeccionPiso,
                li.LlantasInspeccionDesgaste,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                li.LlantasInspeccionFoto,
                li.LlantasInspeccionFoto2,
                vi.pruebasinspeccion_idPruebaInspeccion,
                pi.PruebaInspeccionTitulo,
                pi.PruebaInspeccionFecha,
                f.FlotasNombre,
                f.idFlotas as Flotas_idFlotas,
                tv.TipoVehiculosNombre,
                p.ParametrosPMin,
                p.ParametrosPSug,
                p.ParametrosPMax
            FROM llantasinspeccion li
                LEFT JOIN llantas l ON li.Llantas_idLlantas = l.idLlantas
            LEFT JOIN vehiculosinspeccion vi ON li.vehiculosinspeccion_idVehiculoInspeccion = vi.idVehiculoInspeccion
            LEFT JOIN pruebasinspeccion pi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
            LEFT JOIN flotas f ON pi.Flotas_idFlotas = f.idFlotas
            LEFT JOIN tipovehiculos tv ON vi.TipoVehiculos_idTipoVehiculos = tv.idTipoVehiculos
            LEFT JOIN parametros p ON p.Flotas_idFlotas = pi.Flotas_idFlotas AND p.Llantas_idLlantas = li.Llantas_idLlantas
            WHERE li.idLlantasInspeccion = ?
        `, [id]);
        
        if (rows.length === 0) {
            return res.status(404).json({ message: 'Llanta de inspección no encontrada' });
        }
        
        res.json(normalizeFotoFields(rows[0]));
    } catch (error) {
        handleServerError(res, 'Error al obtener la llanta de inspección', error);
    }
});

// POST - Crear múltiples llantas de inspección (batch)
// NOTA: Esta ruta DEBE ir antes que router.post('/') porque es más específica
router.post('/batch', async (req, res) => {
    const connection = await db.getConnection();
    
    try {
        await connection.beginTransaction();
        
        const llantasData = req.body;

        // Debug: log payload size and first element shape to confirm batch arrives
        console.log('[llantas-inspeccion/batch] items:', Array.isArray(llantasData) ? llantasData.length : 'no-array');
        if (Array.isArray(llantasData) && llantasData.length > 0) {
            const sample = llantasData[0];
            console.log('[llantas-inspeccion/batch] sample item keys:', Object.keys(sample || {}));
            console.log('[llantas-inspeccion/batch] sample item:', sample);
        }
        
        if (!Array.isArray(llantasData) || llantasData.length === 0) {
            return res.status(400).json({ message: 'Se requiere un array de llantas' });
        }

        const results = [];
        
        for (const [index, llanta] of llantasData.entries()) {
            const {
                vehiculosinspeccion_idVehiculoInspeccion,
                Llantas_idLlantas,
                LlantasInspeccionMm1,
                LlantasInspeccionMm2,
                LlantasInspeccionMm3,
                LlantasInspeccionMm4,
                LlantasInspeccionPresion,
                LlantasInspeccionCondPel,
                LlantasInspeccionFoto,
                LlantasInspeccionObservacion,
                LlantasInspeccionComentario,
                LlantasInspeccionFoto2,
                LlantasInspeccionDOT,
                LlantasInspeccionPiso,
                LlantasInspeccionDesgaste
            } = llanta;

            const processedFoto1 = await processImageForStorage(LlantasInspeccionFoto);
            const processedFoto2 = await processImageForStorage(LlantasInspeccionFoto2);

            // Debug each item with index to ensure full batch is processed
            console.log(`[llantas-inspeccion/batch] processing item ${index + 1}/${llantasData.length}`, {
                vehiculosinspeccion_idVehiculoInspeccion,
                Llantas_idLlantas,
                LlantasInspeccionMm1,
                LlantasInspeccionMm2,
                LlantasInspeccionMm3,
                LlantasInspeccionMm4,
                LlantasInspeccionPresion,
                LlantasInspeccionCondPel,
                hasFoto1: Boolean(LlantasInspeccionFoto),
                hasFoto2: Boolean(LlantasInspeccionFoto2)
            });

            const [result] = await connection.execute(`
                INSERT INTO llantasinspeccion (
                    vehiculosinspeccion_idVehiculoInspeccion,
                    Llantas_idLlantas,
                    LlantasInspeccionMm1,
                    LlantasInspeccionMm2,
                    LlantasInspeccionMm3,
                    LlantasInspeccionMm4,
                    LlantasInspeccionPresion,
                    LlantasInspeccionCondPel,
                    LlantasInspeccionFoto,
                    LlantasInspeccionObservacion,
                    LlantasInspeccionComentario,
                    LlantasInspeccionFoto2,
                    LlantasInspeccionDOT,
                    LlantasInspeccionPiso,
                    LlantasInspeccionDesgaste
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `, [
                vehiculosinspeccion_idVehiculoInspeccion,
                Llantas_idLlantas,
                LlantasInspeccionMm1,
                LlantasInspeccionMm2,
                LlantasInspeccionMm3,
                LlantasInspeccionMm4,
                LlantasInspeccionPresion,
                LlantasInspeccionCondPel,
                processedFoto1,
                LlantasInspeccionObservacion || null,
                LlantasInspeccionComentario || null,
                processedFoto2,
                LlantasInspeccionDOT || null,
                LlantasInspeccionPiso || null,
                LlantasInspeccionDesgaste || null
            ]);
            
            results.push({ id: result.insertId });
        }

        await connection.commit();
        console.log('[llantas-inspeccion/batch] insert results:', results);
        res.status(201).json({ 
            message: `${results.length} llantas de inspección creadas correctamente`,
            results 
        });
    } catch (error) {
        await connection.rollback();
        handleServerError(res, 'Error al crear llantas de inspección', error);
    } finally {
        connection.release();
    }
});

// POST - Crear nueva llanta de inspección (individual)
// NOTA: Esta ruta va después de /batch porque es menos específica
router.post('/', async (req, res) => {
    try {
        console.log('Datos recibidos en /api/llantas-inspeccion:', req.body);
        let {
            vehiculosinspeccion_idVehiculoInspeccion,
            Llantas_idLlantas,
            LlantasInspeccionMm1,
            LlantasInspeccionMm2,
            LlantasInspeccionMm3,
            LlantasInspeccionMm4,
            LlantasInspeccionPresion,
            LlantasInspeccionCondPel,
            LlantasInspeccionFoto,
            LlantasInspeccionObservacion,
            LlantasInspeccionComentario,
            LlantasInspeccionFoto2,
            LlantasInspeccionDOT,
            LlantasInspeccionPiso,
            LlantasInspeccionDesgaste
        } = req.body;

        LlantasInspeccionFoto = await processImageForStorage(LlantasInspeccionFoto);
        LlantasInspeccionFoto2 = await processImageForStorage(LlantasInspeccionFoto2);

        if (!vehiculosinspeccion_idVehiculoInspeccion || !Llantas_idLlantas || 
            LlantasInspeccionMm1 === undefined || LlantasInspeccionMm2 === undefined ||
            LlantasInspeccionMm3 === undefined || LlantasInspeccionMm4 === undefined ||
            LlantasInspeccionPresion === undefined || LlantasInspeccionCondPel === undefined) {
            return res.status(400).json({ 
                message: 'Vehículo, llanta, mediciones MM y presión son requeridos' 
            });
        }

        const [result] = await db.execute(`
            INSERT INTO llantasinspeccion (
                vehiculosinspeccion_idVehiculoInspeccion,
                Llantas_idLlantas,
                LlantasInspeccionMm1,
                LlantasInspeccionMm2,
                LlantasInspeccionMm3,
                LlantasInspeccionMm4,
                LlantasInspeccionPresion,
                LlantasInspeccionCondPel,
                LlantasInspeccionFoto,
                LlantasInspeccionObservacion,
                LlantasInspeccionComentario,
                LlantasInspeccionFoto2,
                LlantasInspeccionDOT,
                LlantasInspeccionPiso,
                LlantasInspeccionDesgaste
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, [
            vehiculosinspeccion_idVehiculoInspeccion,
            Llantas_idLlantas,
            LlantasInspeccionMm1,
            LlantasInspeccionMm2,
            LlantasInspeccionMm3,
            LlantasInspeccionMm4,
            LlantasInspeccionPresion,
            LlantasInspeccionCondPel,
            LlantasInspeccionFoto,
            LlantasInspeccionObservacion || null,
            LlantasInspeccionComentario || null,
            LlantasInspeccionFoto2,
            LlantasInspeccionDOT || null,
            LlantasInspeccionPiso || null,
            LlantasInspeccionDesgaste || null
        ]);

        // Obtener la llanta creada
        const [rows] = await db.execute(`
            SELECT 
                li.idLlantasInspeccion,
                li.vehiculosinspeccion_idVehiculoInspeccion,
                li.Llantas_idLlantas,
                li.LlantasInspeccionMm1,
                li.LlantasInspeccionMm2,
                li.LlantasInspeccionMm3,
                li.LlantasInspeccionMm4,
                li.LlantasInspeccionPresion,
                li.LlantasInspeccionCondPel,
                li.LlantasInspeccionObservacion,
                li.LlantasInspeccionComentario,
                li.LlantasInspeccionDOT,
                li.LlantasInspeccionPiso,
                li.LlantasInspeccionDesgaste,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida,
                li.LlantasInspeccionFoto,
                li.LlantasInspeccionFoto2
            FROM llantasinspeccion li
            LEFT JOIN llantas l ON li.Llantas_idLlantas = l.idLlantas
            WHERE li.idLlantasInspeccion = ?
        `, [result.insertId]);

        res.status(201).json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al crear la llanta de inspección', error);
    }
});

// PUT - Actualizar llanta de inspección
router.put('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        let {
            LlantasInspeccionMm1,
            LlantasInspeccionMm2,
            LlantasInspeccionMm3,
            LlantasInspeccionMm4,
            LlantasInspeccionPresion,
            LlantasInspeccionCondPel,
            LlantasInspeccionObservacion,
            LlantasInspeccionComentario,
            LlantasInspeccionFoto,
            LlantasInspeccionFoto2,
            LlantasInspeccionDOT,
            LlantasInspeccionPiso,
            LlantasInspeccionDesgaste
        } = req.body;

        const fields = [];
        const params = [];

        const addField = (name, val) => {
            if (val !== undefined) {
                fields.push(`${name} = ?`);
                params.push(val);
            }
        };

        addField('LlantasInspeccionMm1', LlantasInspeccionMm1);
        addField('LlantasInspeccionMm2', LlantasInspeccionMm2);
        addField('LlantasInspeccionMm3', LlantasInspeccionMm3);
        addField('LlantasInspeccionMm4', LlantasInspeccionMm4);
        addField('LlantasInspeccionPresion', LlantasInspeccionPresion);
        addField('LlantasInspeccionCondPel', LlantasInspeccionCondPel);
        addField('LlantasInspeccionObservacion', LlantasInspeccionObservacion);
        addField('LlantasInspeccionComentario', LlantasInspeccionComentario);
        addField('LlantasInspeccionDOT', LlantasInspeccionDOT);
        addField('LlantasInspeccionPiso', LlantasInspeccionPiso);
        addField('LlantasInspeccionDesgaste', LlantasInspeccionDesgaste);

        if (LlantasInspeccionFoto !== null && LlantasInspeccionFoto !== undefined) {
            fields.push('LlantasInspeccionFoto = ?');
            params.push(await processImageForStorage(LlantasInspeccionFoto));
        }

        if (LlantasInspeccionFoto2 !== null && LlantasInspeccionFoto2 !== undefined) {
            fields.push('LlantasInspeccionFoto2 = ?');
            params.push(await processImageForStorage(LlantasInspeccionFoto2));
        }

        if (fields.length === 0) {
            return res.status(400).json({ message: 'No hay campos para actualizar' });
        }

        params.push(id);
        const [result] = await db.execute(`UPDATE llantasinspeccion SET ${fields.join(', ')} WHERE idLlantasInspeccion = ?`, params);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Llanta de inspección no encontrada' });
        }

        // Obtener la llanta actualizada
        const [rows] = await db.execute(`
            SELECT 
                li.idLlantasInspeccion,
                li.vehiculosinspeccion_idVehiculoInspeccion,
                li.Llantas_idLlantas,
                li.LlantasInspeccionMm1,
                li.LlantasInspeccionMm2,
                li.LlantasInspeccionMm3,
                li.LlantasInspeccionMm4,
                li.LlantasInspeccionPresion,
                li.LlantasInspeccionCondPel,
                li.LlantasInspeccionObservacion,
                li.LlantasInspeccionComentario,
                li.LlantasInspeccionDOT,
                li.LlantasInspeccionPiso,
                li.LlantasInspeccionDesgaste,
                l.LlantasMarca,
                l.LlantasModelo,
                l.LlantasMedida
            FROM llantasinspeccion li
            LEFT JOIN llantas l ON li.Llantas_idLlantas = l.idLlantas
            WHERE li.idLlantasInspeccion = ?
        `, [id]);

        res.json(rows[0]);
    } catch (error) {
        handleServerError(res, 'Error al actualizar la llanta de inspección', error);
    }
});

// DELETE - Eliminar llanta de inspección
router.delete('/:id', async (req, res) => {
    try {
        const { id } = req.params;

        const [result] = await db.execute(`
            DELETE FROM llantasinspeccion WHERE idLlantasInspeccion = ?
        `, [id]);

        if (result.affectedRows === 0) {
            return res.status(404).json({ message: 'Llanta de inspección no encontrada' });
        }

        res.json({ message: 'Llanta de inspección eliminada correctamente' });
    } catch (error) {
        handleServerError(res, 'Error al eliminar la llanta de inspección', error);
    }
});

module.exports = router;
