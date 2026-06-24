const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');

function isDebugRequest(req) {
  return req.query.debug === '1' || process.env.DEBUG_INCENTIVOS === '1';
}

function debugLog(req, ...args) {
  if (isDebugRequest(req)) {
    console.log('[incentivos-debug]', ...args);
  }
}

function toInt(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function toNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeText(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toUpperCase();
}

function isAllowedProfileName(value) {
  const normalized = normalizeText(value);
  return (
    normalized === 'JOKER' ||
    normalized === 'BECARIO' ||
    normalized === 'TECNICO YOKOHAMA' ||
    normalized.includes('JOKER') ||
    normalized.includes('BECARIO') ||
    normalized.includes('TECNICO') && normalized.includes('YOKOHAMA')
  );
}

function parseDistribuidorIds(rawValue) {
  if (!rawValue) {
    return [];
  }

  if (Array.isArray(rawValue)) {
    return rawValue
      .flatMap((part) => String(part).split(','))
      .map((part) => Number(part.trim()))
      .filter((id) => Number.isInteger(id) && id > 0);
  }

  return String(rawValue)
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((id) => Number.isInteger(id) && id > 0);
}

function mapMetricRows(metricRows) {
  const byUser = new Map();

  metricRows.forEach((row) => {
    const userId = Number(row.usuarioId);
    if (!Number.isInteger(userId) || userId <= 0) {
      return;
    }

    if (!byUser.has(userId)) {
      byUser.set(userId, {
        flotaIds: new Set(),
        bases: 0,
        real: 0
      });
    }

    const entry = byUser.get(userId);
    const flotaId = Number(row.flotaId);
    if (Number.isInteger(flotaId) && flotaId > 0) {
      entry.flotaIds.add(flotaId);
    }

    entry.bases += toInt(row.bases);
    entry.real += toInt(row.realCount ?? row.real);
  });

  return byUser;
}

async function resolvePersonalTable(executor) {
  const [rows] = await executor.query(`
    SELECT TABLE_NAME
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('incentivospersonal', 'incentivosindividuales', 'incentivopersonal', 'incentivos_personales', 'incentivosdetalle', 'incentivos_detalle')
    ORDER BY FIELD(TABLE_NAME, 'incentivospersonal', 'incentivosindividuales', 'incentivopersonal', 'incentivos_personales', 'incentivosdetalle', 'incentivos_detalle')
    LIMIT 1
  `);

  return rows.length ? rows[0].TABLE_NAME : null;
}

function calcRealFromPct(bases, pctCumplido) {
  return (toNumber(bases) * toNumber(pctCumplido)) / 100;
}

async function fetchPreviewUsers(distribuidorIds) {
  const withJoin = [
    'SELECT u.idUsuarios, u.UsuariosNombre, u.Distribuidor_idDistribuidor,',
    '       u.PerfilesUsuario_idPerfilesUsuario, p.PerfilesUsuarioNombre, d.DistribuidorNombre',
    'FROM usuarios u',
    'LEFT JOIN perfilesusuario p ON p.idPerfilesUsuario = u.PerfilesUsuario_idPerfilesUsuario',
    'LEFT JOIN distribuidor d ON d.idDistribuidor = u.Distribuidor_idDistribuidor',
    'WHERE u.idUsuarios IS NOT NULL'
  ];

  const withoutJoin = [
    'SELECT u.idUsuarios, u.UsuariosNombre, u.Distribuidor_idDistribuidor,',
    '       u.PerfilesUsuario_idPerfilesUsuario, p.PerfilesUsuarioNombre, NULL AS DistribuidorNombre',
    'FROM usuarios u',
    'LEFT JOIN perfilesusuario p ON p.idPerfilesUsuario = u.PerfilesUsuario_idPerfilesUsuario',
    'WHERE u.idUsuarios IS NOT NULL'
  ];

  const buildQuery = (parts) => {
    const queryParts = [...parts];
    const params = [];

    if (distribuidorIds.length) {
      queryParts.push(` AND u.Distribuidor_idDistribuidor IN (${distribuidorIds.map(() => '?').join(',')})`);
      params.push(...distribuidorIds);
    }

    return { sql: queryParts.join('\n'), params };
  };

  const primary = buildQuery(withJoin);
  try {
    return await db.query(primary.sql, primary.params).then(([rows]) => rows);
  } catch (error) {
    console.error('Preview users query failed; retrying without distribuidor join:', error);
    const fallback = buildQuery(withoutJoin);
    return db.query(fallback.sql, fallback.params).then(([rows]) => rows);
  }
}

router.get('/detalle-real', async (req, res) => {
  try {
    const mes = toInt(req.query.mes);
    const anio = toInt(req.query.anio);
    const usuarioId = toInt(req.query.usuarioId);
    const tipo = String(req.query.tipo || '').trim().toLowerCase();

    if (!mes || !anio || !usuarioId || !tipo) {
      return res.status(400).json({ message: 'Mes, anio, usuarioId y tipo son obligatorios' });
    }

    let sql = '';
    const params = [mes, anio, usuarioId];

    if (tipo === 'rend') {
      sql = `
        SELECT
          pr.idPruebaRendimiento AS pruebaId,
          pr.PruebaRendimientoFecha AS fecha,
          pr.PruebaRendimientoLat AS latitude,
          pr.PruebaRendimientoLon AS longitude,
          pr.Vehiculos_idVehiculos AS vehiculoId,
          COALESCE(v.VehiculosNumero, CONCAT('AUTO ', pr.Vehiculos_idVehiculos)) AS vehiculo,
          lr.idLlantasRendimiento AS llantaId,
          lr.LlantasRendimientoPresion AS presion,
          lr.LlantasRendimientoMm1 AS mm1,
          lr.LlantasRendimientoMm2 AS mm2,
          lr.LlantasRendimientoMm3 AS mm3,
          lr.LlantasRendimientoMm4 AS mm4,
          CASE WHEN lr.LlantasRendimientoCondPel = 1 THEN 'rojo' ELSE 'verde' END AS statusColor,
          COALESCE(l.LlantasMarca, 'LLANTA') AS llantaMarca,
          COALESCE(l.LlantasModelo, '') AS llantaModelo,
          COALESCE(l.LlantasMedida, '') AS llantaMedida,
          COALESCE(lv.LlantasVehiculosPiso, 'Original') AS piso,
          COALESCE(v.VehiculosNumero, CONCAT('AUTO ', pr.Vehiculos_idVehiculos)) AS vehicleLabel,
          CONCAT('Presión: ', COALESCE(lr.LlantasRendimientoPresion, 'Sin dato')) AS detailLine1,
          CONCAT('MM: ', COALESCE(lr.LlantasRendimientoMm1, '-'), ' / ', COALESCE(lr.LlantasRendimientoMm2, '-'), ' / ', COALESCE(lr.LlantasRendimientoMm3, '-'), ' / ', COALESCE(lr.LlantasRendimientoMm4, '-')) AS detailLine2
        FROM pruebarendimiento pr
        LEFT JOIN vehiculos v ON v.idVehiculos = pr.Vehiculos_idVehiculos
        LEFT JOIN llantasrendimiento lr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
        LEFT JOIN llantasvehiculos lv ON lv.idLlantasVehiculos = lr.LlantasVehiculos_idLlantasVehiculos
        LEFT JOIN llantas l ON l.idLlantas = lv.Llantas_idLlantas
        WHERE MONTH(pr.PruebaRendimientoFecha) = ?
          AND YEAR(pr.PruebaRendimientoFecha) = ?
          AND pr.Usuarios_idUsuarios = ?
        ORDER BY pr.PruebaRendimientoFecha DESC, vehicleLabel ASC, llantaId ASC
      `;
    } else if (tipo === 'sem') {
      sql = `
        SELECT
          ps.idPruebasSemaforo AS pruebaId,
          ps.PruebasSemaforoFecha AS fecha,
          ps.latitude AS latitude,
          ps.longitude AS longitude,
          vps.idVehiculoPruebaSemaforo AS vehiculoId,
          COALESCE(vps.VehiculoPruebaSemaforoNo, CONCAT('AUTO ', vps.idVehiculoPruebaSemaforo)) AS vehiculo,
          ls.idLlantasSemaforo AS llantaId,
          ls.LlantasSemaforoPresion AS presion,
          ls.LlantasSemaforoPiso AS piso,
          ls.LlantasSemaforoColor AS statusColor,
          ls.LlantasSemaforoCondPel AS condPel,
          ls.LlantasSemaforoVigia AS vigia,
          COALESCE(l.LlantasMarca, 'LLANTA') AS llantaMarca,
          COALESCE(l.LlantasModelo, '') AS llantaModelo,
          COALESCE(l.LlantasMedida, '') AS llantaMedida,
          CONCAT('Presión: ', CASE WHEN ls.LlantasSemaforoVigia = 1 THEN 'Vigía' ELSE COALESCE(ls.LlantasSemaforoPresion, 'Sin dato') END) AS detailLine1,
          CONCAT('Piso: ', COALESCE(ls.LlantasSemaforoPiso, 'Original')) AS detailLine2
        FROM pruebassemaforo ps
        LEFT JOIN vehiculopruebasemaforo vps ON vps.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
        LEFT JOIN llantassemaforo ls ON ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo = vps.idVehiculoPruebaSemaforo
        LEFT JOIN llantas l ON l.idLlantas = ls.Llantas_idLlantas
        WHERE MONTH(ps.PruebasSemaforoFecha) = ?
          AND YEAR(ps.PruebasSemaforoFecha) = ?
          AND vps.Usuarios_idUsuarios = ?
        ORDER BY ps.PruebasSemaforoFecha DESC, vehiculo ASC, llantaId ASC
      `;
    } else if (tipo === 'des') {
      sql = `
        SELECT
          pd.idPruebasDesecho AS pruebaId,
          pd.PruebasDesechoFecha AS fecha,
          NULL AS latitude,
          NULL AS longitude,
          NULL AS vehiculoId,
          NULL AS vehiculo,
          ld.idLlantasDesecho AS llantaId,
          ld.LlantasDesechoNoLlanta AS LlantasDesechoNoLlanta,
          ld.LlantasDesechoPiso AS piso,
          ld.LlantasDesechoCausaDes AS causaDes,
          ld.LlantasDesechoRemanente AS remanente,
          COALESCE(l.LlantasMarca, 'LLANTA') AS llantaMarca,
          COALESCE(l.LlantasModelo, '') AS llantaModelo,
          COALESCE(l.LlantasMedida, '') AS llantaMedida,
          CASE WHEN COALESCE(ld.LlantasDesechoCausaDes, '') <> '' THEN 'rojo' ELSE 'gris' END AS statusColor,
          CONCAT('Causa: ', COALESCE(ld.LlantasDesechoCausaDes, 'Sin dato')) AS detailLine1,
          CONCAT('Piso: ', COALESCE(ld.LlantasDesechoPiso, '-'), ' | Remanente: ', COALESCE(ld.LlantasDesechoRemanente, '-')) AS detailLine2
        FROM pruebasdesecho pd
        LEFT JOIN llantasdesecho ld ON ld.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
        LEFT JOIN llantas l ON l.idLlantas = ld.Llantas_idLlantas
        WHERE MONTH(pd.PruebasDesechoFecha) = ?
          AND YEAR(pd.PruebasDesechoFecha) = ?
          AND ld.Usuarios_idUsuarios = ?
        ORDER BY pd.PruebasDesechoFecha DESC, llantaId DESC
      `;
    } else if (tipo === 'ins') {
      sql = `
        SELECT
          pi.idPruebaInspeccion AS pruebaId,
          pi.PruebaInspeccionFecha AS fecha,
          pi.latitude AS latitude,
          pi.longitude AS longitude,
          vi.idVehiculoInspeccion AS vehiculoId,
          COALESCE(vi.VehiculoInspeccionNo, CONCAT('AUTO ', vi.idVehiculoInspeccion)) AS vehiculo,
          li.idLlantasInspeccion AS llantaId,
          li.LlantasInspeccionPresion AS presion,
          li.LlantasInspeccionPiso AS piso,
          li.LlantasInspeccionCondPel AS condPel,
          li.LlantasInspeccionVigia AS vigia,
          li.LlantasInspeccionMm1 AS mm1,
          li.LlantasInspeccionMm2 AS mm2,
          li.LlantasInspeccionMm3 AS mm3,
          li.LlantasInspeccionMm4 AS mm4,
          COALESCE(l.LlantasMarca, 'LLANTA') AS llantaMarca,
          COALESCE(l.LlantasModelo, '') AS llantaModelo,
          COALESCE(l.LlantasMedida, '') AS llantaMedida,
          CASE WHEN li.LlantasInspeccionCondPel = 1 THEN 'rojo' ELSE 'verde' END AS statusColor,
          CONCAT('Presión: ', CASE WHEN li.LlantasInspeccionVigia = 1 THEN 'Vigía' ELSE COALESCE(li.LlantasInspeccionPresion, 'Sin dato') END) AS detailLine1,
          CONCAT('Piso: ', COALESCE(li.LlantasInspeccionPiso, 'Original'), ' | MM: ', COALESCE(li.LlantasInspeccionMm1, '-'), '/', COALESCE(li.LlantasInspeccionMm2, '-'), '/', COALESCE(li.LlantasInspeccionMm3, '-'), '/', COALESCE(li.LlantasInspeccionMm4, '-')) AS detailLine2
        FROM pruebasinspeccion pi
        LEFT JOIN vehiculosinspeccion vi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
        LEFT JOIN llantasinspeccion li ON li.vehiculosinspeccion_idVehiculoInspeccion = vi.idVehiculoInspeccion
        LEFT JOIN llantas l ON l.idLlantas = li.Llantas_idLlantas
        WHERE MONTH(pi.PruebaInspeccionFecha) = ?
          AND YEAR(pi.PruebaInspeccionFecha) = ?
          AND vi.Usuarios_idUsuarios = ?
        ORDER BY pi.PruebaInspeccionFecha DESC, vehiculo ASC, llantaId ASC
      `;
    } else {
      return res.status(400).json({ message: 'Tipo invalido. Usa: rend, sem, des, ins' });
    }

    const [rows] = await db.query(sql, params);
    const vehicles = [...new Set(rows.map((row) => String(row.vehiculo || '').trim()).filter((value) => value))];

    return res.json({
      mes,
      anio,
      usuarioId,
      tipo,
      totalRegistros: rows.length,
      vehicles,
      items: rows
    });
  } catch (error) {
    handleServerError(res, 'Error al obtener detalle de vehiculos del REAL', error);
  }
});

router.get('/distribuidores', async (_req, res) => {
  try {
    const candidates = [
      'SELECT idDistribuidor AS idDistribuidor, DistribuidorNombre AS DistribuidorNombre FROM distribuidor ORDER BY DistribuidorNombre ASC',
      'SELECT idDistribuidor AS idDistribuidor, DistribuidorNombre AS DistribuidorNombre FROM distribuidores ORDER BY DistribuidorNombre ASC',
      'SELECT idDistribuidores AS idDistribuidor, DistribuidorNombre AS DistribuidorNombre FROM distribuidor ORDER BY DistribuidorNombre ASC',
      'SELECT idDistribuidores AS idDistribuidor, DistribuidorNombre AS DistribuidorNombre FROM distribuidores ORDER BY DistribuidorNombre ASC'
    ];

    for (const sql of candidates) {
      try {
        const [rows] = await db.query(sql);
        return res.json(rows);
      } catch (_tableError) {
        // Try next known table/column combination.
      }
    }

    // Last fallback: infer ids from users so the UI is never empty.
    const [rowsFromUsers] = await db.query(`
      SELECT DISTINCT
        u.Distribuidor_idDistribuidor AS idDistribuidor,
        CONCAT('Distribuidor ', u.Distribuidor_idDistribuidor) AS DistribuidorNombre
      FROM usuarios u
      WHERE u.Distribuidor_idDistribuidor IS NOT NULL
      ORDER BY u.Distribuidor_idDistribuidor ASC
    `);

    return res.json(rowsFromUsers);
  } catch (error) {
    handleServerError(res, 'Error al obtener distribuidores', error);
  }
});

async function createDistribuidorHandler(req, res) {
  try {
    const nombre = String(req.body?.nombre || '').trim();

    if (!nombre) {
      return res.status(400).json({ message: 'El nombre del distribuidor es obligatorio' });
    }

    if (nombre.length > 120) {
      return res.status(400).json({ message: 'El nombre del distribuidor es demasiado largo' });
    }

    const candidates = [
      { table: 'distribuidor', idColumn: 'idDistribuidor', nameColumn: 'DistribuidorNombre' },
      { table: 'distribuidores', idColumn: 'idDistribuidor', nameColumn: 'DistribuidorNombre' },
      { table: 'distribuidor', idColumn: 'idDistribuidores', nameColumn: 'DistribuidorNombre' },
      { table: 'distribuidores', idColumn: 'idDistribuidores', nameColumn: 'DistribuidorNombre' }
    ];

    for (const candidate of candidates) {
      try {
        const [existingRows] = await db.query(
          `SELECT ${candidate.idColumn} AS idDistribuidor
           FROM ${candidate.table}
           WHERE UPPER(TRIM(${candidate.nameColumn})) = UPPER(TRIM(?))
           LIMIT 1`,
          [nombre]
        );

        if (existingRows.length) {
          return res.status(409).json({
            message: 'Ese distribuidor ya existe',
            idDistribuidor: toInt(existingRows[0].idDistribuidor, null),
            DistribuidorNombre: nombre
          });
        }

        const [insertResult] = await db.query(
          `INSERT INTO ${candidate.table} (${candidate.nameColumn}) VALUES (?)`,
          [nombre]
        );

        return res.status(201).json({
          idDistribuidor: toInt(insertResult?.insertId, null),
          DistribuidorNombre: nombre
        });
      } catch (_tableError) {
        // Try next known table/column combination.
      }
    }

    return res.status(500).json({ message: 'No se encontro una tabla valida para guardar distribuidores' });
  } catch (error) {
    handleServerError(res, 'Error al crear distribuidor', error);
  }
}

router.post('/distribuidores', createDistribuidorHandler);
router.post('/distribuidor', createDistribuidorHandler);

async function deleteDistribuidorHandler(req, res) {
  try {
    const distribuidorId = toInt(req.params.id || req.body?.distribuidorId || req.query?.distribuidorId, 0);

    if (!distribuidorId) {
      return res.status(400).json({ message: 'El id del distribuidor es obligatorio' });
    }

    const candidates = [
      { table: 'distribuidor', idColumn: 'idDistribuidor' },
      { table: 'distribuidores', idColumn: 'idDistribuidor' },
      { table: 'distribuidor', idColumn: 'idDistribuidores' },
      { table: 'distribuidores', idColumn: 'idDistribuidores' }
    ];

    for (const candidate of candidates) {
      try {
        const [existsRows] = await db.query(
          `SELECT ${candidate.idColumn} AS idDistribuidor
           FROM ${candidate.table}
           WHERE ${candidate.idColumn} = ?
           LIMIT 1`,
          [distribuidorId]
        );

        if (!existsRows.length) {
          continue;
        }

        const [deleteResult] = await db.query(
          `DELETE FROM ${candidate.table} WHERE ${candidate.idColumn} = ? LIMIT 1`,
          [distribuidorId]
        );

        if (!deleteResult?.affectedRows) {
          return res.status(404).json({ message: 'Distribuidor no encontrado' });
        }

        return res.json({ message: 'Distribuidor eliminado correctamente', idDistribuidor: distribuidorId });
      } catch (tableError) {
        const mysqlErrorCode = String(tableError?.code || '');
        if (mysqlErrorCode === 'ER_ROW_IS_REFERENCED_2' || mysqlErrorCode === 'ER_ROW_IS_REFERENCED') {
          return res.status(409).json({
            message: 'No se puede borrar el distribuidor porque esta siendo usado por otros registros'
          });
        }
        // Try next known table/column combination.
      }
    }

    return res.status(404).json({ message: 'Distribuidor no encontrado' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar distribuidor', error);
  }
}

router.delete('/distribuidores/:id', deleteDistribuidorHandler);
router.delete('/distribuidor/:id', deleteDistribuidorHandler);
router.post('/distribuidores/:id/eliminar', deleteDistribuidorHandler);
router.post('/distribuidor/:id/eliminar', deleteDistribuidorHandler);
router.post('/eliminar-distribuidor', deleteDistribuidorHandler);

router.get('/config', async (req, res) => {
  try {
    const mes = toInt(req.query.mes);
    const anio = toInt(req.query.anio);
    const distribuidorId = toInt(req.query.distribuidorId);
    debugLog(req, 'GET /config params', { mes, anio, distribuidorId });

    if (!mes || !anio || !distribuidorId) {
      return res.status(400).json({ message: 'Mes, anio y distribuidorId son obligatorios' });
    }

    const [rows] = await db.query(
      `
      SELECT *
      FROM incentivos
      WHERE MesIncentivo = ?
        AND AñoIncentivo = ?
        AND Distribuidor_idDistribuidor = ?
      ORDER BY idIncentivo DESC
      LIMIT 1
      `,
      [mes, anio, distribuidorId]
    );

    if (!rows.length) {
      debugLog(req, 'GET /config sin resultados para parametros');
      return res.json(null);
    }

    debugLog(req, 'GET /config resultado encontrado', rows[0]);
    return res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener configuración de incentivos', error);
  }
});

router.get('/preview', async (req, res) => {
  try {
    const mes = toInt(req.query.mes);
    const anio = toInt(req.query.anio);
    const distribuidorIds = parseDistribuidorIds(req.query.distribuidorIds || req.query.distribuidorId);
    debugLog(req, 'GET /preview filtros', { mes, anio, distribuidorIds });

    if (!mes || !anio) {
      return res.status(400).json({ message: 'Mes y anio son obligatorios' });
    }

    const users = await fetchPreviewUsers(distribuidorIds);

    const [flotas, rendRows, semRows, desRows, insRows, perfilesRows] = await Promise.all([
      db.query('SELECT idFlotas, FlotasZona FROM flotas').then(([rows]) => rows),
      db.query(
        `
        SELECT
          pr.Usuarios_idUsuarios AS usuarioId,
          v.Flotas_idFlotas AS flotaId,
          COUNT(DISTINCT pr.Vehiculos_idVehiculos) AS bases,
          COUNT(lr.idLlantasRendimiento) AS realCount
        FROM pruebarendimiento pr
        INNER JOIN vehiculos v ON v.idVehiculos = pr.Vehiculos_idVehiculos
        LEFT JOIN llantasrendimiento lr ON lr.PruebaRendimiento_idPruebaRendimiento = pr.idPruebaRendimiento
        WHERE MONTH(pr.PruebaRendimientoFecha) = ?
          AND YEAR(pr.PruebaRendimientoFecha) = ?
          AND pr.Usuarios_idUsuarios IS NOT NULL
        GROUP BY pr.Usuarios_idUsuarios, v.Flotas_idFlotas
        `,
        [mes, anio]
      ).then(([rows]) => rows),
      db.query(
        `
        SELECT
          vps.Usuarios_idUsuarios AS usuarioId,
          ps.Flotas_idFlotas AS flotaId,
          COUNT(DISTINCT ps.idPruebasSemaforo) AS bases,
          COUNT(ls.idLlantasSemaforo) AS realCount
        FROM pruebassemaforo ps
        LEFT JOIN vehiculopruebasemaforo vps ON vps.PruebasSemaforo_idPruebasSemaforo = ps.idPruebasSemaforo
        LEFT JOIN llantassemaforo ls ON ls.VehiculoPruebaSemaforo_idVehiculoPruebaSemaforo = vps.idVehiculoPruebaSemaforo
        WHERE MONTH(ps.PruebasSemaforoFecha) = ?
          AND YEAR(ps.PruebasSemaforoFecha) = ?
          AND vps.Usuarios_idUsuarios IS NOT NULL
        GROUP BY vps.Usuarios_idUsuarios, ps.Flotas_idFlotas
        `,
        [mes, anio]
      ).then(([rows]) => rows),
      db.query(
        `
        SELECT
          ld.Usuarios_idUsuarios AS usuarioId,
          pd.Flotas_idFlotas AS flotaId,
          COUNT(DISTINCT pd.idPruebasDesecho) AS bases,
          COUNT(ld.idLlantasDesecho) AS realCount
        FROM pruebasdesecho pd
        LEFT JOIN llantasdesecho ld ON ld.PruebasDesecho_idPruebasDesecho = pd.idPruebasDesecho
        WHERE MONTH(pd.PruebasDesechoFecha) = ?
          AND YEAR(pd.PruebasDesechoFecha) = ?
          AND ld.Usuarios_idUsuarios IS NOT NULL
        GROUP BY ld.Usuarios_idUsuarios, pd.Flotas_idFlotas
        `,
        [mes, anio]
      ).then(([rows]) => rows),
      db.query(
        `
        SELECT
          vi.Usuarios_idUsuarios AS usuarioId,
          pi.Flotas_idFlotas AS flotaId,
          COUNT(DISTINCT pi.idPruebaInspeccion) AS bases,
          COUNT(li.idLlantasInspeccion) AS realCount
        FROM pruebasinspeccion pi
        LEFT JOIN vehiculosinspeccion vi ON vi.pruebasinspeccion_idPruebaInspeccion = pi.idPruebaInspeccion
        LEFT JOIN llantasinspeccion li ON li.vehiculosinspeccion_idVehiculoInspeccion = vi.idVehiculoInspeccion
        WHERE MONTH(pi.PruebaInspeccionFecha) = ?
          AND YEAR(pi.PruebaInspeccionFecha) = ?
          AND vi.Usuarios_idUsuarios IS NOT NULL
        GROUP BY vi.Usuarios_idUsuarios, pi.Flotas_idFlotas
        `,
        [mes, anio]
      ).then(([rows]) => rows),
      db.query('SELECT idPerfilesUsuario, PerfilesUsuarioNombre FROM perfilesusuario').then(([rows]) => rows)
    ]);

    const flotaMap = new Map(
      flotas.map((flota) => [Number(flota.idFlotas), String(flota.FlotasZona || '').trim().toUpperCase() || 'SIN ZONA'])
    );

    const rendByUser = mapMetricRows(rendRows);
    const semByUser = mapMetricRows(semRows);
    const desByUser = mapMetricRows(desRows);
    const insByUser = mapMetricRows(insRows);

    const jokerProfileIds = new Set(
      perfilesRows
        .filter((perfil) => normalizeText(perfil.PerfilesUsuarioNombre).includes('JOKER'))
        .map((perfil) => Number(perfil.idPerfilesUsuario))
        .filter((id) => Number.isInteger(id) && id > 0)
    );

    const allowedProfileIds = new Set(
      perfilesRows
        .filter((perfil) => isAllowedProfileName(perfil.PerfilesUsuarioNombre))
        .map((perfil) => Number(perfil.idPerfilesUsuario))
        .filter((id) => Number.isInteger(id) && id > 0)
    );

    const allowedUsers = users.filter((user) => {
      const profileId = Number(user.PerfilesUsuario_idPerfilesUsuario);
      const normalizedProfile = normalizeText(user.PerfilesUsuarioNombre);
      const isJoker = normalizedProfile.includes('JOKER') || jokerProfileIds.has(profileId);
      return isJoker || isAllowedProfileName(user.PerfilesUsuarioNombre) || allowedProfileIds.has(profileId);
    });

    let effectiveUsers = allowedUsers;
    if (!distribuidorIds.length && effectiveUsers.length === 0) {
      debugLog(req, 'Sin distribuidor y sin usuarios permitidos en primer filtro; aplicando fallback directo');

      let fallbackUsers = [];
      try {
        [fallbackUsers] = await db.query(`
          SELECT u.idUsuarios, u.UsuariosNombre, u.Distribuidor_idDistribuidor,
                 u.PerfilesUsuario_idPerfilesUsuario, p.PerfilesUsuarioNombre, d.DistribuidorNombre
          FROM usuarios u
          LEFT JOIN perfilesusuario p ON p.idPerfilesUsuario = u.PerfilesUsuario_idPerfilesUsuario
          LEFT JOIN distribuidor d ON d.idDistribuidor = u.Distribuidor_idDistribuidor
          WHERE u.idUsuarios IS NOT NULL
            AND (
              UPPER(TRIM(COALESCE(p.PerfilesUsuarioNombre, ''))) LIKE '%JOKER%'
              OR UPPER(TRIM(COALESCE(p.PerfilesUsuarioNombre, ''))) LIKE '%BECARIO%'
              OR UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.PerfilesUsuarioNombre, '')), 'Á', 'A'), 'É', 'E'), 'Í', 'I'), 'Ó', 'O'), 'Ú', 'U')) LIKE '%TECNICO%YOKOHAMA%'
            )
        `);
      } catch (fallbackError) {
        console.error('Fallback users query failed; retrying without distribuidor join:', fallbackError);
        [fallbackUsers] = await db.query(`
          SELECT u.idUsuarios, u.UsuariosNombre, u.Distribuidor_idDistribuidor,
                 u.PerfilesUsuario_idPerfilesUsuario, p.PerfilesUsuarioNombre, NULL AS DistribuidorNombre
          FROM usuarios u
          LEFT JOIN perfilesusuario p ON p.idPerfilesUsuario = u.PerfilesUsuario_idPerfilesUsuario
          WHERE u.idUsuarios IS NOT NULL
            AND (
              UPPER(TRIM(COALESCE(p.PerfilesUsuarioNombre, ''))) LIKE '%JOKER%'
              OR UPPER(TRIM(COALESCE(p.PerfilesUsuarioNombre, ''))) LIKE '%BECARIO%'
              OR UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.PerfilesUsuarioNombre, '')), 'Á', 'A'), 'É', 'E'), 'Í', 'I'), 'Ó', 'O'), 'Ú', 'U')) LIKE '%TECNICO%YOKOHAMA%'
            )
        `);
      }

      effectiveUsers = fallbackUsers;
      debugLog(req, 'Fallback directo usuarios encontrados', effectiveUsers.length);
    }

    let personalByUser = new Map();
    let configByDist = new Map();
    try {
      let incentivoQuery = `
        SELECT *
        FROM incentivos
        WHERE MesIncentivo = ?
          AND AñoIncentivo = ?
      `;
      const queryParams = [mes, anio];

      if (distribuidorIds.length > 0) {
        incentivoQuery += ` AND Distribuidor_idDistribuidor IN (${distribuidorIds.map(() => '?').join(',')})`;
        queryParams.push(...distribuidorIds);
      }

      const [incentivoRows] = await db.query(incentivoQuery, queryParams);

      if (incentivoRows.length) {
        for (const r of incentivoRows) {
          configByDist.set(Number(r.Distribuidor_idDistribuidor), r);
        }

        const incentivoIds = incentivoRows.map(r => r.idIncentivo);
        const personalTable = await resolvePersonalTable(db);
        if (personalTable) {
          const [personalRows] = await db.query(
            `
            SELECT
              Usuarios_idUsuarios AS usuarioId,
              TotalIncentivoIndividual AS baseIncentivo,
              RBaseIncentivoIndividual AS basesRend,
              RPorcenIncentivoIndividual AS pctRend,
              RPorcenCumplidoIncentivoIndividual AS pctCumplidoRend,
              RPagoIncentivoIndividual AS pagoRend,
              SBaseIncentivoIndividual AS basesSem,
              SPorcenIncentivoIndividual AS pctSem,
              SPorcenCumplidoIncentivoIndividual AS pctCumplidoSem,
              SPagoIncentivoIndividual AS pagoSem,
              PBaseIncentivoIndividual AS basesDes,
              PPorcenIncentivoIndividual AS pctDes,
              PPorcenCumplidoIncentivoIndividual AS pctCumplidoDes,
              PPagoIncentivoIndividual AS pagoDes,
              IBaseIncentivoIndividual AS basesIns,
              IPorcenIncentivoIndividual AS pctIns,
              IPorcenCumplidoIncentivoIndividual AS pctCumplidoIns,
              IPagoIncentivoIndividual AS pagoIns,
              PagoTotalIncentivoIndividual AS pagoTotal
            FROM ${personalTable}
            WHERE incentivos_idIncentivo IN (${incentivoIds.map(() => '?').join(',')})
            `,
            incentivoIds
          );

          personalByUser = new Map(
            personalRows
              .filter((row) => Number.isInteger(Number(row.usuarioId)) && Number(row.usuarioId) > 0)
              .map((row) => [Number(row.usuarioId), row])
          );

          debugLog(req, 'Registros personales encontrados', {
            incentivosIds: incentivoIds,
            personalTable,
            total: personalRows.length
          });
        } else {
          debugLog(req, 'No se encontró tabla personal de incentivos; se omite el detalle individual');
        }
      }
    } catch (previewDetailError) {
      console.error('Preview personal lookup failed; continuing without personal rows:', previewDetailError);
    }

    debugLog(req, 'Usuarios totales/permitidos', {
      totalUsers: users.length,
      allowedUsers: allowedUsers.length,
      effectiveUsers: effectiveUsers.length,
      jokerProfileIds: Array.from(jokerProfileIds),
      allowedProfileIds: Array.from(allowedProfileIds),
      sampleProfiles: users.slice(0, 20).map((user) => ({
        idUsuarios: user.idUsuarios,
        profileId: user.PerfilesUsuario_idPerfilesUsuario,
        perfil: user.PerfilesUsuarioNombre,
        normalized: normalizeText(user.PerfilesUsuarioNombre)
      }))
    });

    const rows = effectiveUsers
      .map((user) => {
        const userId = Number(user.idUsuarios);
        const rend = rendByUser.get(userId) || { bases: 0, real: 0, flotaIds: new Set() };
        const sem = semByUser.get(userId) || { bases: 0, real: 0, flotaIds: new Set() };
        const des = desByUser.get(userId) || { bases: 0, real: 0, flotaIds: new Set() };
        const ins = insByUser.get(userId) || { bases: 0, real: 0, flotaIds: new Set() };
        const personal = personalByUser.get(userId) || null;
        const dId = toInt(user.Distribuidor_idDistribuidor, null);
        const distConfig = dId ? configByDist.get(dId) : null;

        const allFlotaIds = new Set([
          ...rend.flotaIds,
          ...sem.flotaIds,
          ...des.flotaIds,
          ...ins.flotaIds
        ]);

        const zonas = new Set();
        allFlotaIds.forEach((flotaId) => {
          const zona = flotaMap.get(Number(flotaId));
          if (zona) {
            zonas.add(zona);
          }
        });

        let rowBaseIncentivo = undefined;
        let rowPctRend = undefined;
        let rowPctSem = undefined;
        let rowPctDes = undefined;
        let rowPctIns = undefined;

        if (personal) {
          rowBaseIncentivo = toNumber(personal.baseIncentivo);
          rowPctRend = toNumber(personal.pctRend);
          rowPctSem = toNumber(personal.pctSem);
          rowPctDes = toNumber(personal.pctDes);
          rowPctIns = toNumber(personal.pctIns);
        } else if (distConfig) {
          rowBaseIncentivo = toNumber(distConfig.TotalIncentivo);
          rowPctRend = toNumber(distConfig.RPorcenIncentivo);
          rowPctSem = toNumber(distConfig.SPorcenIncentivo);
          rowPctDes = toNumber(distConfig.PPorcenIncentivo);
          rowPctIns = toNumber(distConfig.IPorcenIncentivo);
        }

        return {
          usuarioId: userId,
          responsable: String(user.UsuariosNombre || '').trim() || 'SIN RESPONSABLE',
          perfil: String(user.PerfilesUsuarioNombre || '').trim().toUpperCase() || 'SIN PERFIL',
          distribuidorId: dId,
          distribuidorNombre: String(user.DistribuidorNombre || '').trim() || 'SIN DISTRIBUIDOR',
          zona: zonas.size === 0 ? 'SIN ZONA' : zonas.size === 1 ? Array.from(zonas)[0] : 'MIXTA',
          flotas: allFlotaIds.size,
          baseIncentivo: rowBaseIncentivo,
          pctRend: rowPctRend,
          pctSem: rowPctSem,
          pctDes: rowPctDes,
          pctIns: rowPctIns,
          basesRend: personal ? toInt(personal.basesRend) : (distConfig ? toInt(distConfig.RBaseIncentivo) : 0),
          realRend: rend.real,
          basesSem: personal ? toInt(personal.basesSem) : (distConfig ? toInt(distConfig.SBaseIncentivo) : 0),
          realSem: sem.real,
          basesDes: personal ? toInt(personal.basesDes) : (distConfig ? toInt(distConfig.PBaseIncentivo) : 0),
          realDes: des.real,
          basesIns: personal ? toInt(personal.basesIns) : (distConfig ? toInt(distConfig.IBaseIncentivo) : 0),
          realIns: ins.real
        };
      })
      .sort((a, b) => a.responsable.localeCompare(b.responsable, 'es-MX'));

    debugLog(req, 'GET /preview conteos', {
      users: users.length,
      rendRows: rendRows.length,
      semRows: semRows.length,
      desRows: desRows.length,
      insRows: insRows.length,
      rowsFinal: rows.length
    });

    if (rows.length > 0) {
      debugLog(req, 'GET /preview muestra primera fila', rows[0]);
    }

    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al generar vista previa de incentivos', error);
  }
});

router.post('/guardar', async (req, res) => {
  const connection = await db.getConnection();

  try {
    const {
      mes,
      anio,
      distribuidorId,
      totalIncentivo,
      resumenBasesRend,
      resumenPctRend,
      resumenBasesSem,
      resumenPctSem,
      resumenBasesDes,
      resumenPctDes,
      resumenBasesIns,
      resumenPctIns,
      rows
    } = req.body || {};

    const validMes = toInt(mes);
    const validAnio = toInt(anio);
    const validDistribuidorId = toInt(distribuidorId);

    if (!validMes || !validAnio) {
      return res.status(400).json({ message: 'Mes y anio son obligatorios' });
    }

    if (!Array.isArray(rows) || rows.length === 0) {
      return res.status(400).json({ message: 'Se requiere al menos una fila de incentivo individual' });
    }

    await connection.beginTransaction();

    const personalTable = await resolvePersonalTable(connection);
    if (!personalTable) {
      throw new Error('No existe tabla de detalle de incentivos: se esperaba incentivospersonal o incentivosindividuales');
    }

    const detailSql = `
      INSERT INTO ${personalTable} (
        incentivos_idIncentivo,
        TotalIncentivoIndividual,
        RBaseIncentivoIndividual,
        RPorcenIncentivoIndividual,
        RPorcenCumplidoIncentivoIndividual,
        RPagoIncentivoIndividual,
        SBaseIncentivoIndividual,
        SPorcenIncentivoIndividual,
        SPorcenCumplidoIncentivoIndividual,
        SPagoIncentivoIndividual,
        PBaseIncentivoIndividual,
        PPorcenIncentivoIndividual,
        PPorcenCumplidoIncentivoIndividual,
        PPagoIncentivoIndividual,
        IBaseIncentivoIndividual,
        IPorcenIncentivoIndividual,
        IPorcenCumplidoIncentivoIndividual,
        IPagoIncentivoIndividual,
        PagoTotalIncentivoIndividual,
        Usuarios_idUsuarios
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `;

    if (validDistribuidorId) {
      const [existingRows] = await connection.query(
        `
        SELECT idIncentivo
        FROM incentivos
        WHERE MesIncentivo = ?
          AND AñoIncentivo = ?
          AND Distribuidor_idDistribuidor = ?
        ORDER BY idIncentivo DESC
        LIMIT 1
        `,
        [validMes, validAnio, validDistribuidorId]
      );

      let incentivoId;

      if (existingRows.length > 0) {
        incentivoId = existingRows[0].idIncentivo;
        await connection.query(
          `
          UPDATE incentivos
          SET
            TotalIncentivo = ?, RBaseIncentivo = ?, RPorcenIncentivo = ?,
            SBaseIncentivo = ?, SPorcenIncentivo = ?, PBaseIncentivo = ?,
            PPorcenIncentivo = ?, IBaseIncentivo = ?, IPorcenIncentivo = ?
          WHERE idIncentivo = ?
          `,
          [
            toNumber(totalIncentivo), toInt(resumenBasesRend), toNumber(resumenPctRend),
            toInt(resumenBasesSem), toNumber(resumenPctSem), toInt(resumenBasesDes),
            toNumber(resumenPctDes), toInt(resumenBasesIns), toNumber(resumenPctIns),
            incentivoId
          ]
        );

        await connection.query(`DELETE FROM ${personalTable} WHERE incentivos_idIncentivo = ?`, [incentivoId]);
      } else {
        const [insertResult] = await connection.query(
          `
          INSERT INTO incentivos (
            MesIncentivo, AñoIncentivo, Distribuidor_idDistribuidor,
            TotalIncentivo, RBaseIncentivo, RPorcenIncentivo,
            SBaseIncentivo, SPorcenIncentivo, PBaseIncentivo,
            PPorcenIncentivo, IBaseIncentivo, IPorcenIncentivo
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `,
          [
            validMes, validAnio, validDistribuidorId,
            toNumber(totalIncentivo), toInt(resumenBasesRend), toNumber(resumenPctRend),
            toInt(resumenBasesSem), toNumber(resumenPctSem), toInt(resumenBasesDes),
            toNumber(resumenPctDes), toInt(resumenBasesIns), toNumber(resumenPctIns)
          ]
        );
        incentivoId = insertResult.insertId;
      }

      for (const row of rows) {
        await connection.query(detailSql, [
          incentivoId, toNumber(row.baseIncentivo), toInt(row.basesRend), toNumber(row.pctRend),
          toNumber(row.pctCumplidoRend), toNumber(row.pagoRend), toInt(row.basesSem),
          toNumber(row.pctSem), toNumber(row.pctCumplidoSem), toNumber(row.pagoSem),
          toInt(row.basesDes), toNumber(row.pctDes), toNumber(row.pctCumplidoDes),
          toNumber(row.pagoDes), toInt(row.basesIns), toNumber(row.pctIns),
          toNumber(row.pctCumplidoIns), toNumber(row.pagoIns), toNumber(row.pagoTotal),
          toInt(row.usuarioId, null)
        ]);
      }
    } else {
      // Global Save: group by distribuidorId
      const byDist = new Map();
      for (const row of rows) {
        const dId = toInt(row.distribuidorId);
        if (!dId) continue;
        if (!byDist.has(dId)) byDist.set(dId, []);
        byDist.get(dId).push(row);
      }

      for (const [dId, distRows] of byDist.entries()) {
        const [existingRows] = await connection.query(
          `
          SELECT idIncentivo
          FROM incentivos
          WHERE MesIncentivo = ?
            AND AñoIncentivo = ?
            AND Distribuidor_idDistribuidor = ?
          ORDER BY idIncentivo DESC
          LIMIT 1
          `,
          [validMes, validAnio, dId]
        );

        let incentivoId;
        const existingUserIds = new Set();

        if (existingRows.length > 0) {
          incentivoId = existingRows[0].idIncentivo;
          await connection.query(
            `
            UPDATE incentivos
            SET
              TotalIncentivo = ?, RBaseIncentivo = ?, RPorcenIncentivo = ?,
              SBaseIncentivo = ?, SPorcenIncentivo = ?, PBaseIncentivo = ?,
              PPorcenIncentivo = ?, IBaseIncentivo = ?, IPorcenIncentivo = ?
            WHERE idIncentivo = ?
            `,
            [
              toNumber(totalIncentivo), toInt(resumenBasesRend), toNumber(resumenPctRend),
              toInt(resumenBasesSem), toNumber(resumenPctSem), toInt(resumenBasesDes),
              toNumber(resumenPctDes), toInt(resumenBasesIns), toNumber(resumenPctIns),
              incentivoId
            ]
          );

          // Find existing users instead of deleting
          const [existingPersonal] = await connection.query(
            `SELECT Usuarios_idUsuarios FROM ${personalTable} WHERE incentivos_idIncentivo = ?`,
            [incentivoId]
          );
          existingPersonal.forEach(r => existingUserIds.add(Number(r.Usuarios_idUsuarios)));
        } else {
          const [insertResult] = await connection.query(
            `
            INSERT INTO incentivos (
              MesIncentivo, AñoIncentivo, Distribuidor_idDistribuidor,
              TotalIncentivo, RBaseIncentivo, RPorcenIncentivo,
              SBaseIncentivo, SPorcenIncentivo, PBaseIncentivo,
              PPorcenIncentivo, IBaseIncentivo, IPorcenIncentivo
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `,
            [
              validMes, validAnio, dId,
              toNumber(totalIncentivo), toInt(resumenBasesRend), toNumber(resumenPctRend),
              toInt(resumenBasesSem), toNumber(resumenPctSem), toInt(resumenBasesDes),
              toNumber(resumenPctDes), toInt(resumenBasesIns), toNumber(resumenPctIns)
            ]
          );
          incentivoId = insertResult.insertId;
        }

        // Only insert non-existing users
        for (const row of distRows) {
          const uId = toInt(row.usuarioId, null);
          if (uId && existingUserIds.has(uId)) {
            continue;
          }
          await connection.query(detailSql, [
            incentivoId, toNumber(row.baseIncentivo), toInt(row.basesRend), toNumber(row.pctRend),
            toNumber(row.pctCumplidoRend), toNumber(row.pagoRend), toInt(row.basesSem),
            toNumber(row.pctSem), toNumber(row.pctCumplidoSem), toNumber(row.pagoSem),
            toInt(row.basesDes), toNumber(row.pctDes), toNumber(row.pctCumplidoDes),
            toNumber(row.pagoDes), toInt(row.basesIns), toNumber(row.pctIns),
            toNumber(row.pctCumplidoIns), toNumber(row.pagoIns), toNumber(row.pagoTotal),
            uId
          ]);
        }
      }
    }

    await connection.commit();

    res.status(201).json({
      message: validDistribuidorId ? 'Incentivos guardados correctamente' : 'Incentivos guardados correctamente a todos los usuarios sin registro previo',
      totalFilas: rows.length
    });
  } catch (error) {
    await connection.rollback();
    handleServerError(res, 'Error al guardar incentivos', error);
  } finally {
    connection.release();
  }
});

module.exports = router;
