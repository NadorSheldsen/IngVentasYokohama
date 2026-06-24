const express = require('express');
const router = express.Router();
const db = require('../config/database');
const bcrypt = require('bcrypt');
const { handleServerError } = require('../utils/responseUtils');

// LOGIN endpoint - Debe estar antes de las rutas con parámetros como '/:id'
router.post('/login', async (req, res) => {
  try {
    // Comprobación de conexión antes de continuar
    try {
      const connection = await db.getConnection();
      await connection.ping();
      console.log('[LOGIN] Conexión a la base de datos OK');
      connection.release();
    } catch (connErr) {
      console.error('[LOGIN] Error de conexión a la base de datos:', connErr.message);
      return handleServerError(res, 'No se pudo conectar a la base de datos', connErr);
    }

    const { UsuariosCorreo, UsuariosPassword } = req.body;
    console.log(`[LOGIN] Body recibido:`, req.body);
    console.log(`[LOGIN] UsuariosCorreo: ${UsuariosCorreo}`);
    console.log(`[LOGIN] UsuariosPassword: ${UsuariosPassword}`);

    if (!UsuariosCorreo || !UsuariosPassword) {
      console.warn('[LOGIN] Faltan datos de correo o contraseña');
      return res.status(400).json({ message: 'Correo y contraseña son requeridos' });
    }

    const [rows] = await db.query(`
      SELECT *
      FROM usuarios 
      WHERE UsuariosCorreo = ? 
    `, [UsuariosCorreo]);

    // Imprime el resultado de la consulta en consola
    console.log('[LOGIN] Resultado de consulta rows:', rows);

    console.log(`[LOGIN] Usuarios encontrados: ${rows.length}`);

    if (rows.length === 0) {
      console.warn('[LOGIN] Usuario no encontrado');
      return res.status(401).json({ message: 'Credenciales inválidas' });
    }

    const user = rows[0];
    console.log('[LOGIN] Usuario encontrado:', user);

    // Compara el hash usando bcrypt.compare
    const hashed = await bcrypt.hash(UsuariosPassword, 10);
    console.log('contraseña recibida hasheada: ' + hashed);
    console.log('contraseña de la base de datos: ' + user.UsuariosPassword);
    const match = await bcrypt.compare(UsuariosPassword, user.UsuariosPassword);
    console.log(`[LOGIN] Resultado bcrypt.compare: ${match}`);

    if (!match) {
      console.warn('[LOGIN] Contraseña incorrecta');
      return res.status(401).json({ message: 'Credenciales inválidas' });
    }

    delete user.UsuariosPassword;
    console.log('[LOGIN] Login exitoso, usuario autenticado:', user);
    res.json(user);
  } catch (error) {
    console.error('[LOGIN] Error general:', error);
    handleServerError(res, 'Error al iniciar sesión', error);
  }
});

// GET all usuarios
router.get('/', async (req, res) => {
  console.log('[GET ALL] Solicitud recibida');
  try {
    const [rows] = await db.query(`
      SELECT u.*, p.PerfilesUsuarioNombre, d.DistribuidorNombre 
      FROM usuarios u
      LEFT JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario
      LEFT JOIN distribuidor d ON u.Distribuidor_idDistribuidor = d.idDistribuidor
    `);
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener los usuarios', error);
  }
});

// GET a specific usuario by ID
router.get('/:id', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT u.*, p.PerfilesUsuarioNombre, d.DistribuidorNombre 
      FROM usuarios u
      JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario
      LEFT JOIN distribuidor d ON u.Distribuidor_idDistribuidor = d.idDistribuidor
      WHERE u.idUsuarios = ?
    `, [req.params.id]);
    
    if (rows.length === 0) {
      return res.status(404).json({ message: 'Usuario no encontrado' });
    }
    
    res.json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al obtener el usuario', error);
  }
});

// POST a new usuario (con hash)
router.post('/', async (req, res) => {
  try {
    const { 
      UsuariosNombre, 
      UsuariosTelefono, 
      UsuariosCorreo, 
      UsuariosPassword, 
      PerfilesUsuario_idPerfilesUsuario,
      Distribuidor_idDistribuidor
    } = req.body;

    // Verifica si ya existe un usuario con ese correo
    const [existing] = await db.query(
      'SELECT idUsuarios FROM usuarios WHERE UsuariosCorreo = ?',
      [UsuariosCorreo]
    );
    if (existing.length > 0) {
      return res.status(409).json({ message: 'El correo ya está registrado' });
    }

    // Hash de la contraseña antes de guardar
    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(UsuariosPassword, saltRounds);

    const [result] = await db.query(
      'INSERT INTO usuarios (UsuariosNombre, UsuariosTelefono, UsuariosCorreo, UsuariosPassword, PerfilesUsuario_idPerfilesUsuario, Distribuidor_idDistribuidor) VALUES (?, ?, ?, ?, ?, ?)',
      [UsuariosNombre, UsuariosTelefono, UsuariosCorreo, hashedPassword, PerfilesUsuario_idPerfilesUsuario, Distribuidor_idDistribuidor || null]
    );

    // Consulta el usuario recién creado (sin la contraseña)
    const [rows] = await db.query(
      'SELECT u.idUsuarios, u.UsuariosNombre, u.UsuariosTelefono, u.UsuariosCorreo, u.PerfilesUsuario_idPerfilesUsuario, u.Distribuidor_idDistribuidor, p.PerfilesUsuarioNombre, d.DistribuidorNombre FROM usuarios u LEFT JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario LEFT JOIN distribuidor d ON u.Distribuidor_idDistribuidor = d.idDistribuidor WHERE u.idUsuarios = ?',
      [result.insertId]
    );

    res.status(201).json(rows[0]);
  } catch (error) {
    handleServerError(res, 'Error al crear el usuario', error);
  }
});
// PUT/UPDATE a usuario (actualiza con hash si se envía nueva contraseña)
router.put('/:id', async (req, res) => {
  try {
    const { 
      UsuariosNombre, 
      UsuariosTelefono, 
      UsuariosCorreo, 
      UsuariosPassword, 
      PerfilesUsuario_idPerfilesUsuario,
      Distribuidor_idDistribuidor 
    } = req.body;

    let query, params;
    if (UsuariosPassword) {
      const saltRounds = 10;
      const hashedPassword = await bcrypt.hash(UsuariosPassword, saltRounds);
      query = 'UPDATE usuarios SET UsuariosNombre = ?, UsuariosTelefono = ?, UsuariosCorreo = ?, UsuariosPassword = ?, PerfilesUsuario_idPerfilesUsuario = ?, Distribuidor_idDistribuidor = ? WHERE idUsuarios = ?';
      params = [UsuariosNombre, UsuariosTelefono, UsuariosCorreo, hashedPassword, PerfilesUsuario_idPerfilesUsuario, Distribuidor_idDistribuidor || null, req.params.id];
    } else {
      query = 'UPDATE usuarios SET UsuariosNombre = ?, UsuariosTelefono = ?, UsuariosCorreo = ?, PerfilesUsuario_idPerfilesUsuario = ?, Distribuidor_idDistribuidor = ? WHERE idUsuarios = ?';
      params = [UsuariosNombre, UsuariosTelefono, UsuariosCorreo, PerfilesUsuario_idPerfilesUsuario, Distribuidor_idDistribuidor || null, req.params.id];
    }

    const [result] = await db.query(query, params);

    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Usuario no encontrado' });
    }

    // Return the updated usuario (without password)  
    const [updatedUsuario] = await db.query(
      'SELECT u.idUsuarios, u.UsuariosNombre, u.UsuariosTelefono, u.UsuariosCorreo, u.PerfilesUsuario_idPerfilesUsuario, u.Distribuidor_idDistribuidor, p.PerfilesUsuarioNombre, d.DistribuidorNombre FROM usuarios u LEFT JOIN perfilesusuario p ON u.PerfilesUsuario_idPerfilesUsuario = p.idPerfilesUsuario LEFT JOIN distribuidor d ON u.Distribuidor_idDistribuidor = d.idDistribuidor WHERE u.idUsuarios = ?',
      [req.params.id]
    );

    res.json(updatedUsuario[0]);
  } catch (error) {
    handleServerError(res, 'Error al actualizar el usuario', error);
  }
});

// DELETE a usuario
router.delete('/:id', async (req, res) => {
  try {
    const [result] = await db.query('DELETE FROM usuarios WHERE idUsuarios = ?', [req.params.id]);
    
    if (result.affectedRows === 0) {
      return res.status(404).json({ message: 'Usuario no encontrado' });
    }
    
    res.json({ message: 'Usuario eliminado correctamente' });
  } catch (error) {
    handleServerError(res, 'Error al eliminar el usuario', error);
  }
});

// GET flotas by usuario ID
router.get('/:id/flotas', async (req, res) => {
  try {
    const [rows] = await db.query(`
      SELECT f.* 
      FROM flotas f
      JOIN flotasusuarios fu ON f.idFlotas = fu.Flotas_idFlotas
      WHERE fu.Usuarios_idUsuarios = ?
    `, [req.params.id]);
    
    res.json(rows);
  } catch (error) {
    handleServerError(res, 'Error al obtener las flotas del usuario', error);
  }
});

module.exports = router;
