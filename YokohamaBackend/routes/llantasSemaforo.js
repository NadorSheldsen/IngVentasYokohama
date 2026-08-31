const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
const { processImageForStorage } = require('../utils/imageProcessor');

function toTinyIntFlag(value, defaultValue = 0) {
  if (value === undefined || value === null) return defaultValue;
  if (typeof value === 'boolean') return value ? 1 : 0;
  if (typeof value === 'number') return value !== 0 ? 1 : 0;
  if (typeof value === 'string') {
    const v = value.trim().toLowerCase();
    if (v === '' || v === 'null' || v === 'undefined') return defaultValue;
    if (v === '1' || v === 'true' || v === 'si' || v === 'sí' || v === 'yes') return 1;
    if (v === '0' || v === 'false' || v === 'no') return 0;
  }
  return defaultValue;
}

// Helper to convert Buffer photo fields to base64 strings so API always returns strings
function normalizeFotoFields(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  try {
    if (obj.LlantasSemaforoFoto1 && Buffer.isBuffer(obj.LlantasSemaforoFoto1)) {
      obj.LlantasSemaforoFoto1 = obj.LlantasSemaforoFoto1.toString('utf8');
    }
  } catch (_) {}
  try {
    if (obj.LlantasSemaforoFoto2 && Buffer.isBuffer(obj.LlantasSemaforoFoto2)) {
      obj.LlantasSemaforoFoto2 = obj.LlantasSemaforoFoto2.toString('utf8');
    }
  } catch (_) {}
  // Normalize boolean-like fields coming from MySQL tinyint(1) -> convert 0/1 to true/false
  try {
    const val = obj.LlantasSemaforoCondPel;
    if (val !== undefined && val !== null) {
      obj.LlantasSemaforoCondPel = (val === 1 || val === '1' || val === true || val === 'true');
    } else {
      obj.LlantasSemaforoCondPel = null;
    }
  } catch (_) {}
  try {
    const val = obj.LlantasSemaforoVigia;
    if (val !== undefined && val !== null) {
      obj.LlantasSemaforoVigia = (val === 1 || val === '1' || val === true || val === 'true');
    } else {
      obj.LlantasSemaforoVigia = null;
    }
  } catch (_) {}
  return obj;
}

// GET all llantas semaforo with related data
router.get('/', async (req, res) => {
  try {
    // Allow optional filtering by pruebaId (PruebasSemaforo id) or vehiculoId
    let sql = `
      SELECT
        ls.idLlantasSemaforo,
        ls.Llantas_idLlantas,
        ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo AS VehiculoSemaforo_idVehiculoSemaforo,
        ls.LlantasSemaforoPresion,
        ls.LlantasSemaforoColor,
        ls.LlantasSemaforoPiso,
        ls.LlantasSemaforoCondPel,
        ls.LlantasSemaforoVigia,
        ls.LlantasSemaforoObserv,
        ls.LlantasSemaforoComent,
        ls.LlantasSemaforoFoto1,
        ls.LlantasSemaforoFoto2,
        ps.idPruebasSemaforo AS PruebasSemaforo_idPruebasSemaforo,
        ps.PruebasSemaforoFecha,
        ps.Flotas_idFlotas,
        f.FlotasNombre,
        v.VehiculoPruebaSemaforoNo AS VehiculoSemaforoNo,
        t.TipoVehiculosNombre,
        l.LlantasMarca,
        l.LlantasModelo,
        l.LlantasMedida,
        p.ParametrosPMin,
        p.ParametrosPSug,
        p.ParametrosPMax
      FROM llantassemaforo ls
      JOIN vehiculopruebasemaforo v ON ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo = v.idVehiculoPruebaSemaforo
      JOIN pruebassemaforo ps ON v.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
      LEFT JOIN flotas f ON ps.Flotas_idFlotas = f.idFlotas
      JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
      JOIN llantas l ON ls.Llantas_idLlantas = l.idLlantas
      LEFT JOIN parametros p ON p.Flotas_idFlotas = ps.Flotas_idFlotas AND p.Llantas_idLlantas = ls.Llantas_idLlantas
    `;

    const params = [];
    const { pruebaId, vehiculoId } = req.query || {};
    if (pruebaId) {
      sql += ' WHERE ps.idPruebasSemaforo = ?';
      params.push(pruebaId);
    } else if (vehiculoId) {
      sql += ' WHERE v.idVehiculoPruebaSemaforo = ?';
      params.push(vehiculoId);
    }

    // Execute query with optional params
    const [rows] = await db.query(sql, params);
    // Normalize possible Buffer fields to base64 strings and boolean coercion
    const normalized = rows.map(r => normalizeFotoFields(r));
    res.json(normalized);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas semáforo', error);
  }
});

// GET a specific llanta semaforo by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT
        ls.idLlantasSemaforo,
        ls.Llantas_idLlantas,
        ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo AS VehiculoSemaforo_idVehiculoSemaforo,
        ls.LlantasSemaforoPresion,
        ls.LlantasSemaforoColor,
        ls.LlantasSemaforoPiso,
        ls.LlantasSemaforoCondPel,
        ls.LlantasSemaforoVigia,
        ls.LlantasSemaforoObserv,
        ls.LlantasSemaforoComent,
        ls.LlantasSemaforoFoto1,
        ls.LlantasSemaforoFoto2,
        ps.idPruebasSemaforo AS PruebasSemaforo_idPruebasSemaforo,
        ps.PruebasSemaforoFecha,
        ps.Flotas_idFlotas,
        f.FlotasNombre,
        v.VehiculoPruebaSemaforoNo AS VehiculoSemaforoNo,
        t.TipoVehiculosNombre,
        l.LlantasMarca,
        l.LlantasModelo,
        l.LlantasMedida,
        p.ParametrosPMin,
        p.ParametrosPSug,
        p.ParametrosPMax
      FROM llantassemaforo ls
      JOIN vehiculopruebasemaforo v ON ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo = v.idVehiculoPruebaSemaforo
      JOIN pruebassemaforo ps ON v.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
      LEFT JOIN flotas f ON ps.Flotas_idFlotas = f.idFlotas
      JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
      JOIN llantas l ON ls.Llantas_idLlantas = l.idLlantas
      LEFT JOIN parametros p ON p.Flotas_idFlotas = ps.Flotas_idFlotas AND p.Llantas_idLlantas = ls.Llantas_idLlantas
      WHERE ls.idLlantasSemaforo = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Llanta semáforo no encontrada' });
    }
    
    res.json(normalizeFotoFields(rows[0]));
  } catch (error) {
    handleServerError(res, 'Error al obtener la llanta semáforo', error);
  }
});

// POST a new llanta semaforo
router.post('/', async (req, res) => {
  try {
    // Debug: log incoming request body to help reproduce client-side deserialization issues
    console.log('POST /llantas-semaforo - request body:', req.body ? (typeof req.body === 'object' ? JSON.stringify(req.body).slice(0,2000) : req.body) : '(no body)');

    let { 
      Llantas_idLlantas,
      VehiculoSemaforo_idVehiculoSemaforo,
      LlantasSemaforoPresion,
      LlantasSemaforoColor,
      LlantasSemaforoVigia,
      LlantasSemaforoCondPel,
      LlantasSemaforoPiso,
      LlantasSemaforoObserv,
      LlantasSemaforoComent,
      LlantasSemaforoFoto1,
      LlantasSemaforoFoto2
    } = req.body;

    // Compat fallback if old clients send alternate keys.
    if (LlantasSemaforoVigia === undefined) {
      LlantasSemaforoVigia = req.body?.SemaforoVigia;
    }
    if (LlantasSemaforoCondPel === undefined) {
      LlantasSemaforoCondPel = req.body?.SemaforoCondPel;
    }

    const vigiaFlag = toTinyIntFlag(LlantasSemaforoVigia, 0);
    const condPelFlag = toTinyIntFlag(LlantasSemaforoCondPel, 0);

    LlantasSemaforoFoto1 = await processImageForStorage(LlantasSemaforoFoto1);
    LlantasSemaforoFoto2 = await processImageForStorage(LlantasSemaforoFoto2);

    const [result] = await db.query(
      `INSERT INTO llantassemaforo (
        Llantas_idLlantas,
        VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo,
        LlantasSemaforoPresion,
        LlantasSemaforoColor,
        LlantasSemaforoVigia,
        LlantasSemaforoCondPel,
        LlantasSemaforoPiso,
        LlantasSemaforoObserv,
        LlantasSemaforoComent,
        LlantasSemaforoFoto1,
        LlantasSemaforoFoto2
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        Llantas_idLlantas,
        VehiculoSemaforo_idVehiculoSemaforo,
        LlantasSemaforoPresion,
        LlantasSemaforoColor,
        vigiaFlag,
        condPelFlag,
        LlantasSemaforoPiso,
        LlantasSemaforoObserv,
        LlantasSemaforoComent,
        LlantasSemaforoFoto1,
        LlantasSemaforoFoto2
      ]
    );
    
    // Try to return the created llanta semaforo. If the SELECT fails or returns empty,
    // fall back to returning an object with the inserted id so the client can fetch it.
    try {
      // Return the created llanta semaforo (same pattern as other POST handlers: select from the table only)
      const [newLlanta] = await db.query(
        `SELECT
           idLlantasSemaforo,
           Llantas_idLlantas,
           VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo AS VehiculoSemaforo_idVehiculoSemaforo,
           LlantasSemaforoPresion,
           LlantasSemaforoColor,
           LlantasSemaforoCondPel,
           LlantasSemaforoVigia,
           LlantasSemaforoPiso,
           LlantasSemaforoObserv,
           LlantasSemaforoComent,
           LlantasSemaforoFoto1,
           LlantasSemaforoFoto2
         FROM llantassemaforo WHERE idLlantasSemaforo = ?`,
        [result.insertId]
      );

      if (newLlanta && newLlanta.length > 0) {
        const responseObj = normalizeFotoFields(newLlanta[0]);
        // Try to stringify for debug; if it fails, fall back to id-only response
        try {
          const asJson = JSON.stringify(responseObj);
          console.log('POST /llantas-semaforo - response JSON (first 2000 chars):', asJson.slice(0,2000));
          res.setHeader('Content-Type', 'application/json');
          return res.status(201).json(responseObj);
        } catch (stringifyError) {
          console.error('Error stringifying responseObj for /llantas-semaforo:', stringifyError);
          return res.status(201).json({ idLlantasSemaforo: result.insertId });
        }
      }
    } catch (selectError) {
      console.error('Error fetching created LlantasSemaforo:', selectError);
    }

    // Fallback: return the insertId so the client can GET the resource if needed
    return res.status(201).json({ idLlantasSemaforo: result.insertId });
  } catch (error) {
    handleServerError(res, 'Error al crear la llanta semáforo', error);
  }
});

// PUT/UPDATE a llanta semaforo
router.put('/:id', async (req, res) => {
  try {
    // Debug logging: show incoming body keys and photo field presence/types so we can
    // diagnose why deletions may not be applied.
    try {
      console.log(`PUT /llantas-semaforo/${req.params.id} - raw body (truncated 2000):`, req.body ? (typeof req.body === 'object' ? JSON.stringify(req.body).slice(0, 2000) : String(req.body).slice(0, 2000)) : '(no body)');
      console.log(`PUT /llantas-semaforo/${req.params.id} - body keys: ${Object.keys(req.body || {}).join(', ')}`);
      if (Object.prototype.hasOwnProperty.call(req.body || {}, 'LlantasSemaforoFoto1')) {
        const v = req.body.LlantasSemaforoFoto1;
        console.log('  -> LlantasSemaforoFoto1 present, type=', typeof v, Buffer.isBuffer(v) ? 'Buffer' : 'not Buffer', (typeof v === 'string' ? `len=${v.length}` : ''));
      } else {
        console.log('  -> LlantasSemaforoFoto1 NOT present');
      }
      if (Object.prototype.hasOwnProperty.call(req.body || {}, 'LlantasSemaforoFoto2')) {
        const v2 = req.body.LlantasSemaforoFoto2;
        console.log('  -> LlantasSemaforoFoto2 present, type=', typeof v2, Buffer.isBuffer(v2) ? 'Buffer' : 'not Buffer', (typeof v2 === 'string' ? `len=${v2.length}` : ''));
      } else {
        console.log('  -> LlantasSemaforoFoto2 NOT present');
      }
    } catch (logErr) {
      console.error('Error logging PUT body debug info:', logErr);
    }
    // Build dynamic UPDATE so photo fields are only included when present in body
    const {
      Llantas_idLlantas,
      VehiculoSemaforo_idVehiculoSemaforo,
      LlantasSemaforoPresion,
      LlantasSemaforoColor,
      LlantasSemaforoCondPel,
      LlantasSemaforoVigia,
      LlantasSemaforoPiso,
      LlantasSemaforoObserv,
      LlantasSemaforoComent
    } = req.body;

    const vigiaFlag = toTinyIntFlag(LlantasSemaforoVigia, 0);
    const condPelFlag = toTinyIntFlag(LlantasSemaforoCondPel, 0);

    // Extra debug for fields that user reports not updating
    console.log(`PUT /llantas-semaforo/${req.params.id} - Piso:`, LlantasSemaforoPiso, 'Observación:', LlantasSemaforoObserv);

    // Snapshot of incoming params to trace why some fields may be null/undefined
    try {
      const safePreview = (val) => {
        if (val === null) return 'null';
        if (val === undefined) return 'undefined';
        if (Buffer.isBuffer(val)) return `Buffer(len=${val.length})`;
        if (typeof val === 'string') return val.length > 120 ? `${val.slice(0, 120)}...(${val.length})` : val;
        return val;
      };
      console.log('PUT /llantas-semaforo params preview:', {
        Llantas_idLlantas: safePreview(Llantas_idLlantas),
        VehiculoSemaforo_idVehiculoSemaforo: safePreview(VehiculoSemaforo_idVehiculoSemaforo),
        LlantasSemaforoPresion: safePreview(LlantasSemaforoPresion),
        LlantasSemaforoColor: safePreview(LlantasSemaforoColor),
        LlantasSemaforoCondPel: safePreview(LlantasSemaforoCondPel),
        LlantasSemaforoPiso: safePreview(LlantasSemaforoPiso),
        LlantasSemaforoObserv: safePreview(LlantasSemaforoObserv),
        LlantasSemaforoComent: safePreview(LlantasSemaforoComent),
        foto1Present: Object.prototype.hasOwnProperty.call(req.body, 'LlantasSemaforoFoto1'),
        foto2Present: Object.prototype.hasOwnProperty.call(req.body, 'LlantasSemaforoFoto2')
      });
    } catch (snapshotErr) {
      console.error('Error snapshotting PUT params:', snapshotErr);
    }

    // Fetch existing photo values so we can avoid rewriting identical images
    const [existingRows] = await db.query(
      `SELECT LlantasSemaforoFoto1, LlantasSemaforoFoto2 FROM llantassemaforo WHERE idLlantasSemaforo = ?`,
      [req.params.id]
    );
    const existing = (existingRows && existingRows.length > 0) ? existingRows[0] : {};

    // Start with always-updated non-photo fields (assume client sends them)
    const setParts = [
      'Llantas_idLlantas = ?',
      'VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo = ?',
      'LlantasSemaforoPresion = ?',
      'LlantasSemaforoColor = ?',
      'LlantasSemaforoCondPel = ?',
      'LlantasSemaforoVigia = ?',
      'LlantasSemaforoPiso = ?',
      'LlantasSemaforoObserv = ?',
      'LlantasSemaforoComent = ?'
    ];
    const params = [
      Llantas_idLlantas,
      VehiculoSemaforo_idVehiculoSemaforo,
      LlantasSemaforoPresion,
      LlantasSemaforoColor,
      condPelFlag,
      vigiaFlag,
      LlantasSemaforoPiso,
      LlantasSemaforoObserv,
      LlantasSemaforoComent
    ];

    // Include photo fields ONLY if they are explicitly present in the request body.
    // If present, coerce values as follows:
    //  - null => NULL in DB (deletes image)
    //  - empty string ('') => treat as NULL (also deletes)
    //  - base64 or data URI string => convert to Buffer (so we store binary in BLOB columns)
    //  - Buffer => use as-is
    function toBase64String(val) {
      if (val === null || val === undefined) return null;
      if (Buffer.isBuffer(val)) return val.toString('base64');
      if (typeof val === 'string') {
        const m = val.match(/^data:.*;base64,(.*)$/);
        return m ? m[1] : val;
      }
      return String(val);
    }

    async function handleFotoField(fieldName, paramName) {
      if (!Object.prototype.hasOwnProperty.call(req.body, fieldName)) return;
      const val = req.body[fieldName];

      // If incoming value equals existing stored value (after normalizing to base64 strings), skip rewriting
      try {
        const existingVal = existing ? existing[fieldName] : undefined;
        const existingB64 = toBase64String(existingVal);
        const incomingB64 = (val === null || val === undefined) ? null : toBase64String(val);
        if (existingB64 !== null && incomingB64 !== null && existingB64 === incomingB64) {
          // identical, do not include in UPDATE
          return;
        }
      } catch (e) {
        // if normalization fails, proceed to handle normally
      }

      // Si es null, lo ignoramos para evitar que se borre la foto si el frontend la omitió
      if (val === null) {
        return;
      }

      setParts.push(`${paramName} = ?`);
      // continue below to coerce and push param
      
      // const val = req.body[fieldName];

      // empty string -> treat as delete
      if (typeof val === 'string' && val.trim() === '') {
        params.push(null);
        return;
      }

      const processed = await processImageForStorage(val);
      params.push(processed);
    }

    const maybe1 = await handleFotoField('LlantasSemaforoFoto1', 'LlantasSemaforoFoto1');
    if (maybe1 && maybe1.then) {
      // no-op but keeps static analysis happy if handleFotoField ever becomes async
    }
    const maybe2 = await handleFotoField('LlantasSemaforoFoto2', 'LlantasSemaforoFoto2');
    if (maybe2 && maybe2.then) {
      // noop
    }

    if (setParts.length === 0) {
      return res.status(400).json({ message: 'No hay campos para actualizar' });
    }

    try {
      // Log the SQL components without leaking huge photo payloads
      const printableParams = params.map(p => {
        if (Buffer.isBuffer(p)) return `Buffer(len=${p.length})`;
        if (typeof p === 'string' && p.length > 120) return `${p.slice(0, 120)}...(${p.length})`;
        return p;
      });
      console.log(`PUT /llantas-semaforo/${req.params.id} - SQL parts:`, setParts.join(' | '));
      console.log(`PUT /llantas-semaforo/${req.params.id} - Params:`, printableParams);
    } catch (logSqlErr) {
      console.error('Error logging SQL parts/params:', logSqlErr);
    }

    const sql = `UPDATE llantassemaforo SET ${setParts.join(', ')} WHERE idLlantasSemaforo = ?`;
    params.push(req.params.id);

    const [result] = await db.query(sql, params);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta semáforo no encontrada' });
    }

    // Return the updated record to ensure client sees latest values
    try {
      const [rows] = await db.query(
        `SELECT
           ls.idLlantasSemaforo,
           ls.Llantas_idLlantas,
           ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo AS VehiculoSemaforo_idVehiculoSemaforo,
           ls.LlantasSemaforoPresion,
           ls.LlantasSemaforoColor,
           ls.LlantasSemaforoPiso,
           ls.LlantasSemaforoCondPel,
           ls.LlantasSemaforoVigia,
           ls.LlantasSemaforoObserv,
           ls.LlantasSemaforoComent,
           ls.LlantasSemaforoFoto1,
           ls.LlantasSemaforoFoto2
         FROM llantassemaforo ls WHERE ls.idLlantasSemaforo = ?`,
        [req.params.id]
      );
      if (rows && rows.length > 0) {
        const updated = normalizeFotoFields(rows[0]);
        console.log('PUT /llantas-semaforo - updated row:', {
          id: updated.idLlantasSemaforo,
          Piso: updated.LlantasSemaforoPiso,
          Observacion: updated.LlantasSemaforoObserv
        });
        return res.json(updated);
      }
    } catch (selErr) {
      console.error('Error selecting updated LlantasSemaforo:', selErr);
    }

    // Fallback to message
    res.json({ message: 'Llanta semáforo actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la llanta semáforo', error);
  }
});

// DELETE a llanta semaforo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM llantassemaforo WHERE idLlantasSemaforo = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta semáforo no encontrada' });
    }
    
    res.json({ message: 'Llanta semáforo eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la llanta semáforo', error);
  }
});

module.exports = router;