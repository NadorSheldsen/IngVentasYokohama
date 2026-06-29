# Yokohama iOS — Bitácora de Soluciones

## Contexto
- Proyecto Kotlin Multiplatform (Compose Multiplatform 1.6.10) con target iOS + Android.
- Build Release desde Xcode con Gradle wrapper (`./gradlew`).
- iOS target: arm64 + x86_64 (simulador), Xcode 26.5, Apple Silicon.
- Kotlin 1.9.22, Gradle 8.13, JDK 17.

## Problemas Resueltos

### 1. `ENABLE_USER_SCRIPT_SANDBOXING` bloqueaba el build
- **Síntoma**: Build falla con `Sandbox: rsync.samba deny(1) file-write-create`.
- **Causa**: Xcode 16+ habilita `ENABLE_USER_SCRIPT_SANDBOXING = YES` por defecto. La fase de script que corre `./gradlew ...` necesita escribir archivos.
- **Solución**: `ENABLE_USER_SCRIPT_SANDBOXING = NO` en `Config.xcconfig` y `project.pbxproj`.

### 2. Bundle ID incorrecto para el provisioning profile
- **Síntoma**: `No matching provisioning profiles found`.
- **Causa**: El provisioning profile existente espera `com.megatransportes.yokohama.Yokohama`.
- **Solución**: Cambiar `PRODUCT_BUNDLE_IDENTIFIER` en `Config.xcconfig` a `com.megatransportes.yokohama.Yokohama`.

### 3. `UIAlertController` no compila en Kotlin/Native
- **Síntoma**: `unresolved reference: UIAlertControllerStyle` (no existe en Kotlin/Native).
- **Causa**: `UIAlertController` + `UIAlertAction` son Objective-C classes, pero `UIAlertControllerStyle` y `UIAlertActionStyle` son `NSInteger` enums que no se exportan a Kotlin/Native.
- **Solución**: Reemplazar `UIAlertController` por un `AlertDialog` de Compose Multiplatform.

### 4. Componentes LazyColumn sin `key` — warnings de rendimiento
- **Síntoma**: Múltiples warnings "No key defined for item in LazyColumn".
- **Causa**: 21 llamadas a `items()` sin `key`.
- **Solución**: Agregar `key = { it.id }` o similar en todas.

### 5. `Divider` obsoleto → `HorizontalDivider`
- **Causa**: Deprecado en Compose Material 3.
- **Solución**: Reemplazar en 4 archivos.

### 6. `smallTopAppBarColors` → `topAppBarColors`
- **Causa**: Deprecado.
- **Solución**: Reemplazar en archivos que usaban `TopAppBar`.

### 7. Iconos `ArrowBack`, `ExitToApp`, `InsertDriveFile` deprecados
- **Causa**: Migración a `AutoMirrored.Filled.*`.
- **Solución**: 31 archivos actualizados a `AutoMirrored`.

### 8. `KotlinNativeCompile` no estaba configurado en `build.gradle.kts`
- **Síntoma**: Build Release fallaba en producción.
- **Causa**: El bloque `kotlin { ... targets.withType<KotlinNativeCompile> ... }` faltaba; solo existía el bloque para JVM.
- **Solución**: Agregar bloque `KotlinNativeCompile` con flags: `-Xexpect-actual-classes`, `-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi`, `-Xopt-in=kotlinx.cinterop.BetaInteropApi`.

### 9. `NSLog` con argumentos variádicos causa `EXC_BAD_ACCESS`
- **Síntoma**: Crash `EXC_BAD_ACCESS` al abrir "Reporte de inspecciones" o la cámara.
- **Causa**: `NSLog("%@: %@", tag, message)` en Kotlin/Native pasa `kotlin.String` (memoria gestionada por GC) a un C variadic `...` que espera `NSString*` retainable. El mensaje se muestra parcialmente corrupto (`0x5f41424555526e` = `_ABEURn` en ASCII), confirmando que no es un `NSString*` válido.
- **Solución**: Reemplazar `NSLog` por `println` en `DebugLog.ios.kt`.

### 10. `NSCameraUsageDescription` faltante en Info.plist
- **Síntoma**: App crashea al abrir la cámara: "This app has crashed because it attempted to access privacy-sensitive data without a usage description."
- **Causa**: `UIImagePickerController` requiere `NSCameraUsageDescription` en `Info.plist`.
- **Solución**: Agregar `<key>NSCameraUsageDescription</key>` con valor descriptivo en `iosApp/iosApp/Info.plist`.

## Problemas Pendientes

### A. Build Release multi-arquitectura
- **Síntoma**: Si Xcode intenta construir para arm64 + x86_64 simultáneamente, corren dos daemons Gradle y el lock `registry.bin.lock` causa timeout.
- **Solución temporal**: `ONLY_ACTIVE_ARCH = YES` en `Config.xcconfig` para Release.
- **Solución permanente**: Usar Gradle Task Execution Graph para serializar los builds, o configurar Xcode para construir sólo arm64.

### B. Error de compilación en `actualizar-inspeccion` branch (Merge 1)
- **Síntoma**: Merge conflict parcial — `AppNavigation.kt` usa `screen = screen` como parámetro de entrada y también como variable local.
- **Solución**: Capturar en `val s = screen` y usar `s` en el `when` para habilitar smart cast.

## Comandos Útiles
```bash
# Build Release desde Xcode (con ONLY_ACTIVE_ARCH = YES)
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build

# Matar daemon Gradle (si lock contiende)
./gradlew --stop
kill -9 <PID>
rm -f ~/.gradle/daemon/8.13/registry.bin.lock
```
