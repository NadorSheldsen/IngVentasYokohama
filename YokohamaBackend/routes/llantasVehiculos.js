const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

// GET all llantas vehiculos with related data
router.get('/', async (req, res) => {
  try {
    const [rows] = await db.query(`
            SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio,
              v.VehiculosNumero
            FROM llantasvehiculos lv
            JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
            JOIN vehiculos v ON lv.Vehiculos_idVehiculos = v.idVehiculos
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas de vehículo', error);
  }
});

// GET llantas vehiculos by vehiculo ID
router.get('/vehiculo/:vehiculoId', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio
      FROM llantasvehiculos lv
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      WHERE lv.Vehiculos_idVehiculos = ?
      ORDER BY
        CASE
          WHEN lv.LlantasVehiculosPiso REGEXP '^[Pp]os[[:space:]]+[0-9]+$'
            THEN CAST(SUBSTRING_INDEX(lv.LlantasVehiculosPiso, ' ', -1) AS UNSIGNED)
          ELSE 9999
        END,
        lv.LlantasVehiculosPiso,
        lv.idLlantasVehiculos
    `, [req.params.vehiculoId]);
    
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas del vehículo', error);
  }
});

// POST a new llanta vehiculo
router.post('/', async (req, res) => {
  console.log('Received single llanta vehiculo request');
  try {
    const { 
      Llantas_idLlantas,
      Vehiculos_idVehiculos,
      LlantasVehiculosNoQuemado,
      LlantasVehiculosPresion,
      LlantasVehiculosPrecio,
      LlantasVehiculosPiso,
      LlantasVehiculosFechaInicio,
      LlantasVehiculosMM1,
      LlantasVehiculosMM2,
      LlantasVehiculosMM3,
      LlantasVehiculosMM4
    } = req.body;

    // If client didn't provide fecha, use server time
    const fechaInicio = LlantasVehiculosFechaInicio || new Date();

    const replaceId = req.body.replaceId ? Number(req.body.replaceId) : null;
    let effectivePiso = LlantasVehiculosPiso;

    if (replaceId) {
      const [existingRows] = await db.query(
        'SELECT idLlantasVehiculos FROM llantasvehiculos WHERE idLlantasVehiculos = ?',
        [replaceId]
      );

      if (!existingRows || existingRows.length === 0) {
        const [terminatedRows] = await db.query(
          `SELECT LlantasVehiculosPiso, Snapshot
           FROM llantas_terminadas
           WHERE LlantasVehiculos_idLlantasVehiculos = ?
           ORDER BY id DESC
           LIMIT 1`,
          [replaceId]
        );
        const terminatedRow = terminatedRows && terminatedRows[0] ? terminatedRows[0] : null;
        if (terminatedRow) {
          effectivePiso = terminatedRow.LlantasVehiculosPiso || effectivePiso;
          if (!effectivePiso && terminatedRow.Snapshot) {
            try {
              const snapshot = typeof terminatedRow.Snapshot === 'string'
                ? JSON.parse(terminatedRow.Snapshot)
                : terminatedRow.Snapshot;
              effectivePiso = snapshot?.LlantasVehiculosPiso || effectivePiso;
            } catch (_) {
              // keep the client-provided value if the snapshot cannot be parsed
            }
          }
        }
      }

      if (existingRows && existingRows.length > 0) {
        const [updateResult] = await db.query(
          `UPDATE llantasvehiculos SET
            Llantas_idLlantas = ?,
            Vehiculos_idVehiculos = ?,
            LlantasVehiculosNoQuemado = ?,
            LlantasVehiculosPresion = ?,
            LlantasVehiculosPrecio = ?,
            LlantasVehiculosPiso = ?,
            LlantasVehiculosFechaInicio = ?,
            LlantasVehiculosMM1 = ?,
            LlantasVehiculosMM2 = ?,
            LlantasVehiculosMM3 = ?,
            LlantasVehiculosMM4 = ?
          WHERE idLlantasVehiculos = ?`,
          [
            Llantas_idLlantas,
            Vehiculos_idVehiculos,
            LlantasVehiculosNoQuemado,
            LlantasVehiculosPresion,
            LlantasVehiculosPrecio,
            LlantasVehiculosPiso,
            fechaInicio,
            LlantasVehiculosMM1,
            LlantasVehiculosMM2,
            LlantasVehiculosMM3,
            LlantasVehiculosMM4,
            replaceId
          ]
        );

        const [updatedRecord] = await db.query(
          `SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio
           FROM llantasvehiculos lv
           JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
           WHERE lv.idLlantasVehiculos = ?`,
          [replaceId]
        );
        if (updatedRecord && updatedRecord[0]) {
          return res.status(200).json(updatedRecord[0]);
        }
      }
    }

    {
      const [result] = await db.query(
        `INSERT INTO llantasvehiculos (
          Llantas_idLlantas,
          Vehiculos_idVehiculos,
          LlantasVehiculosNoQuemado,
          LlantasVehiculosPresion,
          LlantasVehiculosPrecio,
          LlantasVehiculosPiso,
          LlantasVehiculosFechaInicio,
          LlantasVehiculosMM1,
          LlantasVehiculosMM2,
          LlantasVehiculosMM3,
          LlantasVehiculosMM4
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          Llantas_idLlantas,
          Vehiculos_idVehiculos,
          LlantasVehiculosNoQuemado,
          LlantasVehiculosPresion,
          LlantasVehiculosPrecio,
          effectivePiso,
          fechaInicio,
          LlantasVehiculosMM1,
          LlantasVehiculosMM2,
          LlantasVehiculosMM3,
          LlantasVehiculosMM4
        ]
      );

      // Fetch the created record to return complete object
      const [createdRecord] = await db.query(
        `SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio
         FROM llantasvehiculos lv
         JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
         WHERE lv.idLlantasVehiculos = ?`,
        [result.insertId]
      );
      if (createdRecord && createdRecord[0]) {
        return res.status(201).json(createdRecord[0]);
      }
      // Attempt to retrieve row and llantas separately
      try {
        const [lvOnlyRows] = await db.query('SELECT * FROM llantasvehiculos WHERE idLlantasVehiculos = ?', [result.insertId]);
        const lvOnly = lvOnlyRows && lvOnlyRows[0] ? lvOnlyRows[0] : {};
        let lRow = null;
        if (Llantas_idLlantas) {
          const [lRows] = await db.query('SELECT LlantasMarca, LlantasModelo, LlantasMedida, LlantasMm, LlantasPrecio FROM llantas WHERE idLlantas = ?', [Llantas_idLlantas]);
          lRow = lRows && lRows[0] ? lRows[0] : null;
        }
        const combined = Object.assign({}, lvOnly, {
          LlantasMarca: lRow ? lRow.LlantasMarca : null,
          LlantasModelo: lRow ? lRow.LlantasModelo : null,
          LlantasMedida: lRow ? lRow.LlantasMedida : null,
          LlantasMm: lRow ? lRow.LlantasMm : null,
          LlantasPrecio: lRow ? lRow.LlantasPrecio : null
        });
        return res.status(201).json(combined);
      } catch (e) {
        const fallback = {
          idLlantasVehiculos: result.insertId,
          Llantas_idLlantas: Llantas_idLlantas,
          Vehiculos_idVehiculos: Vehiculos_idVehiculos,
          LlantasVehiculosNoQuemado: LlantasVehiculosNoQuemado,
          LlantasVehiculosPresion: LlantasVehiculosPresion,
          LlantasVehiculosPrecio: LlantasVehiculosPrecio,
          LlantasVehiculosPiso: LlantasVehiculosPiso,
          LlantasVehiculosFechaInicio: fechaInicio,
          LlantasVehiculosMM1: LlantasVehiculosMM1,
          LlantasVehiculosMM2: LlantasVehiculosMM2,
          LlantasVehiculosMM3: LlantasVehiculosMM3,
          LlantasVehiculosMM4: LlantasVehiculosMM4,
          LlantasMarca: null,
          LlantasModelo: null,
          LlantasMedida: null,
          LlantasMm: null,
          LlantasPrecio: null
        };
        return res.status(201).json(fallback);
      }
    }
  } catch (error) {
    handleServerError(res, 'Error al crear la llanta del vehículo', error);
  }
});

router.post('/batch', async (req, res) => {
  console.log('Received batch request');
  console.log(req.body);

  const llantas = req.body;
  if (!Array.isArray(llantas) || llantas.length === 0) {
    return res.status(400).json({ message: 'Debe enviar un arreglo de llantas' });
  }
  const results = [];
  const errors = [];
  for (const data of llantas) {
    try {
      const fechaInicio = data.LlantasVehiculosFechaInicio || new Date();
      if (data.replaceId) {
        const replaceId = data.replaceId;
        const [updateResult] = await db.query(
          `UPDATE llantasvehiculos SET
            Llantas_idLlantas = ?,
            Vehiculos_idVehiculos = ?,
            LlantasVehiculosNoQuemado = ?,
            LlantasVehiculosPresion = ?,
            LlantasVehiculosPrecio = ?,
            LlantasVehiculosPiso = ?,
            LlantasVehiculosFechaInicio = ?,
            LlantasVehiculosMM1 = ?,
            LlantasVehiculosMM2 = ?,
            LlantasVehiculosMM3 = ?,
            LlantasVehiculosMM4 = ?
          WHERE idLlantasVehiculos = ?`,
          [
            data.Llantas_idLlantas,
            data.Vehiculos_idVehiculos,
            data.LlantasVehiculosNoQuemado,
            data.LlantasVehiculosPresion,
            data.LlantasVehiculosPrecio,
            data.LlantasVehiculosPiso,
            fechaInicio,
            data.LlantasVehiculosMM1,
            data.LlantasVehiculosMM2,
            data.LlantasVehiculosMM3,
            data.LlantasVehiculosMM4,
            replaceId
          ]
        );

        const [updatedRecord] = await db.query(
          `SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio
           FROM llantasvehiculos lv
           JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
           WHERE lv.idLlantasVehiculos = ?`,
          [replaceId]
        );
        results.push(updatedRecord[0]);
      } else {
        const [result] = await db.query(
          `INSERT INTO llantasvehiculos (
            Llantas_idLlantas,
            Vehiculos_idVehiculos,
            LlantasVehiculosNoQuemado,
            LlantasVehiculosPresion,
            LlantasVehiculosPrecio,
            LlantasVehiculosPiso,
            LlantasVehiculosFechaInicio,
            LlantasVehiculosMM1,
            LlantasVehiculosMM2,
            LlantasVehiculosMM3,
            LlantasVehiculosMM4
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            data.Llantas_idLlantas,
            data.Vehiculos_idVehiculos,
            data.LlantasVehiculosNoQuemado,
            data.LlantasVehiculosPresion,
            data.LlantasVehiculosPrecio,
            data.LlantasVehiculosPiso,
            fechaInicio,
            data.LlantasVehiculosMM1,
            data.LlantasVehiculosMM2,
            data.LlantasVehiculosMM3,
            data.LlantasVehiculosMM4
          ]
        );
        
        // Fetch the created record to return complete object
        const [createdRecord] = await db.query(
          `SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida, l.LlantasMm, l.LlantasPrecio
           FROM llantasvehiculos lv
           JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
           WHERE lv.idLlantasVehiculos = ?`,
          [result.insertId]
        );
        if (createdRecord && createdRecord[0]) {
          results.push(createdRecord[0]);
        } else {
          try {
            const [lvOnlyRows3] = await db.query('SELECT * FROM llantasvehiculos WHERE idLlantasVehiculos = ?', [result.insertId]);
            const lvOnly3 = lvOnlyRows3 && lvOnlyRows3[0] ? lvOnlyRows3[0] : {};
            let lRow3 = null;
            if (data.Llantas_idLlantas) {
              const [lRows3] = await db.query('SELECT LlantasMarca, LlantasModelo, LlantasMedida, LlantasMm, LlantasPrecio FROM llantas WHERE idLlantas = ?', [data.Llantas_idLlantas]);
              lRow3 = lRows3 && lRows3[0] ? lRows3[0] : null;
            }
            const combined3 = Object.assign({}, lvOnly3, {
              LlantasMarca: lRow3 ? lRow3.LlantasMarca : null,
              LlantasModelo: lRow3 ? lRow3.LlantasModelo : null,
              LlantasMedida: lRow3 ? lRow3.LlantasMedida : null,
              LlantasMm: lRow3 ? lRow3.LlantasMm : null,
              LlantasPrecio: lRow3 ? lRow3.LlantasPrecio : null
            });
            results.push(combined3);
          } catch (e) {
            const fallback = {
              idLlantasVehiculos: result.insertId,
              Llantas_idLlantas: data.Llantas_idLlantas,
              Vehiculos_idVehiculos: data.Vehiculos_idVehiculos,
              LlantasVehiculosNoQuemado: data.LlantasVehiculosNoQuemado,
              LlantasVehiculosPresion: data.LlantasVehiculosPresion,
              LlantasVehiculosPrecio: data.LlantasVehiculosPrecio,
              LlantasVehiculosPiso: data.LlantasVehiculosPiso,
              LlantasVehiculosFechaInicio: fechaInicio,
              LlantasVehiculosMM1: data.LlantasVehiculosMM1,
              LlantasVehiculosMM2: data.LlantasVehiculosMM2,
              LlantasVehiculosMM3: data.LlantasVehiculosMM3,
              LlantasVehiculosMM4: data.LlantasVehiculosMM4,
              LlantasMarca: null,
              LlantasModelo: null,
              LlantasMedida: null,
              LlantasMm: null,
              LlantasPrecio: null
            };
            results.push(fallback);
          }
        }
      }
    } catch (error) {
      errors.push({ error: error.message, data });
    }
  }
  if (errors.length > 0) {
    return res.status(207).json({ message: 'Algunas llantas no se guardaron', results, errors });
  }
  res.status(201).json(results);
});

// PUT/UPDATE a llanta vehiculo
router.put('/:id', async (req, res) => {
  try {
    const { 
      Llantas_idLlantas,
      Vehiculos_idVehiculos,
      LlantasVehiculosNoQuemado,
      LlantasVehiculosPresion,
      LlantasVehiculosPrecio,
      LlantasVehiculosPiso
    } = req.body;
    
    const [result] = await db.query(
      `UPDATE llantasvehiculos SET 
        Llantas_idLlantas = ?,
        Vehiculos_idVehiculos = ?,
        LlantasVehiculosNoQuemado = ?,
        LlantasVehiculosPresion = ?,
        LlantasVehiculosPrecio = ?,
        LlantasVehiculosPiso = ?
      WHERE idLlantasVehiculos = ?`,
      [
        Llantas_idLlantas,
        Vehiculos_idVehiculos,
        LlantasVehiculosNoQuemado,
        LlantasVehiculosPresion,
        LlantasVehiculosPrecio,
        LlantasVehiculosPiso,
        req.params.id
      ]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta de vehículo no encontrada' });
    }
    
    res.json({ message: 'Llanta del vehículo actualizada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar la llanta del vehículo', error);
  }
});

// DELETE a llanta vehiculo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM llantasvehiculos WHERE idLlantasVehiculos = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Llanta de vehículo no encontrada' });
    }
    
    res.json({ message: 'Llanta del vehículo eliminada correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar la llanta del vehículo', error);
  }
});

module.exports = router;

// POST retirar (mover a historial + copiar rendimientos) - Opción B
router.post('/retirar', async (req, res) => {
  const { idLlantasVehiculos, causa, usuarioId, replacedBy } = req.body;
  if (!idLlantasVehiculos) return res.status(400).json({ message: 'Falta idLlantasVehiculos' });

  const conn = await db.getConnection();
  try {
    await conn.beginTransaction();

    // 1) Obtener snapshot de la llanta
    const [rows] = await conn.query('SELECT * FROM llantasvehiculos WHERE idLlantasVehiculos = ?', [idLlantasVehiculos]);
    if (rows.length === 0) {
      await conn.rollback();
      conn.release();
      return res.status(404).json({ message: 'Llanta de vehículo no encontrada' });
    }
    const lv = rows[0];

    // 2) Insertar cabecera en llantas_terminadas
    const [insertRes] = await conn.query(
      `INSERT INTO llantas_terminadas (
        LlantasVehiculos_idLlantasVehiculos,
        Vehiculos_idVehiculos,
        Llantas_idLlantas,
        LlantasVehiculosNoQuemado,
        LlantasVehiculosPresion,
        LlantasVehiculosMM1,
        LlantasVehiculosMM2,
        LlantasVehiculosMM3,
        LlantasVehiculosMM4,
        LlantasVehiculosPiso,
        Usuario_idUsuarios,
        Causa,
        ReplacedBy_idLlantasVehiculos,
        Snapshot
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        idLlantasVehiculos,
        lv.Vehiculos_idVehiculos,
        lv.Llantas_idLlantas,
        lv.LlantasVehiculosNoQuemado,
        lv.LlantasVehiculosPresion,
        lv.LlantasVehiculosMM1,
        lv.LlantasVehiculosMM2,
        lv.LlantasVehiculosMM3,
        lv.LlantasVehiculosMM4,
        lv.LlantasVehiculosPiso,
        usuarioId || null,
        causa || null,
        replacedBy || null,
        JSON.stringify(lv)
      ]
    );
    const terminadaId = insertRes.insertId;

    // 3) Copiar historiales de llantasrendimiento
    const [rendRows] = await conn.query('SELECT * FROM llantasrendimiento WHERE LlantasVehiculos_idLlantasVehiculos = ? ORDER BY idLlantasRendimiento', [idLlantasVehiculos]);
    for (const r of rendRows) {
      await conn.query(
        `INSERT INTO llantas_terminadas_rendimientos (
          LlantasTerminadas_id,
          idLlantasRendimiento,
          PruebaRendimiento_idPruebaRendimiento,
          LlantasRendimientoMm1,
          LlantasRendimientoMm2,
          LlantasRendimientoMm3,
          LlantasRendimientoMm4,
          LlantasRendimientoPresion,
          LlantasRendimientoComent,
          FechaRegistro
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          terminadaId,
          r.idLlantasRendimiento,
          r.PruebaRendimiento_idPruebaRendimiento,
          r.LlantasRendimientoMm1,
          r.LlantasRendimientoMm2,
          r.LlantasRendimientoMm3,
          r.LlantasRendimientoMm4,
          r.LlantasRendimientoPresion,
          r.LlantasRendimientoComent,
          r.FechaRegistro || null
        ]
      );
    }

    // 4) Eliminar la llanta original para que no aparezca al recargar (alternativa: marcar terminada si prefieres)
    await conn.query('DELETE FROM llantasvehiculos WHERE idLlantasVehiculos = ?', [idLlantasVehiculos]);

    await conn.commit();
    conn.release();
    res.status(200).json({ message: 'Llanta retirada y historial copiado', terminadaId });
  } catch (error) {
    try { await conn.rollback(); } catch (_) {}
    conn.release();
    handleServerError(res, 'Error al retirar la llanta', error);
  }
});
