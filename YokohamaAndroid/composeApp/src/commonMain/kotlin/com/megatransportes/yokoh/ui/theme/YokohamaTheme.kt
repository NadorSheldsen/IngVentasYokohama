package com.megatransportes.yokoh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores personalizados para Yokohama
object YokohamaColors {
    // Colores principales - Rojo Yokohama
    val Primary = Color(0xFFD32F2F)        // Rojo principal
    val PrimaryVariant = Color(0xFF9A0007)  // Rojo más oscuro
    val PrimaryLight = Color(0xFFFF6659)    // Rojo más claro
    
    // Colores secundarios - Negro y grises
    val Secondary = Color(0xFF212121)       // Negro principal
    val SecondaryVariant = Color(0xFF424242) // Gris oscuro
    val SecondaryLight = Color(0xFF757575)   // Gris medio
    
    // Colores de superficie y fondo
    val Surface = Color(0xFFFFFFFF)         // Blanco puro
    val Background = Color(0xFFFAFAFA)      // Blanco ligeramente gris
    val OnSurface = Color(0xFF212121)       // Negro para texto en superficies blancas
    val OnBackground = Color(0xFF212121)    // Negro para texto en fondo
    val OnPrimary = Color(0xFFFFFFFF)       // Blanco para texto en rojo
    val OnSecondary = Color(0xFFFFFFFF)     // Blanco para texto en negro
    
    // Colores de error y estados
    val Error = Color(0xFFB00020)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)
    
    // Colores adicionales para cards y containers
    val SurfaceVariant = Color(0xFFF5F5F5)  
    val OnSurfaceVariant = Color(0xFF424242) 
    val Outline = Color(0xFFBDBDBD)         
    val OutlineVariant = Color(0xFFE0E0E0)  
}

// Esquema de colores claro personalizado
private val YokohamaLightColorScheme = lightColorScheme(
    primary = YokohamaColors.Primary,
    onPrimary = YokohamaColors.OnPrimary,
    primaryContainer = Color(0xFFFFEBEE), 
    onPrimaryContainer = YokohamaColors.Primary,
    
    secondary = YokohamaColors.Secondary,
    onSecondary = YokohamaColors.OnSecondary,
    secondaryContainer = Color(0xFFF5F5F5), 
    onSecondaryContainer = YokohamaColors.Secondary,
    
    tertiary = Color(0xFF6D4C41),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEFEBE9),
    onTertiaryContainer = Color(0xFF3E2723),
    
    error = YokohamaColors.Error,
    onError = YokohamaColors.OnError,
    errorContainer = YokohamaColors.ErrorContainer,
    onErrorContainer = YokohamaColors.OnErrorContainer,
    
    background = YokohamaColors.Background,
    onBackground = YokohamaColors.OnBackground,
    surface = YokohamaColors.Surface,
    onSurface = YokohamaColors.OnSurface,
    surfaceVariant = YokohamaColors.SurfaceVariant,
    onSurfaceVariant = YokohamaColors.OnSurfaceVariant,
    
    outline = YokohamaColors.Outline,
    outlineVariant = YokohamaColors.OutlineVariant,
    
    scrim = Color(0x80000000), // Semi-transparente negro para overlays
    inverseSurface = YokohamaColors.Secondary,
    inverseOnSurface = YokohamaColors.Surface,
    inversePrimary = YokohamaColors.PrimaryLight,
    
    surfaceDim = Color(0xFFF5F5F5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8)
)

// Esquema de colores oscuro (opcional, manteniendo el estilo)
private val YokohomaDarkColorScheme = darkColorScheme(
    primary = YokohamaColors.PrimaryLight,
    onPrimary = YokohamaColors.Secondary,
    primaryContainer = YokohamaColors.PrimaryVariant,
    onPrimaryContainer = YokohamaColors.PrimaryLight,
    
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF424242),
    onSecondaryContainer = Color.White,
    
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFEDEDED),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF5E5E5E),
    
    error = Color(0xFFFF5449),
    onError = Color(0xFF680003)
)

@Composable
fun YokohamaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        YokohomaDarkColorScheme
    } else {
        YokohamaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), 
        content = content
    )
}

// Extensiones para acceso fácil a colores personalizados
object YokohamaThemeExtensions {
    val MaterialTheme.yokohamaColors: YokohamaColors
        @Composable get() = YokohamaColors
}