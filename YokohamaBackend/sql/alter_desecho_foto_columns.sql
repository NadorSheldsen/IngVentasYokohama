-- Alterar columnas de foto en llantasdesecho para soportar fotos en base64 grandes (hasta 16MB)
ALTER TABLE llantasdesecho
  MODIFY COLUMN LlantasDesechoFoto1 MEDIUMTEXT DEFAULT NULL,
  MODIFY COLUMN LlantasDesechoFoto2 MEDIUMTEXT DEFAULT NULL;

-- Verificación
SHOW CREATE TABLE llantasdesecho;
