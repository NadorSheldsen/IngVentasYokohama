const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// Helper to convert Buffer photo fields to base64 strings so API always returns strings
function normalizeFotoFields(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  try {
    if (obj.LlantasDesechoFoto1 && Buffer.isBuffer(obj.LlantasDesechoFoto1)) {
      obj.LlantasDesechoFoto1 = obj.LlantasDesechoFoto1.toString('base64');
    }
  } catch (_) {}
  try {
    if (obj.LlantasDesechoFoto2 && Buffer.isBuffer(obj.LlantasDesechoFoto2)) {
      obj.LlantasDesechoFoto2 = obj.LlantasDesechoFoto2.toString('base64');
    }
  } catch (_) {}
  return obj;
}

// Helper to stringify large objects safely (truncate long strings)
function safeStringify(obj, maxLen = 2000) {
  try {
    return JSON.stringify(obj, (k, v) => {
      if (typeof v === 'string' && v.length > maxLen) return v.slice(0, maxLen) + `...[truncated ${v.length - maxLen} chars]`;
      return v;
    }, 2);
  } catch (e) {
    try { return String(obj); } catch (_) { return '<unstringifiable>'; }
  }
}

// GET all llantas desecho with related data
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT ld.*, pd.PruebasDesechoNombre, pd.PruebasDesechoFecha,
             l.LlantasMarca, l.LlantasModelo, l.LlantasMedida
      FROM llantasdesecho ld
      JOIN pruebasdesecho pd ON ld.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
      JOIN llantas l ON ld.Llantas_idLlantas = l.idLlantas
    `);
    // Normalize photo buffer fields to base64 strings so clients always receive strings
    const normalized = rows.map(r => normalizeFotoFields(r));
    res.json(normalized);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas de desecho', error);
  }
});

// GET a specific llanta desecho by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT ld.*, pd.PruebasDesechoNombre, pd.PruebasDesechoFecha,
            l.LlantasMarca, l.LlantasModelo, l.LlantasMedida
      FROM llantasdesecho ld
      JOIN pruebasdesecho pd ON ld.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
      JOIN llantas l ON ld.Llantas_idLlantas = l.idLlantas
      WHERE ld.idLlantasDesecho = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Llanta de desecho no encontrada' });
    }
    // Normalize photo buffer fields
    res.json(normalizeFotoFields(rows[0]));
  } catch (error) {
    handleServerError(res, 'Error al obtener la llanta de desecho', error);
  }
});

// POST a new llanta desecho
router.post('/', async (req, res) => {
  try {
    // Log incoming request body size and keys
    const bodyStr = JSON.stringify(req.body);
    console.log('[llantasDesecho POST] Incoming body size:', bodyStr.length, 'bytes');
    console.log('[llantasDesecho POST] Incoming body keys:', Object.keys(req.body));
    
    const { 
      LlantasDesechocol,
      PruebasDesecho_idPruebasDesecho,
      Llantas_idLlantas,
      LlantasDesechoNoLlanta,
      LlantasDesechoPiso,
      LlantasDesechoCausaDes,
      LlantasDesechoRemanente,
      LlantasDesechoUbi,
      Latitud,
      Longitud,
      Usuarios_idUsuarios,
      LlantasDesechoFecha,
      LlantasDesechoFoto1,
      LlantasDesechoFoto2,
      LlantasDesechoComentarios
    } = req.body || {};

    // Validar campos obligatorios
    if (!PruebasDesecho_idPruebasDesecho || !Llantas_idLlantas || !LlantasDesechoPiso) {
      console.error('[llantasDesecho POST] Missing required fields:', { 
        PruebasDesecho_idPruebasDesecho, 
        Llantas_idLlantas, 
        LlantasDesechoPiso 
      });
      return res.status(400).json({ error: 'Campos obligatorios faltando: PruebasDesecho_idPruebasDesecho, Llantas_idLlantas, LlantasDesechoPiso' });
    }
    
    const causaFinal = (LlantasDesechoCausaDes == null ? '' : String(LlantasDesechoCausaDes)).trim() || 'CORTE EN PISO';
    const creatorId = (req.body && (req.body.Usuarios_idUsuarios || req.body.UsuariosId)) || (req.user && (req.user.id || req.user.idUsuarios)) || null;
    
    // Log para debugging
    try {
      console.log('[llantasDesecho POST] causa=%o (type=%s)', LlantasDesechoCausaDes, typeof LlantasDesechoCausaDes)
      console.log('[llantasDesecho POST] values: NoLlanta=%o (type=%s), Remanente=%o (type=%s), Comentarios=%o (type=%s)',
        LlantasDesechoNoLlanta, typeof LlantasDesechoNoLlanta,
        LlantasDesechoRemanente, typeof LlantasDesechoRemanente,
        LlantasDesechoComentarios, typeof LlantasDesechoComentarios
      )
      console.log('[llantasDesecho POST] ubi=%o (type=%s), Latitud=%o (type=%s), Longitud=%o (type=%s)',
        LlantasDesechoUbi, typeof LlantasDesechoUbi,
        Latitud, typeof Latitud,
        Longitud, typeof Longitud
      )
    } catch (e) { console.log('Error logging:', e) }
    
    // ELIMINA LA VALIDACIÓN QUE RECHAZA ALFANUMÉRICOS
    // Ahora simplemente guardamos el valor como texto (VARCHAR)
    let llantaNoParam = null;
    if (LlantasDesechoNoLlanta != null && LlantasDesechoNoLlanta !== '') {
      // Guardar como string directamente, sin validación de números
      llantaNoParam = String(LlantasDesechoNoLlanta);
    }

    const [result] = await db.query(
      `INSERT INTO llantasdesecho (
        LlantasDesechocol,
        PruebasDesecho_idPruebasDesecho,
        Llantas_idLlantas,
        LlantasDesechoNoLlanta,
        LlantasDesechoPiso,
        LlantasDesechoCausaDes,
        LlantasDesechoUbi,
        Latitud,
        Longitud,
        LlantasDesechoRemanente,
        Usuarios_idUsuarios,
        LlantasDesechoFecha,
        LlantasDesechoFoto1,
        LlantasDesechoFoto2,
        LlantasDesechoComentarios
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURDATE()), ?, ?, ?)`,
      [
        LlantasDesechocol,
        PruebasDesecho_idPruebasDesecho,
        Llantas_idLlantas,
        llantaNoParam,  // Ahora puede ser texto
        LlantasDesechoPiso,
        causaFinal,
        LlantasDesechoUbi,
        Latitud,
        Longitud,
        LlantasDesechoRemanente,
        creatorId || Usuarios_idUsuarios || null,
        LlantasDesechoFecha,
        LlantasDesechoFoto1,
        LlantasDesechoFoto2,
        LlantasDesechoComentarios
      ]
    );
    
    // Resto del código igual...
    const [rows] = await db.query(`
      SELECT ld.*, pd.PruebasDesechoNombre, pd.PruebasDesechoFecha,
            l.LlantasMarca, l.LlantasModelo, l.LlantasMedida
      FROM llantasdesecho ld
      JOIN pruebasdesecho pd ON ld.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
      JOIN llantas l ON ld.Llantas_idLlantas = l.idLlantas
      WHERE ld.idLlantasDesecho = ?
    `, [result.insertId]);

    if (rows.length === 0) {
      return res.status(201).json({ message: 'Llanta desecho creada, pero no se pudo recuperar el registro', id: result.insertId });
    }

    res.status(201).json(normalizeFotoFields(rows[0]));
  } catch (error) {
    console.error('[llantasDesecho POST] ========== ERROR ==========');
    console.error('[llantasDesecho POST] Error message:', error.message);
    console.error('[llantasDesecho POST] Error code:', error.code);
    console.error('[llantasDesecho POST] Error errno:', error.errno);
    console.error('[llantasDesecho POST] Error sqlState:', error.sqlState);
    console.error('[llantasDesecho POST] Error sqlMessage:', error.sqlMessage);
    if (error.stack) {
      console.error('[llantasDesecho POST] Stack trace:');
      console.error(error.stack);
    }
    console.error('[llantasDesecho POST] ============================');
    handleServerError(res, 'Error al crear la llanta de desecho', error);
  }
});

// PUT/UPDATE a llanta desecho
router.put('/:id', async (req, res) => {
  try {
    // Debug: log incoming update body keys and a short summary to help diagnose client/server mismatch
    try {
      console.log('[llantasDesecho PUT] id=%s bodyKeys=%o', req.params.id, Object.keys(req.body))
      console.log('[llantasDesecho PUT] body (truncated): %s', safeStringify(req.body, 800))
      // Log photo field summary (type/length) if present (safe access)
      ['LlantasDesechoFoto1','LlantasDesechoFoto2'].forEach(k => {
        if (req.body && Object.prototype.hasOwnProperty.call(req.body, k)) {
          try {
            const v = req.body[k]
            if (v == null) {
              console.log(`  ${k}: null`)
            } else if (typeof v === 'string') {
              console.log(`  ${k}: string len=${v.length}`)
            } else if (Buffer && Buffer.isBuffer && Buffer.isBuffer(v)) {
              console.log(`  ${k}: Buffer len=${v.length}`)
            } else {
              console.log(`  ${k}: type=${typeof v}`)
            }
          } catch (inner) { console.log(`  ${k}: error inspecting value:`, inner.message) }
        }
      })
    } catch (e) { console.log('Error logging PUT body:', e) }

    // Build dynamic UPDATE: only touch columns that the client explicitly provided.
    // For photo fields we accept explicit null -> delete; for non-photo text fields treat null/empty as "no-change" to avoid accidental erasure.
    const body = req.body || {};

    // First ensure the target row exists
    const [existingRows] = await db.query('SELECT * FROM llantasdesecho WHERE idLlantasDesecho = ?', [req.params.id]);
    if (existingRows.length === 0) {
      return res.status(404).json({ message: 'Llanta de desecho no encontrada' });
    }

    const setParts = [];
    const params = [];

    function hasKey(k) { return Object.prototype.hasOwnProperty.call(body, k); }

    if (hasKey('LlantasDesechocol')) {
      if (body.LlantasDesechocol != null && body.LlantasDesechocol !== '') {
        setParts.push('LlantasDesechocol = ?'); params.push(body.LlantasDesechocol);
      }
    }

    if (hasKey('PruebasDesecho_idPruebasDesecho')) {
      setParts.push('PruebasDesecho_idPruebasDesecho = ?'); params.push(body.PruebasDesecho_idPruebasDesecho);
    }

    if (hasKey('Llantas_idLlantas')) {
      setParts.push('Llantas_idLlantas = ?'); params.push(body.Llantas_idLlantas);
    }

    // En la sección PUT (aproximadamente línea 140-170)

    if (hasKey('LlantasDesechoNoLlanta')) {
      const val = body.LlantasDesechoNoLlanta;
      if (val != null && val !== '') {
        // ELIMINA LA VALIDACIÓN - ahora acepta cualquier texto
        setParts.push('LlantasDesechoNoLlanta = ?'); 
        params.push(String(val)); // Convertir a string explícitamente
      } else if (val === null || val === '') {
        // Si viene null o vacío, guardar como NULL
        setParts.push('LlantasDesechoNoLlanta = ?'); 
        params.push(null);
      }
    }

    if (hasKey('LlantasDesechoPiso')) {
      if (body.LlantasDesechoPiso == null || body.LlantasDesechoPiso === '') {
        // treat empty as no-change
      } else {
        setParts.push('LlantasDesechoPiso = ?'); params.push(body.LlantasDesechoPiso);
      }
    }

    if (hasKey('LlantasDesechoCausaDes')) {
      const causaVal = (body.LlantasDesechoCausaDes == null ? '' : String(body.LlantasDesechoCausaDes)).trim();
      if (causaVal) {
        setParts.push('LlantasDesechoCausaDes = ?'); params.push(causaVal);
      } else {
        setParts.push('LlantasDesechoCausaDes = ?'); params.push('CORTE EN PISO');
      }
    }

    if (hasKey('LlantasDesechoRemanente')) {
      if (body.LlantasDesechoRemanente != null && body.LlantasDesechoRemanente !== '') {
        setParts.push('LlantasDesechoRemanente = ?'); params.push(body.LlantasDesechoRemanente);
      }
    }

    // Ubicación (zona) and GPS coordinates
    if (hasKey('LlantasDesechoUbi')) {
      if (body.LlantasDesechoUbi != null && body.LlantasDesechoUbi !== '') {
        setParts.push('LlantasDesechoUbi = ?'); params.push(body.LlantasDesechoUbi);
      }
    }

    if (hasKey('Latitud')) {
      if (body.Latitud != null && body.Latitud !== '') {
        setParts.push('Latitud = ?'); params.push(body.Latitud);
      }
    }

    if (hasKey('Longitud')) {
      if (body.Longitud != null && body.Longitud !== '') {
        setParts.push('Longitud = ?'); params.push(body.Longitud);
      }
    }

    // Photos: handle robustly like in llantasSemaforo
    // - accept explicit null -> set NULL
    // - empty string -> treat as delete
    // - base64/dataURI string -> convert to Buffer and validate size
    // - Buffer -> use as-is
    const MAX_PHOTO_BYTES = 12 * 1024 * 1024;

    function toBase64String(val) {
      if (val === null || val === undefined) return null;
      if (Buffer.isBuffer(val)) return val.toString('base64');
      if (typeof val === 'string') {
        const m = val.match(/^data:.*;base64,(.*)$/);
        return m ? m[1] : val;
      }
      return String(val);
    }

    async function handleFotoField(fieldName) {
      if (!hasKey(fieldName)) return;
      const val = body[fieldName];

      // If incoming value equals existing stored value (after normalizing to base64 strings), skip rewriting
      try {
        const existingVal = existingRows && existingRows[0] ? existingRows[0][fieldName] : undefined;
        const existingB64 = toBase64String(existingVal);
        const incomingB64 = (val === null || val === undefined) ? null : toBase64String(val);
        if (existingB64 !== null && incomingB64 !== null && existingB64 === incomingB64) {
          // identical, do not include in UPDATE
          return;
        }
      } catch (e) {
        // if normalization fails, proceed to handle normally
      }

      // include column and push param(s)
      setParts.push(`${fieldName} = ?`);

      // explicit null -> delete
      if (val === null) {
        params.push(null);
        return;
      }

      // empty string -> treat as delete
      if (typeof val === 'string' && val.trim() === '') {
        params.push(null);
        return;
      }

      // Buffer -> validate size and use
      if (Buffer.isBuffer(val)) {
        if (val.length > MAX_PHOTO_BYTES) {
          return res.status(413).json({ message: `${fieldName} demasiado grande` });
        }
        params.push(val);
        return;
      }

      // String (likely base64 or data URI) -> try to convert to Buffer
      if (typeof val === 'string') {
        const m = val.match(/^data:.*;base64,(.*)$/);
        const b64 = m ? m[1] : val;
        try {
          const buf = Buffer.from(b64, 'base64');
          if (buf.length > MAX_PHOTO_BYTES) {
            return res.status(413).json({ message: `${fieldName} demasiado grande` });
          }
          params.push(buf);
          return;
        } catch (e) {
          // If conversion fails, fall back to sending original string; DB may accept text
          params.push(val);
          return;
        }
      }

      // Any other type -> pass through
      params.push(val);
    }

    // Apply photo handlers
    await handleFotoField('LlantasDesechoFoto1');
    await handleFotoField('LlantasDesechoFoto2');

    if (hasKey('LlantasDesechoComentarios')) {
      if (body.LlantasDesechoComentarios != null && body.LlantasDesechoComentarios !== '') {
        setParts.push('LlantasDesechoComentarios = ?'); params.push(body.LlantasDesechoComentarios);
      }
    }

    if (setParts.length === 0) {
      // Nothing to update
      return res.json({ message: 'No fields to update' });
    }

    const sql = `UPDATE llantasdesecho SET ${setParts.join(', ')} WHERE idLlantasDesecho = ?`;
    params.push(req.params.id);
    const [result] = await db.query(sql, params);
    
    console.log('[llantasDesecho PUT] DB affectedRows=%d, changedRows=%d', result.affectedRows, result.changedRows)

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta de desecho no encontrada' });
    }
    
    res.json({ message: 'Llanta de desecho actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la llanta de desecho', error);
  }
});

// DELETE a llanta desecho
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM llantasdesecho WHERE idLlantasDesecho = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta de desecho no encontrada' });
    }
    
    res.json({ message: 'Llanta de desecho eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la llanta de desecho', error);
  }
});

module.exports = router;
