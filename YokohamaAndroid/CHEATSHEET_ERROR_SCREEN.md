# 📌 ErrorScreen Cheatsheet - Referencia Rápida

## 3 Pasos Principales

### 1️⃣ IMPORT
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorScreen
// O
import com.megatransportes.yokoh.ui.components.ErrorCard
```

### 2️⃣ FUNCIÓN
```kotlin
suspend fun fetchData() {
    isLoading = true
    errorMessage = null
    
    repository.getData()
        .onSuccess { data = it }
        .onFailure { error -> 
            errorMessage = ErrorUtils.userMessage(error, "Error por defecto")
        }
    
    isLoading = false
}
```

### 3️⃣ UI
```kotlin
// OPCIÓN A: Pantalla Completa
errorMessage != null -> {
    ErrorScreen(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchData() } }
    )
}

// OPCIÓN B: Dentro de Columna
if (errorMessage != null) {
    ErrorCard(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchData() } }
    )
}
```

---

## ✅ Checklist Pre-Implementación

- [ ] ¿Tiene la pantalla un `errorMessage by remember { ... }`?
- [ ] ¿Hay una función que carga datos?
- [ ] ¿Existe `coroutineScope = rememberCoroutineScope()`?
- [ ] ¿Tiene `ErrorUtils.userMessage()` en los errores?

Si dijiste NO a alguna, agrega primero.

---

## 🔍 Buscar y Reemplazar (Regex)

### En VS Code, en cada pantalla:

**Buscar el patrón de error actual:**
```regex
errorMessage\s*!=\s*null\s*->\s*\{[\s\S]*?Button\([\s\S]*?Text\("Reintentar"
```

**Reemplazar con:**
```kotlin
errorMessage != null -> {
    ErrorScreen(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchData() } }
    )
}
```

> ⚠️ Advisa: Esto es aproximado - mejor hacer manualmente

---

## 📋 Por Tipo de Pantalla

### Pantallas de LISTA (Vehicles, etc)
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorScreen

Scaffold(...) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && lista.isEmpty() -> Loader()
            errorMessage != null -> ErrorScreen(...)
            lista.isEmpty() -> Empty()
            else -> LazyColumn { items(...) }
        }
    }
}
```

### Pantallas con BÚSQUEDA (Usuarios, Parámetros)
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorCard

Column {
    SearchBar()
    
    if (errorMessage != null) {
        ErrorCard(errorMessage = ..., onRetry = ...)
    }
    
    LazyColumn { items(...) }
}
```

### Pantallas COMPLEJAS (Reports)
```kotlin
import com.megatransportes.yokoh.ui.components.ErrorScreen

when {
    isLoading -> Loader()
    errorMessage != null -> ErrorScreen(...)
    vehiculos.isEmpty() -> Empty()
    else -> Content()
}
```

---

## 🚨 Errores Comunes (y Soluciones)

| Error | Solución |
|-------|----------|
| `Unresolved reference: ErrorScreen` | Verifica el import exacto |
| `Unresolved reference: coroutineScope` | Agrega `val coroutineScope = rememberCoroutineScope()` |
| El botón no responde | Asegura que la función es `suspend` |
| Mensaje rojo técnico | Verifica `ErrorUtils.userMessage()` |
| Compilación falla | Limpia cache: `./gradlew clean build` |

---

## 🎨 Personalización

### Cambiar el mensaje
```kotlin
onRetry = { 
    errorMessage = "Mi mensj personalizado"
    coroutineScope.launch { fetchData() }
}
```

### Cambiar el comportamiento del botón
```kotlin
ErrorScreen(
    errorMessage = "...",
    onRetry = {
        // Tu lógica aquí
        coroutineScope.launch { fetchData() }
    }
)
```

### Cambiar ubicación
```kotlin
ErrorScreen(
    errorMessage = "...",
    onRetry = { ... },
    modifier = Modifier.align(Alignment.TopCenter)  // ← Aquí
)
```

---

## 📱 Testing Rápido

1. **Forzar error por WiFi:**
   - Abre pantalla
   - Desconecta WiFi
   - Presiona refresh
   - ✓ Debe mostrar ErrorScreen

2. **Forzar error en código:**
   ```kotlin
   errorMessage = "Test error message"
   ```
   - ✓ Debe mostrar ErrorScreen

3. **Probar retry:**
   - Error mostrado
   - Reconecta WiFi
   - Presiona "Reintentar"
   - ✓ Datos deben cargar

---

## 🔄 Flujo de Datos

```
Usuario activa pantalla
  ↓
LaunchedEffect() activa
  ↓
coroutineScope.launch { fetchData() }
  ↓
errorMessage = null
  ↓
API call exception
  ↓
errorMessage = ErrorUtils.userMessage(error)
  ↓
UI renderiza ErrorScreen
  ↓
Usuario presiona "Reintentar"
  ↓
coroutineScope.launch { fetchData() } ← Vuelve a empezar
```

---

## 📦 Componentes

### ErrorScreen (Compacto)
```kotlin
ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
)
```

### ErrorCard (Inline)
```kotlin
ErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## 🧩 Estructura Típica

```kotlin
@Composable
fun MyScreen(
    repository: YokohamaRepository,
    onBack: () -> Unit
) {
    var data by remember { mutableStateOf<List<Data>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // ↓ Crea esta función
    suspend fun fetchData() {
        isLoading = true
        errorMessage = null
        
        repository.getData()
            .onSuccess { data = it }
            .onFailure { error ->
                errorMessage = ErrorUtils.userMessage(error, "Error")
            }
        
        isLoading = false
    }
    
    // Carga inicial
    LaunchedEffect(Unit) {
        coroutineScope.launch { fetchData() }
    }
    
    Scaffold(...) {
        Box {
            when {
                isLoading && data.isEmpty() -> CircularProgressIndicator()
                errorMessage != null -> ErrorScreen(
                    errorMessage = errorMessage!!,
                    onRetry = { coroutineScope.launch { fetchData() } }
                )
                data.isEmpty() -> Text("Sin datos")
                else -> Content(data)
            }
        }
    }
}
```

---

## ⏱️ Tiempo por Pantalla

| Complejidad | Tipo | Tiempo |
|-------------|------|--------|
| 🟢 Simple | Lista sin búsqueda | 2 min |
| 🟡 Media | Lista + búsqueda | 3 min |
| 🔴 Compleja | Con múltiples calls | 5 min |

**Total para 20 pantallas: 60-100 minutos** ≈ 1.5 horas

---

## 🎯 Prioridad de Actualización

1. **PruebaInspeccionReportScreen.kt** ← Empieza aquí
2. **PruebaSemaforoReportScreen.kt**
3. **UsuariosScreen.kt**
4. **FlotasScreen.kt**
5. **LlantasVehiculoScreen.kt**
6. Resto de pantallas

---

## 💾 Variables Necesarias

```kotlin
// REQUIERE estos en remember:
var isLoading by remember { mutableStateOf(false) }
var errorMessage by remember { mutableStateOf<String?>(null) }
var data by remember { mutableStateOf<List<T>>(emptyList()) }

// REQUIERE esto en Composable:
val coroutineScope = rememberCoroutineScope()

// REQUIERE importar:
import com.megatransportes.yokoh.utils.ErrorUtils
import com.megatransportes.yokoh.ui.components.ErrorScreen
// O
import com.megatransportes.yokoh.ui.components.ErrorCard
```

---

## 🔗 Enlaces Rápidos

- **Componente:** `/ui/components/ErrorScreen.kt`
- **Guía Completa:** `ERROR_SCREEN_GUIDE.md`
- **Plan de Acción:** `PLAN_ACCION_APLICAR_ERROR_SCREEN.md`
- **Ejemplos:** `EJEMPLOS_DETALLEDOS_ERROR_SCREEN.md`
- **Pantallas Actualizadas:**
  - VehiculosScreen.kt
  - PruebasInspeccionListScreen.kt
  - PruebasSemaforoListScreen.kt
  - ParametrosListScreen.kt

---

## 🎓 Resumen Rápido

✅ Crea función `suspend fun fetchData()`  
✅ Agrega `import ErrorScreen` o `ErrorCard`  
✅ Reemplaza bloque de error con componente  
✅ Asegura que resetea `errorMessage = null`  
✅ Prueba desconectando WiFi  

**¡Eso es todo!** 🚀

---

*Cheatsheet - Referencia Rápida 2024*
