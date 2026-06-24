-- Añadir columnas faltantes a llantas_terminadas si no existen
ALTER TABLE llantas_terminadas
  ADD COLUMN IF NOT EXISTS LlantasVehiculosPresion FLOAT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosMM1 FLOAT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosMM2 FLOAT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosMM3 FLOAT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosMM4 FLOAT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosPiso VARCHAR(100) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS LlantasVehiculosFechaInicio DATETIME DEFAULT NULL;

-- Asegurarse de que existan índices que pueden ser útiles
ALTER TABLE llantas_terminadas
  ADD INDEX IF NOT EXISTS idx_llantasvehiculos_id (LlantasVehiculos_idLlantasVehiculos),
  ADD INDEX IF NOT EXISTS idx_vehiculos_id (Vehiculos_idVehiculos);

-- Nota: si tu MySQL es versión anterior a 8.0 y no soporta IF NOT EXISTS en ADD COLUMN,
-- copia las sentencias sin el `IF NOT EXISTS` o ejecuta primero un SHOW COLUMNS para verificar.
