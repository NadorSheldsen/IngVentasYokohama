const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
const { processImageForStorage } = require('../utils/imageProcessor');

// Helper to normalize DB row values for JSON clients
function normalizeRendimientoRow(r) {
  // Ensure boolean fields are booleans for JSON consumers (Kotlin expects boolean)
  const fotoRendimiento = Buffer.isBuffer(r.LlantasRendimientoFoto)
    ? r.LlantasRendimientoFoto.toString('base64')
    : (r.LlantasRendimientoFoto || null);

  const out = {
    idLlantasRendimiento: r.idLlantasRendimiento,
    PruebaRendimiento_idPruebaRendimiento: r.PruebaRendimiento_idPruebaRendimiento,
    LlantasVehiculos_idLlantasVehiculos: r.LlantasVehiculos_idLlantasVehiculos,
    LlantasRendimientoMm1: r.LlantasRendimientoMm1,
    LlantasRendimientoMm2: r.LlantasRendimientoMm2,
    LlantasRendimientoMm3: r.LlantasRendimientoMm3,
    LlantasRendimientoMm4: r.LlantasRendimientoMm4,
    LlantasRendimientoPresion: r.LlantasRendimientoPresion,
    LlantasRendimientoCondPel: !!r.LlantasRendimientoCondPel,
    LlantasRendimientoFoto: fotoRendimiento,
    LlantasRendimientoPTerminada: r.LlantasRendimientoPTerminada || 0,
    PruebaRendimientoOdometro: r.PruebaRendimientoOdometro == null ? null : Number(r.PruebaRendimientoOdometro),
    VehiculosOdometro: r.VehiculosOdometro == null ? null : Number(r.VehiculosOdometro),
    // Accept multiple possible field names returned by different endpoints/schemas
    kmRecorrido: (r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec) == null ? null : Number(r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec),
    KmDesdeUltimaPrueba: (r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec) == null ? null : Number(r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec),
    kmRec: (r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec) == null ? null : Number(r.kmRecorrido ?? r.KmDesdeUltimaPrueba ?? r.kmRec),
    LlantasRendimientoComent: r.LlantasRendimientoComent || null,
    LlantasRendimientoDesgaste: r.LlantasRendimientoDesgaste || null,
    LlantasRendimientoCausaRetiro: r.LlantasRendimientoCausaRetiro || null
  };
  try {
    console.log('[llantasRendimiento] normalizeRendimientoRow ->', JSON.stringify({ id: out.idLlantasRendimiento, pruebaOdo: out.PruebaRendimientoOdometro, vehOdo: out.VehiculosOdometro, kmRecorrido: out.kmRecorrido }));
  } catch (e) { /* ignore logging errors */ }
  return out;
}

// Helper to run a primary SQL and, if the DB errors because the optional
// column VehiculosOdometroRegistro doesn't exist, retry with a fallback SQL
// that uses only VehiculosOdometro. This allows the server to work against
// older DB schemas until a migration adds the column.
async function executeQueryWithRegistroFallback(primarySql, fallbackSql, params) {
  try {
    return await db.query(primarySql, params);
  } catch (err) {
    // If the error is a bad field (column not found) and mentions the
    // registration column, retry with the fallback that doesn't reference it.
    if (err && err.code === 'ER_BAD_FIELD_ERROR' && String(err.sqlMessage).includes('VehiculosOdometroRegistro')) {
      console.warn('[llantasRendimiento] Column VehiculosOdometroRegistro not found in DB, retrying query with fallback SQL');
      return await db.query(fallbackSql, params);
    }
    throw err;
  }
}

// Helper: get the last LlantasRendimiento row for a given llantaVehiculoId
async function getLastRendimientoForLlanta(llantaVehiculoId) {
  const [rows] = await db.query(
    `SELECT * FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
    [llantaVehiculoId]
  );
  return rows.length > 0 ? rows[0] : null;
}

// Helper: get the previous (last excluding a specific id) LlantasRendimiento row
async function getPreviousRendimientoForLlantaExcluding(llantaVehiculoId, excludeId) {
  const [rows] = await db.query(
    `SELECT * FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? AND idLlantasRendimiento <> ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
    [llantaVehiculoId, excludeId]
  );
  return rows.length > 0 ? rows[0] : null;
}

// Helper: get the LlantasVehiculos row for a given id (to use initial MM fallback)
async function getLlantasVehiculosById(llantaVehiculoId) {
  const [rows] = await db.query(
    `SELECT * FROM llantasvehiculos WHERE idLlantasVehiculos = ?`,
    [llantaVehiculoId]
  );
  return rows.length > 0 ? rows[0] : null;
}

// Helper: validate mm values do not exceed last recorded mm (if last exists)
function validateMmNotGreater(newVal, lastVal) {
  if (newVal == null) return true; // nothing to validate
  if (lastVal == null) return true; // no last value to compare
  // allow equality or lower values only
  return Number(newVal) <= Number(lastVal);
}

// Helper: clamp a requested MM to the allowed maximum (if provided).
function clampMmToMax(requested, maxAllowed) {
  const reqNum = requested == null ? null : Number(requested);
  const maxNum = maxAllowed == null ? null : Number(maxAllowed);
  if (reqNum == null) return null;
  if (maxNum == null) return reqNum;
  return reqNum > maxNum ? maxNum : reqNum;
}

// GET all llantas rendimiento with related data
router.get('/', async (req, res) => {
  try {
    const primarySql = `
  SELECT lr.*, pr.idPruebaRendimiento, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       -- Km = última prueba.odómetro - odómetro de registro del vehículo (fallback al odómetro actual si no existe el registro)
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(
           pr.PruebaRendimientoOdometro - COALESCE(
             (
               SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
             ),
             COALESCE(v.VehiculosOdometro, 0)
           ), 0
         )
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;
    const fallbackSql = `
  SELECT lr.*, pr.idPruebaRendimiento, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(
           pr.PruebaRendimientoOdometro - COALESCE(
             (
               SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
             ),
             v.VehiculosOdometro
           ), 0
         )
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;

    const [rows] = await executeQueryWithRegistroFallback(primarySql, fallbackSql, []);
    console.log(`[llantasRendimiento] GET / - rows=${rows.length}`);
    if (rows.length > 0) {
      const sample = rows.slice(0, 10).map(r => ({ id: r.idLlantasRendimiento, llantaVehiculoId: r.LlantasVehiculos_idLlantasVehiculos }));
      console.log('[llantasRendimiento] Sample rows:', JSON.stringify(sample));
      // Count by llantaVehiculoId to diagnose filtering
      const grouped = {};
      rows.forEach(r => {
        const key = r.LlantasVehiculos_idLlantasVehiculos;
        grouped[key] = (grouped[key] || 0) + 1;
      });
      console.log('[llantasRendimiento] Rows grouped by LlantasVehiculos_idLlantasVehiculos:', JSON.stringify(grouped));
    }
    // Normalize boolean-like fields before returning
    res.json(rows.map(normalizeRendimientoRow));
  } catch (error) {
    handleServerError(res, 'Error al obtener los rendimientos de llantas', error);
  }
});

// GET latest llantas rendimiento per LlantasVehiculos_idLlantasVehiculos
router.get('/ultimos', async (req, res) => {
  try {
    const primarySql = `
  SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(
           pr.PruebaRendimientoOdometro - COALESCE(
             (
               SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
             ),
             COALESCE(v.VehiculosOdometro, 0)
           ), 0
         )
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN (
        SELECT LlantasVehiculos_idLlantasVehiculos, MAX(idLlantasRendimiento) as maxId
        FROM llantasrendimiento
        GROUP BY LlantasVehiculos_idLlantasVehiculos
      ) m ON lr.LlantasVehiculos_idLlantasVehiculos = m.LlantasVehiculos_idLlantasVehiculos
         AND lr.idLlantasRendimiento = m.maxId
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;
    const fallbackSql = `
  SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(
           pr.PruebaRendimientoOdometro - COALESCE(
             (
               SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
             ),
             v.VehiculosOdometro
           ), 0
         )
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN (
        SELECT LlantasVehiculos_idLlantasVehiculos, MAX(idLlantasRendimiento) as maxId
        FROM llantasrendimiento
        GROUP BY LlantasVehiculos_idLlantasVehiculos
      ) m ON lr.LlantasVehiculos_idLlantasVehiculos = m.LlantasVehiculos_idLlantasVehiculos
         AND lr.idLlantasRendimiento = m.maxId
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;

    const [rows] = await executeQueryWithRegistroFallback(primarySql, fallbackSql, []);
    console.log(`[llantasRendimiento] GET /ultimos - rows=${rows.length}`);
    if (rows.length > 0) {
      const sample = rows.slice(0, 20).map(r => ({ id: r.idLlantasRendimiento, llantaVehiculoId: r.LlantasVehiculos_idLlantasVehiculos }));
      console.log('[llantasRendimiento] /ultimos sample:', JSON.stringify(sample));
    }
    res.json(rows.map(normalizeRendimientoRow));
  } catch (error) {
    handleServerError(res, 'Error al obtener los últimos rendimientos de llantas', error);
  }
});

// DIAGNÓSTICO: verificar si el campo PruebaRendimientoOdometro existe y tiene datos
router.get('/diagnosis/odometer-check', async (req, res) => {
  try {
    // Verificar si existen registros en pruebarendimiento con odómetro
    const [pruebasConOdometro] = await db.query(`
      SELECT COUNT(*) as total, 
             SUM(CASE WHEN PruebaRendimientoOdometro IS NOT NULL AND PruebaRendimientoOdometro > 0 THEN 1 ELSE 0 END) as conOdometro,
             SUM(CASE WHEN PruebaRendimientoOdometro IS NULL THEN 1 ELSE 0 END) as sinOdometro
      FROM pruebarendimiento
    `);
    
    // Obtener algunas pruebas de ejemplo
    const [ejemplos] = await db.query(`
      SELECT idPruebaRendimiento, Vehiculos_idVehiculos, PruebaRendimientoFecha, PruebaRendimientoOdometro 
      FROM pruebarendimiento 
      ORDER BY idPruebaRendimiento DESC 
      LIMIT 10
    `);
    
    console.log('[llantasRendimiento] DIAGNOSIS odometer check:', JSON.stringify(pruebasConOdometro[0]));
    
    res.json({
      diagnosis: pruebasConOdometro[0],
      ejemplos: ejemplos.map(r => ({ 
        id: r.idPruebaRendimiento, 
        vehId: r.Vehiculos_idVehiculos, 
        fecha: r.PruebaRendimientoFecha, 
        odometro: r.PruebaRendimientoOdometro 
      }))
    });
  } catch (error) {
    console.error('[llantasRendimiento] DIAGNOSIS error:', error);
    res.status(500).json({ error: error.message });
  }
});

// DEBUG: return raw rows for a given LlantasVehiculos_idLlantasVehiculos
router.get('/debug/by-llanta/:llantaVehiculoId', async (req, res) => {
  try {
    const llantaId = req.params.llantaVehiculoId;
    console.log(`[llantasRendimiento] DEBUG /by-llanta - requested id=${llantaId}`);
    
    const primarySql = `
      SELECT lr.*, pr.idPruebaRendimiento, pr.PruebaRendimientoOdometro, v.VehiculosOdometro,
             CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
               ELSE GREATEST(
                 pr.PruebaRendimientoOdometro - COALESCE(
                   (
                     SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
                     WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                       AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
                     ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
                   ),
                   COALESCE(v.VehiculosOdometro, 0)
                 ), 0
               )
             END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
      WHERE lr.LlantasVehiculos_idLlantasVehiculos = ?
      ORDER BY lr.idLlantasRendimiento DESC
      LIMIT 200
    `;
    const fallbackSql = `
      SELECT lr.*, pr.idPruebaRendimiento, pr.PruebaRendimientoOdometro, v.VehiculosOdometro,
             CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
               ELSE GREATEST(
                 pr.PruebaRendimientoOdometro - COALESCE(
                   (
                     SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
                     WHERE pr2.Vehiculos_idVehiculos = pr.Vehiculos_idVehiculos
                       AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
                     ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1
                   ),
                   v.VehiculosOdometro
                 ), 0
               )
             END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
      WHERE lr.LlantasVehiculos_idLlantasVehiculos = ?
      ORDER BY lr.idLlantasRendimiento DESC
      LIMIT 200
    `;
    const [rows] = await executeQueryWithRegistroFallback(primarySql, fallbackSql, [llantaId]);
    console.log(`[llantasRendimiento] DEBUG /by-llanta - found=${rows.length}`);
    if (rows.length > 0) console.log('[llantasRendimiento] DEBUG sample:', JSON.stringify(rows.slice(0,10).map(r => ({ id: r.idLlantasRendimiento, mm1: r.LlantasRendimientoMm1, llantaVehiculoId: r.LlantasVehiculos_idLlantasVehiculos, pruebaId: r.idPruebaRendimiento, pruebaOdo: r.PruebaRendimientoOdometro, vehOdo: r.VehiculosOdometro, kmRecorrido: r.kmRecorrido }))));
    res.json(rows.map(normalizeRendimientoRow));
  } catch (error) {
    console.error('[llantasRendimiento] DEBUG error:', error);
    handleServerError(res, 'Error al obtener rendimientos (debug) por llanta', error);
  }
});

// GET whether the last rendimiento for a llanta is marked PTerminada
router.get('/by-llanta/:llantaVehiculoId/terminada', async (req, res) => {
  try {
    const llantaId = req.params.llantaVehiculoId;
    const last = await getLastRendimientoForLlanta(llantaId);
    if (!last) {
      return res.json({ terminada: false });
    }
    const terminada = Number(last.LlantasRendimientoPTerminada) === 1;
    res.json({ terminada });
  } catch (error) {
    handleServerError(res, 'Error al consultar estado terminada de la llanta', error);
  }
});

// PUT: update the last rendimiento's PTerminada flag for a given llanta
router.put('/by-llanta/:llantaVehiculoId/terminada', async (req, res) => {
  try {
    const llantaId = req.params.llantaVehiculoId;
    const { pTerminada } = req.body;
    const flag = pTerminada ? 1 : 0;

    const last = await getLastRendimientoForLlanta(llantaId);
    if (!last) {
      return res.status(404).json({ message: 'No existe rendimiento para esta llanta para marcar terminada' });
    }

    const [result] = await db.query(`UPDATE llantasrendimiento SET LlantasRendimientoPTerminada = ? WHERE idLlantasRendimiento = ?`, [flag, last.idLlantasRendimiento]);
    if (result.affectedRows === 0) {
      return res.status(500).json({ message: 'No se pudo actualizar el registro' });
    }
    res.json({ message: 'Estado terminada actualizado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar terminada en la llanta', error);
  }
});

// DEBUG: return recent raw rows from LlantasRendimiento table (no joins)
router.get('/debug/raw', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 200;
    console.log(`[llantasRendimiento] DEBUG /raw - limit=${limit}`);
    const [rows] = await db.query(`SELECT * FROM llantasrendimiento ORDER BY idLlantasRendimiento DESC LIMIT ?`, [limit]);
    console.log(`[llantasRendimiento] DEBUG /raw - returned=${rows.length}`);
    res.json(rows.map(normalizeRendimientoRow));
  } catch (error) {
    handleServerError(res, 'Error al obtener rendimientos (raw)', error);
  }
});

// POST: accept a list of llantaVehiculoIds and return a map { llantaVehiculoId: lastRecord }
router.post('/ultimos/map', async (req, res) => {
  try {
    const ids = req.body;
    if (!Array.isArray(ids) || ids.length === 0) {
      return res.status(400).json({ message: 'Debe enviar un arreglo de ids de llantas' });
    }

    // Build placeholders for IN clause
    const placeholders = ids.map(() => '?').join(',');

    const primarySql = `
  SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(pr.PruebaRendimientoOdometro - COALESCE(v.VehiculosOdometro, 0), 0)
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN (
        SELECT LlantasVehiculos_idLlantasVehiculos, MAX(idLlantasRendimiento) as maxId
        FROM llantasrendimiento
        WHERE LlantasVehiculos_idLlantasVehiculos IN (${placeholders})
        GROUP BY LlantasVehiculos_idLlantasVehiculos
      ) m ON lr.LlantasVehiculos_idLlantasVehiculos = m.LlantasVehiculos_idLlantasVehiculos
         AND lr.idLlantasRendimiento = m.maxId
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;
    const fallbackSql = `
   SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
        l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
         ELSE GREATEST(pr.PruebaRendimientoOdometro - v.VehiculosOdometro, 0)
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN (
        SELECT LlantasVehiculos_idLlantasVehiculos, MAX(idLlantasRendimiento) as maxId
        FROM llantasrendimiento
        WHERE LlantasVehiculos_idLlantasVehiculos IN (${placeholders})
        GROUP BY LlantasVehiculos_idLlantasVehiculos
      ) m ON lr.LlantasVehiculos_idLlantasVehiculos = m.LlantasVehiculos_idLlantasVehiculos
         AND lr.idLlantasRendimiento = m.maxId
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `;

    const [rows] = await executeQueryWithRegistroFallback(primarySql, fallbackSql, ids);

  // debug logs removed

    // Build map: key -> normalized record
    const map = {};
    rows.forEach(r => {
      map[r.LlantasVehiculos_idLlantasVehiculos] = normalizeRendimientoRow(r);
    });

    // For any requested llanta id that has NO LlantasRendimiento rows, provide a sensible
    // fallback so clients that prefill forms (for example when they toggle "terminada")
    // receive vehicle odometer and the initial llantasVehiculos/mm/psi values.
    for (const id of ids) {
      if (!map[id]) {
        try {
          const [lvRows] = await db.query(`SELECT lv.*, v.VehiculosOdometro FROM llantasvehiculos lv JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos WHERE lv.idLlantasVehiculos = ?`, [id]);
          if (lvRows.length > 0) {
            const lv = lvRows[0];
            // Try to use the last recorded comment for this llanta (if any).
            let commentForLlanta = 'Ninguno';
            try {
              const [lastCommentRows] = await db.query(
                `SELECT LlantasRendimientoComent FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
                [lv.idLlantasVehiculos]
              );
              console.log('[llantasRendimiento] fallback lastCommentRows preview for llanta', lv.idLlantasVehiculos, '->', lastCommentRows && lastCommentRows[0]);
              if (lastCommentRows && lastCommentRows.length > 0 && lastCommentRows[0].LlantasRendimientoComent != null) {
                commentForLlanta = lastCommentRows[0].LlantasRendimientoComent;
              }
            } catch (e) {
              // ignore and fall back to default
            }

            const synthetic = {
              idLlantasRendimiento: null,
              LlantasVehiculos_idLlantasVehiculos: lv.idLlantasVehiculos,
              LlantasRendimientoMm1: lv.LlantasVehiculosMM1,
              LlantasRendimientoMm2: lv.LlantasVehiculosMM2,
              LlantasRendimientoMm3: lv.LlantasVehiculosMM3,
              LlantasRendimientoMm4: lv.LlantasVehiculosMM4,
              LlantasRendimientoPresion: lv.LlantasVehiculosPresion,
              LlantasRendimientoCondPel: false,
              PruebaRendimientoOdometro: null,
              kmRecorrido: lv.VehiculosOdometro,
              LlantasRendimientoComent: commentForLlanta,
              LlantasRendimientoDesgaste: null
            };
            map[id] = normalizeRendimientoRow(synthetic);
          }
        } catch (e) {
          console.warn('[llantasRendimiento] Could not build fallback for llanta', id, e.message);
        }
      }
    }

    res.json(map);
  } catch (error) {
    handleServerError(res, 'Error al obtener el mapa de últimos rendimientos', error);
  }
});

// POST: accept a list of llantaVehiculoIds and return an ARRAY of last records (useful for clients that prefer lists)
router.post('/ultimos/list', async (req, res) => {
  try {
    const ids = req.body;
    if (!Array.isArray(ids) || ids.length === 0) {
      return res.status(400).json({ message: 'Debe enviar un arreglo de ids de llantas' });
    }

    const placeholders = ids.map(() => '?').join(',');

    const [rows] = await db.query(`
  SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
       l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero, v.VehiculosOdometro,
       CASE WHEN pr.PruebaRendimientoOdometro IS NULL THEN 0
        ELSE GREATEST(pr.PruebaRendimientoOdometro - COALESCE(v.VehiculosOdometro, 0), 0)
       END AS kmRecorrido
      FROM llantasrendimiento lr
      JOIN (
        SELECT LlantasVehiculos_idLlantasVehiculos, MAX(idLlantasRendimiento) as maxId
        FROM llantasrendimiento
        WHERE LlantasVehiculos_idLlantasVehiculos IN (${placeholders})
        GROUP BY LlantasVehiculos_idLlantasVehiculos
      ) m ON lr.LlantasVehiculos_idLlantasVehiculos = m.LlantasVehiculos_idLlantasVehiculos
         AND lr.idLlantasRendimiento = m.maxId
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `, ids);

    // Debug: log incoming ids and a sample of returned rows (mm1/presion) to help diagnose client fallbacks
    try {
      console.log(`[llantasRendimiento] /ultimos/list -> requested ids=${JSON.stringify(ids)}`);
      const sample = rows.slice(0, 20).map(r => ({ id: r.idLlantasRendimiento, llantaVehiculoId: r.LlantasVehiculos_idLlantasVehiculos, mm1: r.LlantasRendimientoMm1, presion: r.LlantasRendimientoPresion }));
      console.log('[llantasRendimiento] /ultimos/list sample rows:', JSON.stringify(sample));
    } catch (e) {
      console.log('[llantasRendimiento] /ultimos/list logging error:', e.message);
    }

    // For any requested id that had no LlantasRendimiento row returned, add a synthetic
    // fallback row populated from LlantasVehiculos and Vehiculos so clients get sensible
    // defaults (VehiculosOdometro for Km, LlantasVehiculosMM1 for mm, LlantasVehiculosPresion for psi).
    const returnedIds = new Set(rows.map(r => r.LlantasVehiculos_idLlantasVehiculos));
    for (const id of ids) {
      if (!returnedIds.has(id)) {
        try {
          const [lvRows] = await db.query(`SELECT lv.*, v.VehiculosOdometro FROM llantasvehiculos lv JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos WHERE lv.idLlantasVehiculos = ?`, [id]);
          if (lvRows.length > 0) {
            const lv = lvRows[0];
            // Prefer the last recorded comment for this llanta if available
            let commentForLlanta = 'Ninguno';
            try {
              const [lastCommentRows] = await db.query(
                `SELECT LlantasRendimientoComent FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
                [lv.idLlantasVehiculos]
              );
              console.log('[llantasRendimiento] fallback lastCommentRows preview for llanta', lv.idLlantasVehiculos, '->', lastCommentRows && lastCommentRows[0]);
              if (lastCommentRows && lastCommentRows.length > 0 && lastCommentRows[0].LlantasRendimientoComent != null) {
                commentForLlanta = lastCommentRows[0].LlantasRendimientoComent;
              }
            } catch (e) {
              // ignore
            }

            rows.push({
              idLlantasRendimiento: null,
              LlantasVehiculos_idLlantasVehiculos: lv.idLlantasVehiculos,
              LlantasRendimientoMm1: lv.LlantasVehiculosMM1,
              LlantasRendimientoMm2: lv.LlantasVehiculosMM2,
              LlantasRendimientoMm3: lv.LlantasVehiculosMM3,
              LlantasRendimientoMm4: lv.LlantasVehiculosMM4,
              LlantasRendimientoPresion: lv.LlantasVehiculosPresion,
              LlantasRendimientoCondPel: false,
              PruebaRendimientoOdometro: null,
              kmRecorrido: lv.VehiculosOdometro,
              LlantasRendimientoComent: commentForLlanta,
              LlantasRendimientoDesgaste: null
            });
          }
        } catch (e) {
          console.warn('[llantasRendimiento] Could not build fallback row for llanta', id, e.message);
        }
      }
    }

    res.json(rows.map(normalizeRendimientoRow));
  } catch (error) {
    handleServerError(res, 'Error al obtener la lista de últimos rendimientos', error);
  }
});

// GET a specific llanta rendimiento by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
  SELECT lr.*, pr.PruebaRendimientoFecha, pr.PruebaRendimientoOdometro, lv.LlantasVehiculosNoQuemado,
             l.LlantasMarca, l.LlantasModelo, v.VehiculosNumero
      FROM llantasrendimiento lr
      JOIN pruebarendimiento pr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
      WHERE lr.idLlantasRendimiento = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Rendimiento de llanta no encontrado' });
    }
    
    res.json(normalizeRendimientoRow(rows[0]));
  } catch (error) {
    handleServerError(res, 'Error al obtener el rendimiento de la llanta', error);
  }
});

// POST a new llanta rendimiento
router.post('/', async (req, res) => {
  console.log('Received single llanta rendimiento request');
  console.log('Request body:', JSON.stringify(req.body, null, 2)); // Depuración
  try {
    let { 
      PruebaRendimiento_idPruebaRendimiento,
      LlantasVehiculos_idLlantasVehiculos,
      LlantasRendimientoMm1,
      LlantasRendimientoMm2,
      LlantasRendimientoMm3,
      LlantasRendimientoMm4,
      LlantasRendimientoPresion,
      LlantasRendimientoCondPel,
      LlantasRendimientoFoto,
      LlantasRendimientoPTerminada,
      LlantasRendimientoComent,
      LlantasRendimientoDesgaste,
      LlantasRendimientoCausaRetiro
    } = req.body;

    LlantasRendimientoFoto = await processImageForStorage(LlantasRendimientoFoto);
    // Server-side validation: don't allow MM higher than last recorded MM for this llanta.
      // If no previous LlantasRendimiento exists, fall back to the LlantasVehiculos initial MM values.
      let last = await getLastRendimientoForLlanta(LlantasVehiculos_idLlantasVehiculos);
      // If the last rendimiento is marked terminada, block creation
      if (last && Number(last.LlantasRendimientoPTerminada) === 1) {
        return res.status(400).json({ message: 'La llanta está marcada como terminada y no se permiten nuevos registros' });
      }
      if (!last) {
        const lv = await getLlantasVehiculosById(LlantasVehiculos_idLlantasVehiculos);
        if (lv) {
          last = {
            LlantasRendimientoMm1: lv.LlantasVehiculosMM1,
            LlantasRendimientoMm2: lv.LlantasVehiculosMM2,
            LlantasRendimientoMm3: lv.LlantasVehiculosMM3,
            LlantasRendimientoMm4: lv.LlantasVehiculosMM4
          };
        }
      }
      // Clamp values to the maximum allowed (last recorded or initial llantasVehiculos)
      const mm1ToInsert = clampMmToMax(LlantasRendimientoMm1, last ? last.LlantasRendimientoMm1 : null) ?? 0;
      const mm2ToInsert = clampMmToMax(LlantasRendimientoMm2, last ? last.LlantasRendimientoMm2 : null) ?? 0;
      const mm3ToInsert = clampMmToMax(LlantasRendimientoMm3, last ? last.LlantasRendimientoMm3 : null) ?? 0;
      const mm4ToInsert = clampMmToMax(LlantasRendimientoMm4, last ? last.LlantasRendimientoMm4 : null) ?? 0;
    
    const pTerminadaFlag = Number(LlantasRendimientoPTerminada) === 1 ? 1 : 0;
    const [result] = await db.query(
      `INSERT INTO llantasrendimiento (
        PruebaRendimiento_idPruebaRendimiento,
        LlantasVehiculos_idLlantasVehiculos,
        LlantasRendimientoMm1,
        LlantasRendimientoMm2,
        LlantasRendimientoMm3,
        LlantasRendimientoMm4,
        LlantasRendimientoPresion,
        LlantasRendimientoCondPel,
        LlantasRendimientoFoto,
        LlantasRendimientoComent,
        LlantasRendimientoDesgaste,
        LlantasRendimientoPTerminada,
        LlantasRendimientoCausaRetiro
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        PruebaRendimiento_idPruebaRendimiento,
        LlantasVehiculos_idLlantasVehiculos,
        mm1ToInsert,
        mm2ToInsert,
        mm3ToInsert,
        mm4ToInsert,
        LlantasRendimientoPresion,
        LlantasRendimientoCondPel,
        LlantasRendimientoFoto,
        LlantasRendimientoComent || null,
        LlantasRendimientoDesgaste || null,
        pTerminadaFlag,
        LlantasRendimientoCausaRetiro || null
      ]
    );
    
    res.status(201).json({ 
      message: 'Rendimiento de llanta creado correctamente', 
      id: result.insertId 
    });
    console.log('Llanta rendimiento insert result:', result); // Depuración
  } catch (error) {
    handleServerError(res, 'Error al crear el rendimiento de la llanta', error);
  }
});

router.post('/batch', async (req, res) => {
  console.log('Received batch llantas rendimiento request');
  console.log('Request body:', JSON.stringify(req.body, null, 2)); // Depuración

  const llantas = req.body; 
  if (!Array.isArray(llantas) || llantas.length === 0) {
    console.log('Invalid request: not an array or empty');
    return res.status(400).json({ message: 'Debe enviar un arreglo de llantas rendimiento' });
  }
  
  const results = [];
  const errors = [];
  
  // Use transaction for batch operations
  const connection = await db.getConnection();
  
  try {
    await connection.beginTransaction();
    console.log('Transaction started'); // Depuración

    for (let i = 0; i < llantas.length; i++) {
      const data = llantas[i];
      console.log(`Processing llanta ${i + 1}:`, data); // Depuración
      
        try {
        // Validate required fields
        if (!data.PruebaRendimiento_idPruebaRendimiento || 
            !data.LlantasVehiculos_idLlantasVehiculos) {
          throw new Error(`Missing required fields for llanta ${i + 1}`);
        }
          // If the last record of this llanta is marked as terminada, skip creating a new one
          const lastQueryResTerm = await connection.query(
            `SELECT LlantasRendimientoPTerminada FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
            [data.LlantasVehiculos_idLlantasVehiculos]
          );
          const lastTermRow = lastQueryResTerm[0].length > 0 ? lastQueryResTerm[0][0] : null;
          if (lastTermRow && Number(lastTermRow.LlantasRendimientoPTerminada) === 1) {
            console.log(`Skipping llanta ${i + 1} because it's marked terminada`);
            results.push({ index: i + 1, skipped: true, llantaVehiculoId: data.LlantasVehiculos_idLlantasVehiculos });
            continue; // skip to next
          }

          // Clamp each mm to the allowed maximum (last recorded or initial LlantasVehiculos)
          const lastQueryRes = await connection.query(
            `SELECT * FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento DESC LIMIT 1`,
            [data.LlantasVehiculos_idLlantasVehiculos]
          );
          let lastRow = lastQueryRes[0].length > 0 ? lastQueryRes[0][0] : null;
          if (!lastRow) {
            const lvRes = await connection.query(`SELECT * FROM llantasvehiculos WHERE idLlantasVehiculos = ?`, [data.LlantasVehiculos_idLlantasVehiculos]);
            if (lvRes[0].length > 0) {
              const lv = lvRes[0][0];
              lastRow = {
                LlantasRendimientoMm1: lv.LlantasVehiculosMM1,
                LlantasRendimientoMm2: lv.LlantasVehiculosMM2,
                LlantasRendimientoMm3: lv.LlantasVehiculosMM3,
                LlantasRendimientoMm4: lv.LlantasVehiculosMM4
              };
            }
          }

          const mm1 = clampMmToMax(data.LlantasRendimientoMm1, lastRow ? lastRow.LlantasRendimientoMm1 : null) ?? 0;
          const mm2 = clampMmToMax(data.LlantasRendimientoMm2, lastRow ? lastRow.LlantasRendimientoMm2 : null) ?? 0;
          const mm3 = clampMmToMax(data.LlantasRendimientoMm3, lastRow ? lastRow.LlantasRendimientoMm3 : null) ?? 0;
          const mm4 = clampMmToMax(data.LlantasRendimientoMm4, lastRow ? lastRow.LlantasRendimientoMm4 : null) ?? 0;
        
        const pTerminadaFlag = Number(data.LlantasRendimientoPTerminada) === 1 ? 1 : 0;
        const [result] = await connection.query(
          `INSERT INTO llantasrendimiento (
            PruebaRendimiento_idPruebaRendimiento,
            LlantasVehiculos_idLlantasVehiculos,
            LlantasRendimientoMm1,
            LlantasRendimientoMm2,
            LlantasRendimientoMm3,
            LlantasRendimientoMm4,
            LlantasRendimientoPresion,
            LlantasRendimientoCondPel,
            LlantasRendimientoFoto,
            LlantasRendimientoComent,
            LlantasRendimientoDesgaste,
            LlantasRendimientoPTerminada,
            LlantasRendimientoCausaRetiro
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            data.PruebaRendimiento_idPruebaRendimiento,
            data.LlantasVehiculos_idLlantasVehiculos,
            mm1,
            mm2,
            mm3,
            mm4,
            data.LlantasRendimientoPresion || 0,
            data.LlantasRendimientoCondPel || false,
            await processImageForStorage(data.LlantasRendimientoFoto),
            data.LlantasRendimientoComent || null,
            data.LlantasRendimientoDesgaste || null,
            pTerminadaFlag,
            data.LlantasRendimientoCausaRetiro || null
          ]
        );
        
        results.push({ 
          index: i + 1,
          id: result.insertId,
          llantaVehiculoId: data.LlantasVehiculos_idLlantasVehiculos
        });
        console.log(`Llanta ${i + 1} saved successfully with ID: ${result.insertId}`); // Depuración
        
      } catch (error) {
        console.error(`Error processing llanta ${i + 1}:`, error);
        errors.push({ 
          index: i + 1,
          error: error.message, 
          data: data
        });
      }
    }
    
    if (errors.length > 0) {
      await connection.rollback();
      console.log('Transaction rolled back due to errors:', errors);
      return res.status(400).json({
        message: `Errores encontrados en ${errors.length} de ${llantas.length} llantas`,
        errors: errors,
        success: results.length
      });
    }
    
    await connection.commit();
    console.log(`All ${results.length} llantas rendimiento saved successfully`); // Depuración

      // After successful commit, update VehiculosPTerminada for affected vehicles based on the
      // incoming batch: if any of the provided items for a vehicle had LlantasRendimientoPTerminada=1
      // then mark the vehicle as terminada; otherwise clear the flag.
      try {
        // Build a map vehiculoId -> hasTerminada based on provided data
        const vehiculoMap = {}; // vehiculoId -> boolean
        for (let i = 0; i < llantas.length; i++) {
          const d = llantas[i];
          // We'll query LlantasVehiculos to find the associated vehicle
          try {
            const [lvRows] = await db.query(`SELECT Vehiculos_idVehiculos FROM llantasvehiculos WHERE idLlantasVehiculos = ?`, [d.LlantasVehiculos_idLlantasVehiculos]);
            if (lvRows.length === 0) continue;
            const vehId = lvRows[0].Vehiculos_idVehiculos;
            const hasTerm = Number(d.LlantasRendimientoPTerminada) === 1;
            if (!vehiculoMap[vehId]) vehiculoMap[vehId] = hasTerm;
            else vehiculoMap[vehId] = vehiculoMap[vehId] || hasTerm;
          } catch (e) {
            console.warn('[llantasRendimiento] Could not resolve vehiculo for llanta', d.LlantasVehiculos_idLlantasVehiculos, e.message);
          }
        }

        // Apply updates for each vehicle found.
        // Instead of trusting only the incoming payload, query the DB to determine
        // whether the vehicle currently has any llanta whose rendimiento is marked PTerminada=1.
        for (const vehIdStr of Object.keys(vehiculoMap)) {
          const vehId = Number(vehIdStr);
          try {
            // Count any LlantasRendimiento rows linked to this vehicle with PTerminada=1
            const sql = `
              SELECT COUNT(*) as cnt
              FROM llantasrendimiento lr
              JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
              WHERE lv.Vehiculos_idVehiculos = ? AND lr.LlantasRendimientoPTerminada = 1
            `;
            const [countRows] = await db.query(sql, [vehId]);
            const cnt = (countRows && countRows[0] && Number(countRows[0].cnt)) || 0;
            const setFlag = cnt > 0 ? 1 : 0;
            await db.query(`UPDATE vehiculos SET VehiculosPTerminada = ? WHERE idVehiculos = ?`, [setFlag, vehId]);
            console.log(`[llantasRendimiento] Updated VehiculosPTerminada for veh=${vehId} -> ${setFlag} (count=${cnt})`);
          } catch (e) {
            console.warn(`[llantasRendimiento] Failed to update VehiculosPTerminada for veh=${vehId}: ${e.message}`);
          }
        }
      } catch (e) {
        console.warn('[llantasRendimiento] Error while updating VehiculosPTerminada after batch:', e.message);
      }

    res.status(201).json({
      message: `Todas las ${results.length} llantas rendimiento guardadas exitosamente`,
      results: results,
      total: results.length
    });
  } catch (error) {
    await connection.rollback();
    handleServerError(res, 'Error en transacción batch', error);
  } finally {
    connection.release();
    console.log('Connection released'); // Depuración
  }
});

// PUT/UPDATE a llanta rendimiento
router.put('/:id', async (req, res) => {
  try {
    let { 
      PruebaRendimiento_idPruebaRendimiento,
      LlantasVehiculos_idLlantasVehiculos,
      LlantasRendimientoMm1,
      LlantasRendimientoMm2,
      LlantasRendimientoMm3,
      LlantasRendimientoMm4,
      LlantasRendimientoPresion,
      LlantasRendimientoCondPel,
      LlantasRendimientoFoto,
      LlantasRendimientoComent,
      LlantasRendimientoDesgaste
    } = req.body;
    LlantasRendimientoFoto = await processImageForStorage(LlantasRendimientoFoto);
    // Validate mm values against the previous recorded MM for this llanta (exclude current id if present).
    let prev = await getPreviousRendimientoForLlantaExcluding(LlantasVehiculos_idLlantasVehiculos, req.params.id);
    if (!prev) {
      // fallback to initial LlantasVehiculos values
      const lv = await getLlantasVehiculosById(LlantasVehiculos_idLlantasVehiculos);
      if (lv) {
        prev = {
          LlantasRendimientoMm1: lv.LlantasVehiculosMM1,
          LlantasRendimientoMm2: lv.LlantasVehiculosMM2,
          LlantasRendimientoMm3: lv.LlantasVehiculosMM3,
          LlantasRendimientoMm4: lv.LlantasVehiculosMM4
        };
      }
    }
    // Clamp incoming values to the previous allowed maximum
    const mm1ToUpdate = clampMmToMax(LlantasRendimientoMm1, prev ? prev.LlantasRendimientoMm1 : null) ?? 0;
    const mm2ToUpdate = clampMmToMax(LlantasRendimientoMm2, prev ? prev.LlantasRendimientoMm2 : null) ?? 0;
    const mm3ToUpdate = clampMmToMax(LlantasRendimientoMm3, prev ? prev.LlantasRendimientoMm3 : null) ?? 0;
    const mm4ToUpdate = clampMmToMax(LlantasRendimientoMm4, prev ? prev.LlantasRendimientoMm4 : null) ?? 0;
    
    const [result] = await db.query(
      `UPDATE llantasrendimiento SET 
        PruebaRendimiento_idPruebaRendimiento = ?,
        LlantasVehiculos_idLlantasVehiculos = ?,
        LlantasRendimientoMm1 = ?,
        LlantasRendimientoMm2 = ?,
        LlantasRendimientoMm3 = ?,
        LlantasRendimientoMm4 = ?,
        LlantasRendimientoPresion = ?,
        LlantasRendimientoCondPel = ?,
        LlantasRendimientoFoto = ?,
        LlantasRendimientoComent = ?,
        LlantasRendimientoDesgaste = ?
      WHERE idLlantasRendimiento = ?`,
      [
        PruebaRendimiento_idPruebaRendimiento,
        LlantasVehiculos_idLlantasVehiculos,
        mm1ToUpdate,
        mm2ToUpdate,
        mm3ToUpdate,
        mm4ToUpdate,
        LlantasRendimientoPresion,
        LlantasRendimientoCondPel,
        LlantasRendimientoFoto,
        LlantasRendimientoComent || null,
        LlantasRendimientoDesgaste || null,
        req.params.id
      ]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Rendimiento de llanta no encontrado' });
    }
    
    res.json({ message: 'Rendimiento de llanta actualizado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar el rendimiento de la llanta', error);
  }
});

// DELETE a llanta rendimiento
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM llantasrendimiento WHERE idLlantasRendimiento = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Rendimiento de llanta no encontrado' });
    }
    
    res.json({ message: 'Rendimiento de llanta eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el rendimiento de la llanta', error);
  }
});

// GET: list of vehicle IDs that have at least one installed llanta whose LAST rendimiento is marked PTerminada=1
// This uses an optimized SQL that picks the last rendimiento per llanta and then finds distinct vehicles.
router.get('/vehiculos-terminadas', async (req, res) => {
  try {
    // Consider a vehicle "terminada" if ANY llantasrendimiento row for its installed llantas
    // has LlantasRendimientoPTerminada = 1. This is intentionally less strict than checking
    // only the last record per llanta to match user requirements.
    const sql = `
      SELECT DISTINCT v.idVehiculos
      FROM vehiculos v
      JOIN llantasvehiculos lv ON v.idVehiculos = lv.Vehiculos_idVehiculos
      JOIN llantasrendimiento lr ON lv.idLlantasVehiculos = lr.LlantasVehiculos_idLlantasVehiculos
      WHERE lr.LlantasRendimientoPTerminada = 1
    `;

    const [rows] = await db.query(sql);
    const ids = rows.map(r => r.idVehiculos);
    console.log(`[llantasRendimiento] GET /vehiculos-terminadas -> found ${ids.length} vehiculos`);
    res.json(ids);
  } catch (error) {
    handleServerError(res, 'Error al obtener vehículos con llantas terminadas', error);
  }
});

// DEBUG: more verbose diagnostic endpoint to help troubleshooting environments
// Returns count, distinct vehicle ids and a small sample of matching LlantasRendimiento rows
router.get('/vehiculos-terminadas/debug', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT lr.idLlantasRendimiento, lr.LlantasRendimientoPTerminada, lv.idLlantasVehiculos, lv.Vehiculos_idVehiculos, v.VehiculosNumero,
             lr.LlantasRendimientoMm1, lr.LlantasRendimientoMm2, lr.LlantasRendimientoMm3, lr.LlantasRendimientoMm4,
             lr.PruebaRendimiento_idPruebaRendimiento
      FROM llantasrendimiento lr
      JOIN llantasvehiculos lv ON lr.LlantasVehiculos_idLlantasVehiculos = lv.idLlantasVehiculos
      JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
      WHERE lr.LlantasRendimientoPTerminada = 1
      LIMIT 500
    `);

    const vehicleIds = Array.from(new Set(rows.map(r => r.Vehiculos_idVehiculos)));
    console.log(`[llantasRendimiento] DEBUG /vehiculos-terminadas/debug -> foundRows=${rows.length}, distinctVehicleIds=${vehicleIds.length}`);

    res.json({
      count: rows.length,
      vehicleIds: vehicleIds,
      sampleRows: rows.slice(0, 50)
    });
  } catch (error) {
    handleServerError(res, 'Error en debug vehiculos-terminadas', error);
  }
});

// DEBUG: Echo headers/body so we can inspect what IIS/Plesk forwards to Node
router.post('/debug/echo-headers', async (req, res) => {
  try {
    // Log a concise preview to the server console for quick inspection
    try {
      console.log('[llantasRendimiento] DEBUG /debug/echo-headers - headers:', JSON.stringify(req.headers));
    } catch (e) {
      console.log('[llantasRendimiento] DEBUG /debug/echo-headers - headers logging error', e && e.message);
    }

    try {
      console.log('[llantasRendimiento] DEBUG /debug/echo-headers - req.get("host") ->', req.get('host'));
    } catch (e) {}

    // Body may be large; return a preview in logs and full parsed body in JSON response
    let bodyPreview = null;
    try {
      bodyPreview = typeof req.body === 'string' ? req.body.substring(0, 4000) : JSON.stringify(req.body).substring(0, 4000);
      console.log('[llantasRendimiento] DEBUG /debug/echo-headers - body preview:', bodyPreview);
    } catch (e) {
      console.log('[llantasRendimiento] DEBUG /debug/echo-headers - body preview failed', e && e.message);
    }

    res.json({
      message: 'echo',
      hostHeader: req.get('host'),
      headers: req.headers,
      body: req.body
    });
  } catch (error) {
    handleServerError(res, 'Error en debug echo-headers', error);
  }
});

module.exports = router;