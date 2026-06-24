# Solución: Pantalla de Inspección Vehicular

## Problema Identificado
El endpoint de guardar llantas de inspección (`/api/llantas-inspeccion`) tenía un error en el INSERT SQL donde se estaba intentando insertar un campo `LlantasInspeccionNoEco` que **no estaba siendo enviado por el cliente** y aparentemente no existe en la tabla `llantasinspeccion`.

## Cambios Realizados

### 1. Archivo: `routes/llantasInspeccion.js` - POST endpoint (simple)

**Problema:** 
- Línea 153: Se incluía `LlantasInspeccionNoEco` en el INSERT
- Línea 179: Se pasaba `LlantasInspeccionNoEco || null` en los parámetros
- Campo no estaba en el destructuring de `req.body`

**Solución:**
- Removido el campo `LlantasInspeccionNoEco` del INSERT
- Actualizado a 15 parámetros (eliminando 1)
- El SQL ahora coincide exactamente con los campos enviados por el cliente

### 2. Archivo: `routes/llantasInspeccion.js` - POST endpoint (batch)

**Estado:** Ya estaba correctamente implementado después del cambio anterior.

## Flujo de Uso Correcto

### 1. Crear Prueba de Inspección
```
POST /api/pruebas-inspeccion
{
  "PruebaInspeccionTitulo": "Inspección enero 2024",
  "Flotas_idFlotas": 1
}
```

### 2. Crear Vehículos de Inspección
```
POST /api/vehiculos-inspeccion
{
  "pruebasinspeccion_idPruebaInspeccion": 1,
  "TipoVehiculos_idTipoVehiculos": 2,
  "VehiculoInspeccionNo": "VEH001"
}
```

### 3. Crear Llantas de Inspección (Opción A - Individual)
```
POST /api/llantas-inspeccion
{
  "vehiculosinspeccion_idVehiculoInspeccion": 1,
  "Llantas_idLlantas": 1,
  "LlantasInspeccionMm1": 5.5,
  "LlantasInspeccionMm2": 6.0,
  "LlantasInspeccionMm3": 5.8,
  "LlantasInspeccionMm4": 5.9,
  "LlantasInspeccionPresion": 28.5,
  "LlantasInspeccionCondPel": 0,  // 0 = sin peligro, 1 = peligrosa
  "LlantasInspeccionObservacion": "Sin novedad",
  "LlantasInspeccionComentario": "Buen estado",
  "LlantasInspeccionDOT": "2023",
  "LlantasInspeccionPiso": "buen estado",
  "LlantasInspeccionDesgaste": "normal",
  "LlantasInspeccionFoto": "base64_encoded_image_or_null",
  "LlantasInspeccionFoto2": "base64_encoded_image_or_null"
}
```

### 3. Crear Llantas de Inspección (Opción B - Batch/Múltiples)
```
POST /api/llantas-inspeccion/batch
[
  {
    "vehiculosinspeccion_idVehiculoInspeccion": 1,
    "Llantas_idLlantas": 1,
    "LlantasInspeccionMm1": 5.5,
    "LlantasInspeccionMm2": 6.0,
    "LlantasInspeccionMm3": 5.8,
    "LlantasInspeccionMm4": 5.9,
    "LlantasInspeccionPresion": 28.5,
    "LlantasInspeccionCondPel": 0,
    "LlantasInspeccionObservacion": "Sin novedad",
    "LlantasInspeccionComentario": "Buen estado",
    "LlantasInspeccionDOT": "2023",
    "LlantasInspeccionPiso": "buen estado",
    "LlantasInspeccionDesgaste": "normal",
    "LlantasInspeccionFoto": null,
    "LlantasInspeccionFoto2": null
  },
  {
    "vehiculosinspeccion_idVehiculoInspeccion": 1,
    "Llantas_idLlantas": 2,
    "LlantasInspeccionMm1": 6.0,
    "LlantasInspeccionMm2": 6.1,
    "LlantasInspeccionMm3": 5.9,
    "LlantasInspeccionMm4": 6.0,
    "LlantasInspeccionPresion": 29.0,
    "LlantasInspeccionCondPel": 0,
    "LlantasInspeccionObservacion": "Sin novedad",
    "LlantasInspeccionComentario": "Buen estado",
    "LlantasInspeccionDOT": "2023",
    "LlantasInspeccionPiso": "buen estado",
    "LlantasInspeccionDesgaste": "normal",
    "LlantasInspeccionFoto": null,
    "LlantasInspeccionFoto2": null
  }
]
```

## Campos de llantasinspeccion

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| idLlantasInspeccion | INT | Auto | ID único |
| vehiculosinspeccion_idVehiculoInspeccion | INT | Sí | FK a vehiculosinspeccion |
| Llantas_idLlantas | INT | Sí | FK a Llantas |
| LlantasInspeccionMm1 | FLOAT | Sí | Medida 1 en mm |
| LlantasInspeccionMm2 | FLOAT | Sí | Medida 2 en mm |
| LlantasInspeccionMm3 | FLOAT | Sí | Medida 3 en mm |
| LlantasInspeccionMm4 | FLOAT | Sí | Medida 4 en mm |
| LlantasInspeccionPresion | FLOAT | Sí | Presión de aire |
| LlantasInspeccionCondPel | TINYINT | Sí | Condición peligrosa (0/1) |
| LlantasInspeccionObservacion | VARCHAR | No | Observación general |
| LlantasInspeccionComentario | VARCHAR | No | Comentarios adicionales |
| LlantasInspeccionDOT | VARCHAR | No | Código DOT |
| LlantasInspeccionPiso | VARCHAR | No | Condición del piso |
| LlantasInspeccionDesgaste | VARCHAR | No | Tipo de desgaste |
| LlantasInspeccionFoto | LONGBLOB | No | Imagen 1 (base64) |
| LlantasInspeccionFoto2 | LONGBLOB | No | Imagen 2 (base64) |

## Verificación

Para probar los cambios:

1. El cliente (Kotlin/Compose) ya envía correctamente los datos según `LlantaInspeccionCreateRequest`
2. El backend ahora acepta estos datos sin el campo `LlantasInspeccionNoEco`
3. Cada formulario de llanta genera una fila en la tabla `llantasinspeccion`

## Testing

Se ha creado un archivo `test-llantas-inspeccion.js` para pruebas manuales:

```bash
node test-llantas-inspeccion.js
```

Este script prueba:
1. Crear una llanta-inspeccion individual
2. Crear múltiples llantas-inspeccion (batch)
3. Obtener todas las llantas-inspeccion
