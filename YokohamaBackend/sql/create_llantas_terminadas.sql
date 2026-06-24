-- DDL para guardar llantas retiradas y su historial (Opción B)

CREATE TABLE IF NOT EXISTS llantas_terminadas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  LlantasVehiculos_idLlantasVehiculos INT NOT NULL,
  Vehiculos_idVehiculos INT,
  Llantas_idLlantas INT,
  LlantasVehiculosNoQuemado VARCHAR(255),
  LlantasVehiculosPresion FLOAT,
  LlantasVehiculosMM1 FLOAT,
  LlantasVehiculosMM2 FLOAT,
  LlantasVehiculosMM3 FLOAT,
  LlantasVehiculosMM4 FLOAT,
  LlantasVehiculosPiso VARCHAR(100),
  FechaRetiro DATETIME DEFAULT CURRENT_TIMESTAMP,
  Usuario_idUsuarios INT NULL,
  Causa TEXT NULL,
  ReplacedBy_idLlantasVehiculos INT NULL,
  Snapshot JSON NULL,
  INDEX (LlantasVehiculos_idLlantasVehiculos),
  INDEX (Vehiculos_idVehiculos)
);

CREATE TABLE IF NOT EXISTS llantas_terminadas_rendimientos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  LlantasTerminadas_id INT NOT NULL,
  idLlantasRendimiento INT,
  PruebaRendimiento_idPruebaRendimiento INT,
  LlantasRendimientoMm1 FLOAT,
  LlantasRendimientoMm2 FLOAT,
  LlantasRendimientoMm3 FLOAT,
  LlantasRendimientoMm4 FLOAT,
  LlantasRendimientoPresion FLOAT,
  LlantasRendimientoComent TEXT,
  FechaRegistro DATETIME,
  FOREIGN KEY (LlantasTerminadas_id) REFERENCES llantas_terminadas(id) ON DELETE CASCADE,
  INDEX (LlantasTerminadas_id)
);
