# Yokohama - Inicio Rápido

## 🚀 Iniciar la aplicación

1. **Instalar dependencias del backend** (solo la primera vez):
   ```bash
   cd YokohamaBackend
   npm install
   ```

2. **Iniciar el backend**:
   ```bash
   cd YokohamaBackend
   node index.js
   ```

3. **Abrir la aplicación web**:
   Abre tu navegador en: **http://localhost:3000**

## 📁 Estructura del Proyecto

### Carpeta WEB (Documentación y referencia)
- Solo contiene HTML y CSS como referencia
- Los JS están en YokohamaBackend

### Backend Express (Lo que realmente se sirve)
- Los archivos se sirven desde `YokohamaBackend/public/`
- CSS en: `YokohamaBackend/public/css/styles.css`
- JS en: `YokohamaBackend/public/js/`
- Imágenes en: `YokohamaBackend/routes/img/`

## 🔑 Login

Completamente integrado con el backend:
- Usa credenciales de la base de datos MySQL
- Las sesiones se guardan en localStorage
- Tema claro/oscuro automático

## 📖 Más información

Ver `ESTRUCTURA_CARPETAS.md` para entender cómo se organiza todo.

