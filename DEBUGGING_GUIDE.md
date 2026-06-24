# Debugging: Cómo Verificar que los Cambios Funcionan

## 1. Verificar Orden de Rutas en el Backend

Ejecuta en PowerShell para verificar que `/batch` está ANTES que `/`:

```powershell
cd c:\yokohamareferencia\YokohamaBackend
grep -n "router.post" routes/llantasInspeccion.js
```

Deberías ver:
```
117: router.post('/batch', async (req, res) => {
210: router.post('/', async (req, res) => {
```

## 2. Habilitar Logging en el Backend

Agrega logs para ver qué ruta se ejecuta:

En `routes/llantasInspeccion.js`, agrega al inicio del archivo:

```javascript
// Agregar después de los imports
router.use((req, res, next) => {
    if (req.method === 'POST') {
        console.log(`🔍 POST recibido: ${req.originalUrl}`);
        console.log(`📦 Body type: ${Array.isArray(req.body) ? 'ARRAY' : 'OBJECT'}`);
        console.log(`📊 Body length: ${Array.isArray(req.body) ? req.body.length : 'N/A'}`);
    }
    next();
});
```

## 3. Probar el Endpoint Manualmente

### Test 1: Batch (array de 4 llantas)

```bash
curl -X POST http://localhost:3000/api/llantas-inspeccion/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "vehiculosinspeccion_idVehiculoInspeccion": 1,
      "Llantas_idLlantas": 1,
      "LlantasInspeccionMm1": 5.5,
      "LlantasInspeccionMm2": 6.0,
      "LlantasInspeccionMm3": 5.8,
      "LlantasInspeccionMm4": 5.9,
      "LlantasInspeccionPresion": 28.5,
      "LlantasInspeccionCondPel": 0,
      "LlantasInspeccionObservacion": "Test 1",
      "LlantasInspeccionComentario": "Comment 1",
      "LlantasInspeccionDOT": "2023",
      "LlantasInspeccionPiso": "buen estado",
      "LlantasInspeccionDesgaste": "normal"
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
      "LlantasInspeccionObservacion": "Test 2",
      "LlantasInspeccionComentario": "Comment 2",
      "LlantasInspeccionDOT": "2023",
      "LlantasInspeccionPiso": "buen estado",
      "LlantasInspeccionDesgaste": "normal"
    },
    {
      "vehiculosinspeccion_idVehiculoInspeccion": 1,
      "Llantas_idLlantas": 3,
      "LlantasInspeccionMm1": 5.7,
      "LlantasInspeccionMm2": 6.2,
      "LlantasInspeccionMm3": 6.0,
      "LlantasInspeccionMm4": 5.8,
      "LlantasInspeccionPresion": 28.8,
      "LlantasInspeccionCondPel": 0,
      "LlantasInspeccionObservacion": "Test 3",
      "LlantasInspeccionComentario": "Comment 3",
      "LlantasInspeccionDOT": "2023",
      "LlantasInspeccionPiso": "buen estado",
      "LlantasInspeccionDesgaste": "normal"
    },
    {
      "vehiculosinspeccion_idVehiculoInspeccion": 1,
      "Llantas_idLlantas": 4,
      "LlantasInspeccionMm1": 5.6,
      "LlantasInspeccionMm2": 6.3,
      "LlantasInspeccionMm3": 6.1,
      "LlantasInspeccionMm4": 5.9,
      "LlantasInspeccionPresion": 29.1,
      "LlantasInspeccionCondPel": 0,
      "LlantasInspeccionObservacion": "Test 4",
      "LlantasInspeccionComentario": "Comment 4",
      "LlantasInspeccionDOT": "2023",
      "LlantasInspeccionPiso": "buen estado",
      "LlantasInspeccionDesgaste": "normal"
    }
  ]'
```

**Respuesta esperada:**
```json
{
  "message": "4 llantas de inspección creadas correctamente",
  "results": [
    { "id": 101 },
    { "id": 102 },
    { "id": 103 },
    { "id": 104 }
  ]
}
```

### Test 2: Individual (una sola llanta)

```bash
curl -X POST http://localhost:3000/api/llantas-inspeccion \
  -H "Content-Type: application/json" \
  -d '{
    "vehiculosinspeccion_idVehiculoInspeccion": 1,
    "Llantas_idLlantas": 5,
    "LlantasInspeccionMm1": 5.5,
    "LlantasInspeccionMm2": 6.0,
    "LlantasInspeccionMm3": 5.8,
    "LlantasInspeccionMm4": 5.9,
    "LlantasInspeccionPresion": 28.5,
    "LlantasInspeccionCondPel": 0,
    "LlantasInspeccionObservacion": "Test",
    "LlantasInspeccionComentario": "Comment",
    "LlantasInspeccionDOT": "2023",
    "LlantasInspeccionPiso": "buen estado",
    "LlantasInspeccionDesgaste": "normal"
  }'
```

## 4. Verificar en la Base de Datos

Después de ejecutar los tests, verifica que se guardaron:

```sql
SELECT COUNT(*) as total FROM llantasinspeccion;

-- O más detallado:
SELECT 
    idLlantasInspeccion,
    vehiculosinspeccion_idVehiculoInspeccion,
    Llantas_idLlantas,
    LlantasInspeccionMm1
FROM llantasinspeccion
ORDER BY idLlantasInspeccion DESC
LIMIT 10;
```

## 5. Verificar el Frontend

En la aplicación Kotlin, busca en los logs (Logcat):
```
Datos recibidos en /api/llantas-inspeccion:
```

Deberías ver que se envía un array, no un objeto individual.

## 6. Si Todavía no Funciona

### Opciones a verificar:

1. **¿El servidor está reiniciado?**
   ```powershell
   # Detén el servidor
   Ctrl+C
   
   # Inicia nuevamente
   npm start
   ```

2. **¿El cliente (app) está enviando un array?**
   - Revisa en `ApiClient.kt` la función `createMultipleLlantasInspeccion`
   - Verifica que realmente llama a `/batch`

3. **¿Hay un middleware que modifican las rutas?**
   - Revisa `index.js` para ver si hay algo que interfiera
   - Busca `bodyParser` o middleware personalizado

4. **¿La BD está actualizándose?**
   - A veces hay caché o transacciones no completadas
   - Verifica con `SELECT COUNT(*) FROM llantasinspeccion;`

## Comandos Útiles

```powershell
# Ver si el servidor está corriendo
netstat -ano | findstr :3000

# Mostrar últimas 20 líneas del archivo (para ver logs)
Get-Content c:\yokohamareferencia\YokohamaBackend\routes\llantasInspeccion.js -Tail 20

# Verificar sintaxis de JavaScript
node -c c:\yokohamareferencia\YokohamaBackend\routes\llantasInspeccion.js
```
