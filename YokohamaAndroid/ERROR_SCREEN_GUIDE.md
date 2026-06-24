# Implementación de Error Screen en Todas las Pantallas

## Resumen

Se ha creado un componente reutilizable `ErrorScreen` y `ErrorCard` en `ErrorScreen.kt` para mostrar errores de conexión de forma consistente en toda la aplicación.

## Componentes Disponibles

### 1. ErrorScreen (Pantalla Completa)

Usa esta cuando el error **ocupa toda la pantalla** (durante carga inicial):

```kotlin
if (errorMessage != null) {
    ErrorScreen(
        errorMessage = errorMessage!!,
        onRetry = { coroutineScope.launch { fetchData() } }
    )
} else {
    // tu contenido normal
}
```

**Características:**
- Icono de error grande
- Card con título "Error de conexión"
- Botón "Reintentar" destacado
- Hint: "Verifica tu conexión a internet e intenta nuevamente"

### 2. ErrorCard (Card Compacto)

Usa esta cuando tienes **otros elementos en la pantalla**:

```kotlin
Column {
    if (errorMessage != null) {
        ErrorCard(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchData() } },
            modifier = Modifier.padding(16.dp)
        )
    }
    // otros elementos
}
```

## Patrón Típico de Implementación

En tus pantallas, reemplaza el código actual con este patrón:

```kotlin
// 1. Importar el componente
import com.megatransportes.yokohama.ui.components.ErrorScreen

// 2. En tu función Composable, dentro del Scaffold
) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        when {
            isLoading && lista.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            
            // ← Agregar esto para mostrar errores
            errorMessage != null -> {
                ErrorScreen(
                    errorMessage = errorMessage!!,
                    onRetry = { coroutineScope.launch { fetchData() } },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            
            lista.isEmpty() -> {
                Text("No hay datos", modifier = Modifier.align(Alignment.Center))
            }
            
            else -> {
                // Tu contenido normal (LazyColumn, etc)
            }
        }
    }
}
```

## Pantallas a Actualizar

### Pantallas Principales (Ya con patrón similar):
1. ✅ `VehiculosScreen.kt` - ACTUALIZADA
2. ✅ `PruebasInspeccionListScreen.kt` - ACTUALIZADA
3. ✅ `PruebasSemaforoListScreen.kt` - ACTUALIZADA
4. `ParametrosListScreen.kt`
5. `PruebaInspeccionReportScreen.kt`
6. `PruebaSemaforoReportScreen.kt`
7. `UsuariosScreen.kt`
8. `FlotasScreen.kt`
9. `LlantasDesechoScreen.kt` (y pantallas de desecho)
10. `LlantasVehiculoScreen.kt`
11. Todas las pantallas de reportes

## Integración Automática

Para **agregar ErrorScreen a una pantalla existente**:

```kotlin
// ANTES:
when {
    isLoading -> { CircularProgressIndicator() }
    errorMessage != null -> {
        Text(text = errorMessage!!, color = Color.Red)
    }
    lista.isEmpty() -> { Text("Sin datos") }
    else -> { LazyColumn { items(...) } }
}

// DESPUÉS:
when {
    isLoading && lista.isEmpty() -> {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
    errorMessage != null -> {
        ErrorScreen(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchData() } }
        )
    }
    lista.isEmpty() -> {
        Text("Sin datos", modifier = Modifier.align(Alignment.Center))
    }
    else -> {
        LazyColumn { items(...) }
    }
}
```

## Manejo de Errores (Backend)

Los mensajes de error ya son **procesados por `ErrorUtils.userMessage()`** en el ApiClient, por lo que recibirás mensajes amigables como:

- ✅ "No se pudo conectar al servidor. Revisa tu conexión."
- ✅ "No autorizado. Verifica tus credenciales."
- ✅ "Respuesta inválida del servidor. Intenta nuevamente más tarde."

**No necesitas hacer nada especial en el backend.**

## Función de Retry Ejemplo

Todas tus pantallas ya tienen una función `suspend` para cargar datos:

```kotlin
suspend fun fetchData() {
    isLoading = true
    errorMessage = null
    
    repository.getData()
        .onSuccess { data -> lista = data }
        .onFailure { error -> 
            errorMessage = ErrorUtils.userMessage(error, "Error por defecto")
        }
    
    isLoading = false
}
```

Luego úsala en `onRetry`:

```kotlin
ErrorScreen(
    errorMessage = errorMessage!!,
    onRetry = { coroutineScope.launch { fetchData() } }
)
```

## Ejemplo Completo

Ver [VehiculosScreen.kt](VehiculosScreen.kt) para un ejemplo de implementación completa.

## Notas Importantes

1. **Siempre** usa `ErrorUtils.userMessage()` para convertir excepciones en mensajes amigables
2. **Siempre** resetea `errorMessage = null` antes de hacer una solicitud
3. El botón "Reintentar" debe llamar a tu función `suspend` de carga dentro de `coroutineScope.launch {}`
4. Usa `ErrorScreen` para pantallas completas y `ErrorCard` para elementos parciales
5. El componente ya maneja **internacionalizacion** (es completamente en español)

---

**Estado**: ✅ Implementado en ejemplo  
**Próximos pasos**: Aplicar a todas las pantallas listadas arriba
