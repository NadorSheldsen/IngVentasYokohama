# 🚀 Plan de Acción: Aplicar a Todas las Pantallas

## 📊 Estado Actual del Proyecto

```
┌─────────────────────────────────────────────────┐
│  ErrorScreen Implementation Progress            │
├─────────────────────────────────────────────────┤
│  ✅ Completado:    4 pantallas (17%)            │
│  🔄 Pendiente:    20+ pantallas (83%)          │
│  ⏱️  Tiempo est:   5-10 minutos por pantalla    │
│  📈 Total aprox:   2-3 horas para todas        │
└─────────────────────────────────────────────────┘
```

## ✅ Pantallas Completas

```
✅ VehiculosScreen.kt
   ├─ Import: ErrorScreen
   ├─ Función: fetchVehiculos()
   ├─ Estado: LISTO para uso
   └─ Testing: Desconecta WiFi para probar

✅ PruebasInspeccionListScreen.kt
   ├─ Import: ErrorScreen
   ├─ Función: fetchPruebasInspeccion()
   ├─ Estado: LISTO para uso
   └─ Testing: Desconecta WiFi para probar

✅ PruebasSemaforoListScreen.kt
   ├─ Import: ErrorScreen
   ├─ Función: fetchPruebasSemaforo()
   ├─ Estado: LISTO para uso
   └─ Testing: Desconecta WiFi para probar

✅ ParametrosListScreen.kt
   ├─ Import: ErrorCard
   ├─ Función: loadParametros()
   ├─ Estado: LISTO para uso
   └─ Testing: Desconecta WiFi para probar
```

---

## 🔄 Próximas Pantallas (En Orden de Prioridad)

### 🔴 PRIORITARIAS (Usuarios verán errores frecuentemente)

#### 1. PruebaInspeccionReportScreen.kt
**Ubicación:** `/inspeccion/PruebaInspeccionReportScreen.kt`  
**Línea del error:** ~248  
**Cambios necesarios:** 2 minutos

```kotlin
// Paso 1: Agregar import (línea 2-20)
import com.megatransportes.yokohama.ui.components.ErrorScreen

// Paso 2: Reemplazar error block (línea ~248)
ANTES:
errorMessage != null -> {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text(errorMessage ?: "Error")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            coroutineScope.launch {
                // retry logic aquí
            }
        }) {
            Text("Reintentar")
        }
    }
}

DESPUÉS:
errorMessage != null -> {
    ErrorScreen(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchVehiculosInspeccion() } }
    )
}
```

**Función a crear (línea ~60):**
```kotlin
suspend fun fetchVehiculosInspeccion() {
    isLoading = true
    errorMessage = null
    try {
        repository.getVehiculosInspeccionByPruebaId(prueba.idPruebaInspeccion)
            .onSuccess { list ->
                vehiculos = list
                val deferred = list.map { v ->
                    coroutineScope.async {
                        repository.getLlantasInspeccionByVehiculoId(v.idVehiculoInspeccion).getOrThrow()
                    }
                }
                val results = deferred.awaitAll()
                llantasPorVehiculo = list.mapIndexed { idx, v -> v.idVehiculoInspeccion to results[idx] }.toMap()
                repository.getLlantasByFlota(flota.idFlotas)
                    .onSuccess { catalog -> llantaCatalog = catalog }
                    .onFailure { }
                repository.getParametrosByFlotaId(flota.idFlotas)
                    .onSuccess { params -> parametros = params }
                    .onFailure { }
                isLoading = false
            }
            .onFailure { err ->
                errorMessage = ErrorUtils.userMessage(err, "Error cargando vehículos")
                isLoading = false
            }
    } catch (e: Exception) {
        errorMessage = ErrorUtils.userMessage(e, "Error inesperado")
        isLoading = false
    }
}
```

✅ **Tiempo estimado:** 3 minutos

---

#### 2. PruebaSemaforoReportScreen.kt
**Ubicación:** `/semaforo/PruebaSemaforoReportScreen.kt`  
**Línea del error:** ~248  
**Cambios necesarios:** IDÉNTICO a PruebaInspeccionReportScreen.kt

> Solo cambia `fetchVehiculosInspeccion()` por `fetchVehiculosSemaforo()`

✅ **Tiempo estimado:** 2 minutos (COPY/PASTE)

---

#### 3. UsuariosScreen.kt
**Ubicación:** `/usuarios/UsuariosScreen.kt`  
**Línea del error:** ~191  
**Cambios necesarios:** 2 minutos

```kotlin
// Paso 1: Agregar import
import com.megatransportes.yokohama.ui.components.ErrorCard

// Paso 2: Reemplazar error (línea ~191 dentro del when)
ANTES:
errorMessage != null -> {
    Text(
        text = errorMessage!!,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.align(Alignment.Center).padding(16.dp)
    )
}

DESPUÉS:
errorMessage != null -> {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ErrorCard(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchUsuarios() } }
        )
    }
}

// Paso 3: Verificar que existe fetchUsuarios()
// Ya debería existir alrededor de línea 60
```

✅ **Tiempo estimado:** 2 minutos

---

### 🟡 IMPORTANTES (Usuarios pueden necesitar)

#### 4. FlotasScreen.kt
#### 5. LlantasVehiculoScreen.kt  
#### 6. PruebaRendimientoScreen.kt
#### 7. SemaforoScreen.kt

Cada una: 3-5 minutos (mismo patrón que VehiculosScreen)

---

### 🟢 OPCIONALES (Menos críticos)

#### Pantallas de Creación:
- AddVehiculoScreen.kt
- NuevaLlantaDesechoScreen.kt
- NuevaPruebaInspeccionScreen.kt

#### Pantallas de Administración:
- LlantasAdminScreen.kt
- TipoVehiculosAdminScreen.kt
- PerfilesUsuarioListScreen.kt
- EditUsuarioScreen.kt

---

## 📝 Template de Actualización (Copia/Pega)

### Para Pantallas Completas (ErrorScreen):

**PASO 1:** Busca línea de imports y agrega:
```kotlin
import com.megatransportes.yokohama.ui.components.ErrorScreen
```

**PASO 2:** Busca el bloque `when` dentro del `Scaffold` y reemplaza el caso de error:

```kotlin
when {
    isLoading && lista.isEmpty() -> { /* loader */ }
    
    errorMessage != null -> {
        ErrorScreen(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchData() } }
        )
    }
    
    lista.isEmpty() -> { /* empty state */ }
    else -> { /* content */ }
}
```

**PASO 3:** Asegúrate de que `fetchData()` existe como función `suspend`

---

### Para Pantallas con Otros Elementos (ErrorCard):

**PASO 1:** Busca línea de imports y agrega:
```kotlin
import com.megatransportes.yokohama.ui.components.ErrorCard
```

**PASO 2:** Busca el bloque de error dentro de Column/Box:

```kotlin
Column {
    SearchBar()
    
    if (errorMessage != null) {
        ErrorCard(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchData() } }
        )
    }
    
    LazyColumn { /* content */ }
}
```

---

## 🎯 Plan de Trabajo Sugerido

### Hoy (Ahora mismo):
```
[ ] Leer este documento (5 min)
[ ] Actualizar PruebaInspeccionReportScreen.kt (5 min)
[ ] Actualizar PruebaSemaforoReportScreen.kt (3 min)
[ ] Actualizar UsuariosScreen.kt (3 min)
[ ] Compilar y probar cambios (3 min)
────────────────────────────
    Total: 19 minutos
```

### Mañana:
```
[ ] FlotasScreen.kt (5 min)
[ ] LlantasVehiculoScreen.kt (5 min)
[ ] PruebaRendimientoScreen.kt (5 min)
[ ] Compilar y probar (5 min)
────────────────────────────
    Total: 20 minutos
```

### Próxima semana (en ratos libres):
```
[ ] Resto de pantallas (2-3 horas)
[ ] Testing completo (30 min)
```

---

## 🧪 Testing de Cada Pantalla

Una vez actualizada, prueba así:

```kotlin
// 1. Abre la pantalla
// 2. Desconecta WiFi
// 3. Presiona el botón de refresh/reload
// ✓ Deberías ver ErrorScreen con icono y botón
// 4. Reconecta WiFi
// 5. Presiona "Reintentar"
// ✓ Los datos deberían cargar nuevamente
```

---

## ✨ Indicadores de Éxito

✅ Compilas sin errores  
✅ ErrorScreen muestra cuando hay error  
✅ Botón "Reintentar" carga datos nuevamente  
✅ Mensaje se ve limpio y profesional  
✅ Funciona en modo portrait y landscape  

---

## 📞 Si Tienes Problemas

**Problema:** "Unresolved reference: ErrorScreen"
```kotlin
// Solución: Verifica el import exacto
import com.megatransportes.yokohama.ui.components.ErrorScreen
```

**Problema:** Botón no funciona
```kotlin
// Verifica que:
// 1. La función es suspend
// 2. Llamas coroutineScope.launch { }
// 3. La función resetea errorMessage = null
```

**Problema:** Mensaje muy técnico
```kotlin
// Verifica ErrorUtils lo está convirtiendo
errorMessage = ErrorUtils.userMessage(error, "Fallback")
//                                             ↑ mostrado si error.message está vacío
```

---

## 📊 Métricas

| Métrica | Valor |
|---------|-------|
| Componentes creados | 1 (ErrorScreen.kt) |
| Pantallas actualizadas | 4 |
| Líneas de código total | ~164 |
| Importes necesarios | 1 (por pantalla) |
| Tiempo por pantalla | 2-5 min |

---

## 🎓 Lo Aprendiste

✅ Crear componentes reutilizables en Compose  
✅ Manejo consistente de errores  
✅ Mejor UX con mensajes amigables  
✅ Pattern de retry en Kotlin  
✅ Buenas prácticas de UI/UX  

---

## 📚 Referencias Internas

- **ErrorScreen.kt** - El componente principal
- **ErrorUtils.kt** - Convierte excepciones en mensajes amigables
- **VehiculosScreen.kt** - Ejemplo de implementación completa

---

## ⏰ Timeline Recomendado

```
HOY:      Actualizar 3 pantallas (20 min)
MAÑANA:   Actualizar 3 más (20 min)
SEMANA:   Resto + testing (2 horas)
```

**Total: 3 horas para todas las pantallas ✅**

---

**¿Listo para empezar?** 🚀

Comienza con PruebaInspeccionReportScreen.kt siguiendo los pasos arriba.  
¡Será rápido y sencillo!

---

*Documento de referencia - 2024*
