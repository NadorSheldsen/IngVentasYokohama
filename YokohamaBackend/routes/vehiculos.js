const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { handleServerError } = require('../utils/responseUtils');
const { processImageToBase64WithinLimit } = require('../utils/imageProcessor');

// GET all vehiculos with related data and latest km from inspection history
router.get('/', async (req, res) => {
  try {
    console.log('[vehiculos GET /] Starting query for all vehicles with km calculation');
    const [rows] = await db.query(`
      SELECT 
        v.*, 
        TO_BASE64(v.VehiculosImagen) AS VehiculosImagen, 
        f.FlotasNombre, 
        t.TipoVehiculosNombre,
        COALESCE(
          GREATEST(
            COALESCE(pr.PruebaRendimientoOdometro, 0) - COALESCE(
              (SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = v.idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1),
              COALESCE(v.VehiculosOdometro, 0)
            ),
            0
          ),
          0
        ) AS kmRecorridoLatest
      FROM vehiculos v
      LEFT JOIN flotas f ON v.Flotas_idFlotas = f.idFlotas
      LEFT JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
      LEFT JOIN pruebarendimiento pr ON v.idVehiculos = pr.Vehiculos_idVehiculos
        AND pr.idPruebaRendimiento = (
          SELECT MAX(idPruebaRendimiento) FROM pruebarendimiento 
          WHERE Vehiculos_idVehiculos = v.idVehiculos
        )
      ORDER BY v.idVehiculos
    `);
    console.log(`[vehiculos GET /] Query returned ${rows.length} rows. First row keys:`, Object.keys(rows[0] || {}));
    if (rows.length > 0) {
      console.log(`[vehiculos GET /] First row kmRecorridoLatest value:`, rows[0].kmRecorridoLatest);
    }
    // Ensure any binary/image-like columns are returned as base64 strings
    const processed = rows.map(r => {
      try {
        Object.keys(r).forEach(key => {
          const low = String(key).toLowerCase();
          if (low.includes('vehiculos') && low.includes('imagen')) {
            try {
              const val = r[key];
              let b64 = null;
              if (Buffer.isBuffer(val)) {
                b64 = val.toString('base64');
                console.log(`[vehiculos] converted buffer for key=${key}, length=${b64.length}`);
              } else if (typeof val === 'object' && val && Array.isArray(val.data)) {
                b64 = Buffer.from(val.data).toString('base64');
                console.log(`[vehiculos] converted object.data for key=${key}, length=${b64.length}`);
              }
              // set canonical property name expected by the client
              if (b64 !== null) {
                r['VehiculosImagen'] = b64;
              }
            } catch (e) {
              // ignore conversion error for specific key
            }
          }
        });
      } catch (e) {
        // ignore conversion errors, leave original
      }
      return r;
    });
    console.log(`[vehiculos GET /] Final processed response - ${processed.length} vehicles. First vehicle has kmRecorridoLatest:`, processed[0]?.kmRecorridoLatest);
    res.json(processed);
  } catch (error) {
    handleServerError(res, 'Error al obtener los vehículos', error);
  }
});

// GET a specific vehiculo by ID with latest km from inspection history
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT 
        v.*, 
        TO_BASE64(v.VehiculosImagen) AS VehiculosImagen, 
        f.FlotasNombre, 
        t.TipoVehiculosNombre,
        COALESCE(
          GREATEST(
            COALESCE(pr.PruebaRendimientoOdometro, 0) - COALESCE(
              (SELECT pr2.PruebaRendimientoOdometro FROM pruebarendimiento pr2
               WHERE pr2.Vehiculos_idVehiculos = v.idVehiculos
                 AND pr2.idPruebaRendimiento < pr.idPruebaRendimiento
               ORDER BY pr2.idPruebaRendimiento DESC LIMIT 1),
              COALESCE(v.VehiculosOdometro, 0)
            ),
            0
          ),
          0
        ) AS kmRecorridoLatest
      FROM vehiculos v
      LEFT JOIN flotas f ON v.Flotas_idFlotas = f.idFlotas
      LEFT JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
      LEFT JOIN pruebarendimiento pr ON v.idVehiculos = pr.Vehiculos_idVehiculos
        AND pr.idPruebaRendimiento = (
          SELECT MAX(idPruebaRendimiento) FROM pruebarendimiento 
          WHERE Vehiculos_idVehiculos = v.idVehiculos
        )
      WHERE v.idVehiculos = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Vehículo no encontrado' });
    }
    console.log(`[vehiculos GET /:id] Fetched vehicle ${req.params.id}. Query returned kmRecorridoLatest:`, rows[0]?.kmRecorridoLatest);
    // Convert image buffer to base64 if necessary
    const row = rows[0];
    try {
      Object.keys(row).forEach(key => {
        const low = String(key).toLowerCase();
        if (low.includes('vehiculos') && low.includes('imagen')) {
          const val = row[key];
          try {
            let b64 = null;
            if (Buffer.isBuffer(val)) {
              b64 = val.toString('base64');
              console.log(`[vehiculos] converted buffer for single row key=${key}, length=${b64.length}`);
            } else if (typeof val === 'object' && val && Array.isArray(val.data)) {
              b64 = Buffer.from(val.data).toString('base64');
              console.log(`[vehiculos] converted object.data for single row key=${key}, length=${b64.length}`);
            }
            if (b64 !== null) {
              row['VehiculosImagen'] = b64;
            }
          } catch (e) {
            // ignore conversion errors
          }
        }
      });
    } catch (e) {
      // ignore
    }

    console.log(`[vehiculos GET /:id] Final response for vehicle ${req.params.id} has kmRecorridoLatest:`, row.kmRecorridoLatest);
    res.json(row);
  } catch (error) {
    handleServerError(res, 'Error al obtener el vehículo', error);
  }
});

// POST a new vehiculo
router.post('/', async (req, res) => {
  try {
    let { 
      Flotas_idFlotas, 
      VehiculosNumero, 
      TipoVehiculos_idTipoVehiculos, 
      VehiculosOdometro, 
      VehiculosImagen 
    } = req.body;

    // Procesar imagen si existe
    if (VehiculosImagen) {
      try {
        const originalSize = (typeof VehiculosImagen === 'string' ? VehiculosImagen.length : VehiculosImagen.length);
        VehiculosImagen = await processImageToBase64WithinLimit(VehiculosImagen, {
          maxWidth: 800,
          maxHeight: 600,
          quality: 80,
          maxBase64Length: 60000
        });
        const processedSize = VehiculosImagen.length;
        console.log(`[vehiculos] Imagen procesada - Original: ${originalSize} bytes, Procesada: ${processedSize} bytes, Reducción: ${Math.round((1 - processedSize / originalSize) * 100)}%`);
      } catch (error) {
        console.error(`[vehiculos] Error procesando imagen:`, error.message);
        return res.status(400).json({ message: `Error al procesar la imagen: ${error.message}` });
      }
    }
    
    // Prevent duplicate vehicle number within the same flota
    const [existing] = await db.query(
      'SELECT idVehiculos FROM vehiculos WHERE Flotas_idFlotas = ? AND VehiculosNumero = ? LIMIT 1',
      [Flotas_idFlotas, VehiculosNumero]
    );
    if (existing && existing.length > 0) {
      const msg = 'Vehículo con ese número ya existe en la flota';
      res.set('X-Error-Message', msg);
      return res.status(409).json({ message: msg });
    }

    const [result] = await db.query(
      'INSERT INTO vehiculos (Flotas_idFlotas, VehiculosNumero, TipoVehiculos_idTipoVehiculos, VehiculosOdometro, VehiculosImagen) VALUES (?, ?, ?, ?, ?)',
      [Flotas_idFlotas, VehiculosNumero, TipoVehiculos_idTipoVehiculos, VehiculosOdometro, VehiculosImagen]
    );

    // If the schema includes VehiculosOdometroRegistro, set it to the initial odometer value
    // for the newly created vehicle. Wrap in try/catch so code works even when column is absent.
    try {
      await db.query(
        'UPDATE vehiculos SET VehiculosOdometroRegistro = ? WHERE idVehiculos = ? AND (VehiculosOdometroRegistro IS NULL OR VehiculosOdometroRegistro = 0)',
        [VehiculosOdometro, result.insertId]
      );
    } catch (e) {
      // ignore: column may not exist in older schemas
    }

    // Try to return the freshly created vehiculo so clients can deserialize directly
    try {
      const [rows] = await db.query(`
        SELECT v.*, TO_BASE64(v.VehiculosImagen) AS VehiculosImagen, f.FlotasNombre, t.TipoVehiculosNombre 
        FROM vehiculos v
        JOIN flotas f ON v.Flotas_idFlotas = f.idFlotas
        JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos
        WHERE v.idVehiculos = ?
      `, [result.insertId]);

      if (rows.length === 0) {
        return res.status(201).json({ message: 'Vehiculo created', id: result.insertId });
      }

      // Ensure image field is base64 string if necessary (same processing as GET /:id)
      const row = rows[0];
      try {
        Object.keys(row).forEach(key => {
          const low = String(key).toLowerCase();
          if (low.includes('vehiculos') && low.includes('imagen')) {
            const val = row[key];
            try {
              let b64 = null;
              if (Buffer.isBuffer(val)) {
                b64 = val.toString('base64');
              } else if (typeof val === 'object' && val && Array.isArray(val.data)) {
                b64 = Buffer.from(val.data).toString('base64');
              }
              if (b64 !== null) {
                row['VehiculosImagen'] = b64;
              }
            } catch (e) {
              // ignore conversion errors
            }
          }
        });
      } catch (e) {
        // ignore
      }

      return res.status(201).json(row);
    } catch (fetchError) {
      console.error('Error fetching created vehiculo:', fetchError);
      return res.status(201).json({ message: 'Vehiculo created', id: result.insertId });
    }
  } catch (error) {
    handleServerError(res, 'Error al crear el vehículo', error);
  }
});

// PUT/UPDATE a vehiculo
router.put('/:id', async (req, res) => {
  try {
    let { 
      Flotas_idFlotas, 
      VehiculosNumero, 
      TipoVehiculos_idTipoVehiculos, 
      VehiculosOdometro, 
      VehiculosImagen 
    } = req.body;

    const [currentRows] = await db.query(
      'SELECT Flotas_idFlotas, VehiculosNumero, TipoVehiculos_idTipoVehiculos, VehiculosOdometro, VehiculosImagen FROM vehiculos WHERE idVehiculos = ?',
      [req.params.id]
    );

    if (currentRows.length === 0) {
      return res.status(404).json({ message: 'Vehículo no encontrado' });
    }

    const current = currentRows[0];

    // Partial update safety: keep current DB values when a field is not provided.
    Flotas_idFlotas = Flotas_idFlotas ?? current.Flotas_idFlotas;
    VehiculosNumero = VehiculosNumero ?? current.VehiculosNumero;
    TipoVehiculos_idTipoVehiculos = TipoVehiculos_idTipoVehiculos ?? current.TipoVehiculos_idTipoVehiculos;
    VehiculosOdometro = VehiculosOdometro ?? current.VehiculosOdometro;

    // Procesar imagen si existe
    if (VehiculosImagen) {
      try {
        const originalSize = (typeof VehiculosImagen === 'string' ? VehiculosImagen.length : VehiculosImagen.length);
        VehiculosImagen = await processImageToBase64WithinLimit(VehiculosImagen, {
          maxWidth: 800,
          maxHeight: 600,
          quality: 80,
          maxBase64Length: 60000
        });
        const processedSize = VehiculosImagen.length;
        console.log(`[vehiculos] Imagen procesada (UPDATE) - Original: ${originalSize} bytes, Procesada: ${processedSize} bytes, Reducción: ${Math.round((1 - processedSize / originalSize) * 100)}%`);
      } catch (error) {
        console.error(`[vehiculos] Error procesando imagen:`, error.message);
        return res.status(400).json({ message: `Error al procesar la imagen: ${error.message}` });
      }
    } else if (typeof VehiculosImagen === 'undefined') {
      VehiculosImagen = current.VehiculosImagen;
    }
    
    const [result] = await db.query(
      'UPDATE vehiculos SET Flotas_idFlotas = ?, VehiculosNumero = ?, TipoVehiculos_idTipoVehiculos = ?, VehiculosOdometro = ?, VehiculosImagen = ? WHERE idVehiculos = ?',
      [Flotas_idFlotas, VehiculosNumero, TipoVehiculos_idTipoVehiculos, VehiculosOdometro, VehiculosImagen, req.params.id]
    );
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Vehículo no encontrado' });
    }
    
    res.json({ message: 'Vehículo actualizado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al actualizar el vehículo', error);
  }
});

// PUT: set/clear the VehiculosPTerminada flag for a vehicle
router.put('/:id/terminada', async (req, res) => {
  try {
    const { VehiculosPTerminada, terminada } = req.body;
    // Accept either numeric VehiculosPTerminada or boolean 'terminada' in the body
    let flag = null;
    if (typeof VehiculosPTerminada !== 'undefined') {
      flag = Number(VehiculosPTerminada) === 1 ? 1 : 0;
    } else if (typeof terminada !== 'undefined') {
      flag = terminada ? 1 : 0;
    } else {
      return res.status(400).json({ message: 'Se debe enviar VehiculosPTerminada o terminada en el cuerpo' });
    }

    const [result] = await db.query('UPDATE vehiculos SET VehiculosPTerminada = ? WHERE idVehiculos = ?', [flag, req.params.id]);
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Vehículo no encontrado' });
    }

    // Return updated row
    const [rows] = await db.query(`SELECT v.*, TO_BASE64(v.VehiculosImagen) AS VehiculosImagen, f.FlotasNombre, t.TipoVehiculosNombre FROM vehiculos v JOIN flotas f ON v.Flotas_idFlotas = f.idFlotas JOIN tipovehiculos t ON v.TipoVehiculos_idTipoVehiculos = t.idTipoVehiculos WHERE v.idVehiculos = ?`, [req.params.id]);
    if (rows.length === 0) return res.json({ message: 'Vehículo actualizado' });
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al actualizar VehiculosPTerminada', error);
  }
});

// DELETE a vehiculo
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM vehiculos WHERE idVehiculos = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Vehículo no encontrado' });
    }
    
    res.json({ message: 'Vehículo eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el vehículo', error);
  }
});

// GET all llantas for a specific vehiculo
router.get('/:id/llantas', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT lv.*, l.LlantasMarca, l.LlantasModelo, l.LlantasMedida
      FROM llantasvehiculos lv
      JOIN llantas l ON lv.Llantas_idLlantas = l.idLlantas
      WHERE lv.Vehiculos_idVehiculos = ?
    `, [req.params.id]);
    
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las llantas del vehículo', error);
  }
});

module.exports = router;
