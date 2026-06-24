# Ejemplos Detallados: Antes y Después

## Ejemplo 1: PruebaInspeccionReportScreen.kt

### ANTES (Línea ~248):
```kotlin
errorMessage != null -> {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text(errorMessage ?: "Error")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            coroutineScope.launch {
                isLoading = true
                errorMessage = null
                repository.getVehiculosInspeccionByPruebaId(prueba.idPruebaInspeccion)
                    .onSuccess { list ->
                        vehiculos = list
                        // ... más lógica ...
                        isLoading = false
                    }
                    .onFailure { err ->
                        errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos")
                        isLoading = false
                    }
            }
        }) {
            Text("Reintentar")
        }
    }
}
```

### PASO 1: Agregar Import
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorScreen
```

### PASO 2: Refactorizar Lógica (Crear función suspend)
Crear una función reutilizable al inicio del Composable:

```kotlin
suspend fun fetchVehiculosInspeccion() {
    isLoading = true
    errorMessage = null
    repository.getVehiculosInspeccionByPruebaId(prueba.idPruebaInspeccion)
        .onSuccess { list ->
            vehiculos = list
            val deferred = list.map { v ->
                coroutineScope.async { 
                    repository.getLlantasInspeccionByVehiculoId(v.idVehiculoInspeccion).getOrThrow() 
                }
            }
            val results = deferred.awaitAll()
            val map = list.mapIndexed { idx, v -> v.idVehiculoInspeccion to results[idx] }.toMap()
            llantasPorVehiculo = map
            // fetch llanta catalog
            repository.getLlantasByFlota(flota.idFlotas)
                .onSuccess { catalog -> llantaCatalog = catalog }
                .onFailure { }
            // fetch parametros
            repository.getParametrosByFlotaId(flota.idFlotas)
                .onSuccess { params -> parametros = params }
                .onFailure { }
            isLoading = false
        }
        .onFailure { err ->
            errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos")
            isLoading = false
        }
}
```

### PASO 3: Reemplazar en el when statement
DESPUÉS:
```kotlin
errorMessage != null -> {
    ErrorScreen(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchVehiculosInspeccion() } }
    )
}
```

---

## Ejemplo 2: UsuariosScreen.kt

### ANTES (Línea ~191):
```kotlin
errorMessage != null -> {
    Text(
        text = errorMessage!!,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.align(Alignment.Center).padding(16.dp)
    )
}
```

### PASO 1: Agregar Import
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorCard
```

### PASO 2: Crear función suspend si no existe
```kotlin
suspend fun fetchUsuarios() {
    isLoading = true
    errorMessage = null
    
    repository.getAllUsuarios()
        .onSuccess { result ->
            usuarios = result
            isLoading = false
        }
        .onFailure { error ->
            isLoading = false
            errorMessage = ErrorUtils.userMessage(error, "Error cargando usuarios")
        }
}
```

### PASO 3: Reemplazar el error inline
DESPUÉS (dentro del Box que contiene los usuario):
```kotlin
errorMessage != null -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorCard(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchUsuarios() } }
        )
    }
}
```

---

## Ejemplo 3: LlantasVehiculoScreen.kt (Pantalla Completa)

### Estructura típica:
```kotlin
Scaffold(...) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        when {
            isLoading && llantas.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            
            // ← AGREGAR ErrorScreen aquí
            errorMessage != null -> {
                ErrorScreen(
                    errorMessage = errorMessage!!,
                    onRetry = { coroutineScope.launch { fetchLlantas() } }
                )
            }
            
            llantas.isEmpty() -> {
                Text("No hay llantas", modifier = Modifier.align(Alignment.Center))
            }
            
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(llantas) { llanta ->
                        LlantaCard(llanta)
                    }
                }
            }
        }
    }
}
```

---

## Guía Rápida: Decidir ErrorScreen vs ErrorCard

### ❌ **NO usar ErrorScreen si...**
- Hay un SearchBar/TextField encima
- Hay un FAB (FloatingActionButton) responsivo
- La pantalla tiene múltiples secciones

✅ **USA ErrorCard en estos casos**

### ✅ **USA ErrorScreen si...**
- El error ocupa TODA la pantalla
- Es una pantalla de solo "lista" o "datos"
- No hay otros controles visibles

---

## Testing del Componente

Para probar que funciona, puedes:

1. **Desconectar WiFi** - Hará que las llamadas API fallen
2. **Usar un dato inválido** - Fuerza mensaje de error del backend
3. **Mock el error en el repositorio** - Para testing local

### Ejemplo de Mock (Testing):
```kotlin
// En un test, puedes mockear:
every { repository.getVehiculos() } returns Result.failure(
    Exception("No se pudo conectar al servidor. Revisa tu conexión.")
)
```

---

## Validación: Checklist Final

- [ ] ¿Importé ErrorScreen o ErrorCard?
- [ ] ¿Creé la función `suspend fun fetch...()`?
- [ ] ¿Reemplacé TODO el bloque anterior de error?
- [ ] ¿Reseteo `errorMessage = null` al inicio?
- [ ] ¿La función retry llama a coroutineScope.launch?
- [ ] ¿Compiló sin errores?
- [ ] ¿El botón "Reintentar" funciona?

---

## Errores Comunes

### ❌ Error: "Unresolved reference: ErrorScreen"
**Solución:** Asegúrate de agregar el import correcto
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorScreen
```

### ❌ Error: "coroutineScope not available"
**Solución:** Crea el coroutineScope al inicio del Composable
```kotlin
val coroutineScope = rememberCoroutineScope()
```

### ❌ El botón "Reintentar" no hace nada
**Solución:** Asegúrate de que la función `fetchData()` es `suspend`
```kotlin
suspend fun fetchData() {  // ← Must be suspend
    // ...
}
```

### ❌ Mensaje de error muy largo o técnico
**Solución:** `ErrorUtils.userMessage()` debería convertirlo. Si no:
```kotlin
errorMessage = ErrorUtils.userMessage(error, "Error por defecto")
//                                             ↑ fallback message
```

---

**Archivo relacionado:** `ErrorScreen.kt`  
**Documentación:** `ERROR_SCREEN_GUIDE.md`  
**Última actualización:** 2024
