# Guía Rápida: Aplicar ErrorScreen a Todas las Pantallas

## ✅ Status Actual

### Pantallas Ya Actualizadas:
- ✅ `VehiculosScreen.kt`
- ✅ `PruebasInspeccionListScreen.kt`
- ✅ `PruebasSemaforoListScreen.kt`
- ✅ `ParametrosListScreen.kt`

### Pantallas Pendientes:

#### Grupo 1: Pantallas de Reportes (PullToRefreshBox ya quitado)
- [ ] `PruebaInspeccionReportScreen.kt` - Línea ~248
- [ ] `PruebaSemaforoReportScreen.kt` - Línea ~248

#### Grupo 2: Pantallas de Desecho
- [ ] `LlantasDesechoScreen.kt` - Verificar si tiene error handling
- [ ] `PilasDesechoScreen.kt` - Verificar si tiene error handling

#### Grupo 3: Pantallas de Usuarios y Flotas
- [ ] `UsuariosScreen.kt` - Línea ~191
- [ ] `FlotasScreen.kt`
- [ ] `FlotaUsuariosScreen.kt`
- [ ] `GestionTiposVehiculosScreen.kt`

#### Grupo 4: Pantallas de Vehículos (Detalle)
- [ ] `LlantasVehiculoScreen.kt`
- [ ] `LlantaBitacoraScreen.kt`
- [ ] `PruebaRendimientoScreen.kt`
- [ ] `SemaforoScreen.kt`
- [ ] `InspeccionVehiculoScreen.kt`

#### Grupo 5: Pantallas de Creación
- [ ] `AddVehiculoScreen.kt`
- [ ] `NuevaLlantaDesechoScreen.kt`
- [ ] `NuevaPruebaInspeccionScreen.kt`
- [ ] `InspeccionVehicularScreen.kt` (Actualización)

#### Grupo 6: Pantallas de Admin/Configuración
- [ ] `LoginScreen.kt` - Ya tiene error handling básico
- [ ] `PerfilesUsuarioListScreen.kt`
- [ ] `EditUsuarioScreen.kt`
- [ ] `LlantasAdminScreen.kt`
- [ ] `TipoVehiculosAdminScreen.kt`

---

## 📋 Template de Reemplazo

Para cada pantalla, sigue este patrón:

### Paso 1: Agregar Import
```kotlin
import com.megatransportes.yokohama.ui.components.ErrorScreen
// O si es parte de una columna más grande:
import com.megatransportes.yokohama.ui.components.ErrorCard
```

### Paso 2: Reemplazar Manejo de Error

**OPCIÓN A: Pantalla Completa (ErrorScreen)**

ANTES:
```kotlin
when {
    isLoading -> { CircularProgressIndicator() }
    errorMessage != null -> {
        Text(text = errorMessage!!, color = Color.Red)
        Button(onClick = { /* retry */ }) { Text("Reintentar") }
    }
    lista.isEmpty() -> { Text("Sin datos") }
    else -> { LazyColumn { items(...) } }
}
```

DESPUÉS:
```kotlin
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
    else -> { LazyColumn { items(...) } }
}
```

**OPCIÓN B: Dentro de una Columna (ErrorCard)**

ANTES:
```kotlin
Column {
    OutlinedTextField(...)
    
    if (errorMessage != null) {
        Text(errorMessage!!, color = Color.Red)
        Button(...) { Text("Reintentar") }
    }
    
    LazyColumn { items(...) }
}
```

DESPUÉS:
```kotlin
Column {
    OutlinedTextField(...)
    
    if (errorMessage != null) {
        ErrorCard(
            errorMessage = errorMessage!!,
            onRetry = { coroutineScope.launch { fetchData() } }
        )
    }
    
    LazyColumn { items(...) }
}
```

---

## 🔍 Pantallas Prioritarias Primero

**Recomendación: Hacer estas 3 primero**

1. **PruebaInspeccionReportScreen.kt** - Usuarios verán errores aquí frecuentemente
2. **PruebaSemaforoReportScreen.kt** - Similar a inspección
3. **UsuariosScreen.kt** - Gestión general del sistema

---

## 📝 Checklist por Pantalla

### LlantasDesechoScreen.kt
- [ ] Buscar `errorMessage` en el archivo
- [ ] Agregar import `ErrorScreen` o `ErrorCard`
- [ ] Reemplazar el bloque de error

### PilasDesechoScreen.kt
- [ ] ¿Tiene manejo de error? Verificar
- [ ] Si no, agregar validación de conexión

### PruebaInspeccionReportScreen.kt
- [ ] Línea ~248: Reemplazar `Column { Text(errorMessage) Button(...) }`
- [ ] Agregar import `ErrorScreen`

### PruebaSemaforoReportScreen.kt
- [ ] Línea ~248: Idéntico a ReportScreen anterior
- [ ] Agregar import `ErrorScreen`

### UsuariosScreen.kt
- [ ] Línea ~191: Reemplazar texto de error simple
- [ ] Agregar import `ErrorCard` (hay otros elementos en pantalla)

---

## 🚀 Quick Reference: Imports Necesarios

```kotlin
// Para pantalla completa
import com.megatransportes.yokohama.ui.components.ErrorScreen

// Para card dentro de otros elementos
import com.megatransportes.yokohama.ui.components.ErrorCard

// ErrorUtils ya debería estar importado
import com.megatransportes.yokohama.utils.ErrorUtils
```

---

## 💡 Notas Importantes

1. **coroutineScope** debe estar disponible (crealo con `rememberCoroutineScope()` si no existe)
2. Siempre crea una función `suspend fun fetchData()` para el retry
3. Resetea `errorMessage = null` al inicio de cada carga
4. El componente ya muestra "Error de conexión" automáticamente
5. No necesitas traducir - el componente está 100% en español

---

## ✨ Ejemplo Completo Funcional

Ver archivos actualizados:
- [VehiculosScreen.kt](VehiculosScreen.kt) - Pantalla completa con ErrorScreen
- [ParametrosListScreen.kt](ParametrosListScreen.kt) - Pantalla con otros elementos y ErrorCard

---

**Última actualización**: 2024  
**Componente**: `ErrorScreen.kt`  
**Estado**: 4 pantallas ✅ | 20+ pendientes 🔄
