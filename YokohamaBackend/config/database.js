const mysql = require('mysql2/promise');
require('dotenv').config();

// Allow overriding DB credentials via environment variables for deployments
/*const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'megatransporte_admin',
  password: process.env.DB_PASSWORD || '1iSPf7qad?L%~%0a',
  database: process.env.DB_NAME || 'megatransporte_yokohamabd',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});*/
const pool = mysql.createPool({
  host: 'localhost',
  user: 'root',
  password: 'admin',
  database: 'megatransporte_yokohamabd',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

// Comprobación de conexión
(async () => {
  try {
    const connection = await pool.getConnection();
    await connection.ping();
    console.log('Conexión a la base de datos exitosa');
    connection.release();
  } catch (err) {
    console.error('Error al conectar con la base de datos:', err.message);
    console.error('Hint: verify DB credentials and that MySQL is running. You can set DB_USER/DB_PASSWORD/DB_HOST/DB_NAME in a .env file or environment.');
  }
})();

module.exports = pool;
