# 📁 Estructura de Carpetas - Yokohama

## Carpeta WEB (Referencia y documentación)

```
web/
├── index.html              # HTML de login (referencia)
├── test.html              # HTML de test (referencia)
├── styles.css             # Estilos CSS (referencia)
└── README.md              # Documentación
```

**Nota**: Los HTML en esta carpeta ahora referencian archivos del backend (`/css/`, `/js/`)

## Backend (Lo que se sirve)

```
YokohamaBackend/
├── public/                           # Archivos estáticos servidos por Express
│   ├── index.html                   # Página de login (SERVIDA)
│   ├── test.html                    # Página de test (SERVIDA)
│   ├── css/
│   │   └── styles.css               # Estilos (SERVIDOS)
│   └── js/
│       ├── config.js                # Configuración (SERVIDO)
│       └── login.js                 # Lógica de login (SERVIDO)
│
├── routes/
│   ├── usuarios.js                  # Endpoints de usuarios + login
│   ├── img/
│   │   └── yokohamalogo.png         # Logo del sitio
│   └── ...
│
├── app.js                           # Configuración de Express
├── index.js                         # Punto de entrada
└── package.json
```

## Cómo funciona

1. **Navega a**: `http://localhost:3000`
2. **Express sirve**: `/public/index.html`
3. **El HTML enlaza**: `/css/styles.css` y `/js/config.js`, `/js/login.js`
4. **Imágenes desde**: `/img/yokohamalogo.png`

## Próximas ventanas

Cuando crees nuevas páginas (dashboard, etc.):
1. Crea el HTML en `web/nombre.html` (referencia)
2. Copia el HTML a `YokohamaBackend/public/nombre.html`
3. Los estilos reutilizan `/css/styles.css`
4. Agrega el JS necesario a `YokohamaBackend/public/js/`
