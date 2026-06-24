# Configuración de Correo Electrónico

## Descripción
El sistema ahora soporta envío de reportes por correo desde las pantallas de:
- Semáforo (`semaforo.html`)
- Desecho (`desecho.html`)
- Inspección (`inspeccion.html`)

Los usuarios pueden ingresar direcciones de correo (separadas por comas) y enviar la tabla visible actualmente como HTML en el correo.

## Variables de Entorno Requeridas

Para habilitar la funcionalidad de email, configura las siguientes variables de entorno en un archivo `.env` en la raíz del directorio `YokohamaBackend`:

```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=tu_email@gmail.com
SMTP_PASS=tu_password_o_app_key
SMTP_FROM=tu_email@gmail.com
SMTP_SECURE=false
```

### Descripción de Variables

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `SMTP_HOST` | Servidor SMTP a usar | `smtp.gmail.com`, `smtp.outlook.com`, `mail.tudominio.com` |
| `SMTP_PORT` | Puerto SMTP | `587` (TLS), `465` (SSL), `25` (sin encriptación) |
| `SMTP_USER` | Usuario/email para autenticación | `tu_email@gmail.com` |
| `SMTP_PASS` | Contraseña o App Key para autenticación | Use [App Passwords](https://support.google.com/accounts/answer/185833) para Gmail |
| `SMTP_FROM` | Dirección de "desde" en los correos | `tu_email@gmail.com` |
| `SMTP_SECURE` | Usar SSL (true para puerto 465, false para 587 TLS) | `true` o `false` |

## Ejemplo de Configuración para Gmail

### Con 2FA/App Passwords (Recomendado)

1. Habilitar 2-Step Verification en tu cuenta Google
2. Generar una [App Password](https://support.google.com/accounts/answer/185833)
3. Usar el App Password en la variable `SMTP_PASS`

```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=tu_email@gmail.com
SMTP_PASS=xxxx xxxx xxxx xxxx
SMTP_FROM=tu_email@gmail.com
SMTP_SECURE=false
```

### Con Contraseña Directa (No Recomendado)

```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=tu_email@gmail.com
SMTP_PASS=tu_contraseña
SMTP_FROM=tu_email@gmail.com
SMTP_SECURE=false
```

## Ejemplo de Configuración para Outlook

```
SMTP_HOST=smtp-mail.outlook.com
SMTP_PORT=587
SMTP_USER=tu_email@outlook.com
SMTP_PASS=tu_contraseña
SMTP_FROM=tu_email@outlook.com
SMTP_SECURE=false
```

## Ejemplo de Configuración para Servidor Corporativo

```
SMTP_HOST=mail.tudominio.com
SMTP_PORT=25
SMTP_USER=usuario@tudominio.com
SMTP_PASS=contraseña
SMTP_FROM=noreply@tudominio.com
SMTP_SECURE=false
```

## Cómo Usar en la Aplicación

1. Navega a cualquiera de las tres pantallas (Semáforo, Desecho, Inspección)
2. En el panel de filtrado, verás un input etiquetado "Email(s)" y un botón "Enviar"
3. Ingresa uno o más correos separados por comas (ej: `user1@example.com, user2@example.com`)
4. Haz clic en "Enviar" 
5. La tabla visible actualmente será enviada por email en formato HTML

## Endpoint API

### POST `/api/reports/send-table-email`

**Body:**
```json
{
  "emails": ["user1@example.com", "user2@example.com"],
  "subject": "Reporte Semaforo",
  "viewName": "Semaforo",
  "tableHtml": "<table>...</table>"
}
```

**Response (Éxito 200):**
```json
{
  "message": "Correo enviado correctamente"
}
```

**Response (Error):**
```json
{
  "message": "Descripción del error"
}
```

## Validaciones

El endpoint realiza las siguientes validaciones:

- ✅ Al menos un correo válido debe ser proporcionado
- ✅ Todos los correos tienen formato válido (`user@domain.com`)
- ✅ La tabla HTML debe estar presente
- ✅ Todas las variables SMTP deben estar configuradas
- ✅ Se normaliza el input de correos (trim, lowercase, sin duplicados)

## Dependencias

El proyecto utiliza `nodemailer` para manejar las conexiones SMTP.

```bash
npm install nodemailer
```

Al ejecutar `npm install` en el directorio `YokohamaBackend`, nodemailer se instala automáticamente si no está presente.

## Troubleshooting

### Error: "Falta configuración SMTP"
- Verifica que todas las variables de entorno estén definidas en `.env`
- Reinicia el servidor después de cambiar `.env`

### Error: "Invalid login credentials"
- Verifica el usuario y contraseña
- Para Gmail, asegúrate de usar una App Password, no la contraseña de cuenta directa
- Verifica que la cuenta tenga permiso para "Aplicaciones Less Secure" (configuración más antigua de Gmail)

### Error: "Network timeout"
- Verifica que el host SMTP sea accesible desde tu red
- Comprueba que el puerto SMTP sea el correcto (587 para TLS, 465 para SSL)
- Verifica si hay un firewall bloqueando el puerto

### Los correos no se reciben
- Revisa la carpeta de spam
- Verifica que la dirección "desde" coincida con la cuenta SMTP
- Algunos servidores SMTP requieren que los correos provengan de la misma cuenta

## Seguridad

- ⚠️ **Nunca** cometas las variables SMTP a git
- Usa un archivo `.env` local (agregado a `.gitignore`)
- Para producción, usa variables de entorno del servidor (AWS Secrets, Azure Key Vault, etc.)
- Considera usar un servicio de correo (SendGrid, Mailgun, AWS SES) en lugar de SMTP directo
