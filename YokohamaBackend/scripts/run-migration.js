const fs = require('fs');
const path = require('path');
const db = require('../config/database');

(async () => {
  try {
    const conn = await db.getConnection();
    try {
      // Ensure table exists: if not, attempt to create from create_llantas_terminadas.sql
      const [tables] = await conn.query("SHOW TABLES LIKE 'llantas_terminadas'");
      if (!tables || tables.length === 0) {
        const createPath = path.join(__dirname, '..', 'sql', 'create_llantas_terminadas.sql');
        if (fs.existsSync(createPath)) {
          console.log('Tabla llantas_terminadas no existe. Creando desde:', createPath);
          const createSql = fs.readFileSync(createPath, 'utf8');
          const stmts = createSql.split(/;\s*\n/).map(s => s.trim()).filter(s => s.length > 0);
          for (const s of stmts) {
            console.log('Ejecutando:', s.substring(0, 120).replace(/\n/g, ' '));
            await conn.query(s);
          }
        } else {
          throw new Error('No se encontró create_llantas_terminadas.sql para crear la tabla.');
        }
      }

      // Helper to check and add a column if missing
      async function ensureColumn(columnName, columnDef) {
        const [cols] = await conn.query("SHOW COLUMNS FROM llantas_terminadas LIKE ?", [columnName]);
        if (!cols || cols.length === 0) {
          console.log(`Agregando columna ${columnName} ${columnDef}`);
          await conn.query(`ALTER TABLE llantas_terminadas ADD COLUMN ${columnName} ${columnDef}`);
        } else {
          console.log(`Columna ${columnName} ya existe, omitiendo.`);
        }
      }

      await ensureColumn('LlantasVehiculosPresion', 'FLOAT NULL');
      await ensureColumn('LlantasVehiculosMM1', 'FLOAT NULL');
      await ensureColumn('LlantasVehiculosMM2', 'FLOAT NULL');
      await ensureColumn('LlantasVehiculosMM3', 'FLOAT NULL');
      await ensureColumn('LlantasVehiculosMM4', 'FLOAT NULL');
      await ensureColumn('LlantasVehiculosPiso', "VARCHAR(100) NULL");
      await ensureColumn('LlantasVehiculosFechaInicio', 'DATETIME NULL');

      // Ensure indexes
      const [idx] = await conn.query("SHOW INDEX FROM llantas_terminadas WHERE Key_name = 'idx_llantasvehiculos_id' LIMIT 1");
      if (!idx || idx.length === 0) {
        try {
          await conn.query('ALTER TABLE llantas_terminadas ADD INDEX idx_llantasvehiculos_id (LlantasVehiculos_idLlantasVehiculos)');
        } catch (e) { console.log('Warning adding index idx_llantasvehiculos_id:', e.message); }
      }
      const [idx2] = await conn.query("SHOW INDEX FROM llantas_terminadas WHERE Key_name = 'idx_vehiculos_id' LIMIT 1");
      if (!idx2 || idx2.length === 0) {
        try {
          await conn.query('ALTER TABLE llantas_terminadas ADD INDEX idx_vehiculos_id (Vehiculos_idVehiculos)');
        } catch (e) { console.log('Warning adding index idx_vehiculos_id:', e.message); }
      }

      console.log('Migración condicional completada correctamente.');
    } finally {
      conn.release();
    }
    process.exit(0);
  } catch (err) {
    console.error('Error ejecutando migración:', err);
    process.exit(1);
  }
})();
