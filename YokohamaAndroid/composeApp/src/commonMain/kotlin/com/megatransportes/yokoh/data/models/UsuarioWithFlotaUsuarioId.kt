package com.megatransportes.yokoh.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UsuarioWithFlotaUsuarioId(
    val idUsuarios: Int,
    val UsuariosNombre: String,
    val UsuariosTelefono: String,
    val UsuariosCorreo: String,
    val PerfilesUsuario_idPerfilesUsuario: Int,
    val PerfilesUsuarioNombre: String? = null,
    val idFlotasUsuarios: Int
)
