const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8888;
const APK_FILE = path.join(__dirname, 'AlertaRIFT-v7.apk');

const server = http.createServer((req, res) => {
  if (req.url === '/' || req.url === '/download') {
    // Página de descarga
    const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Descargar Alerta RIFT</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
      min-height: 100vh; color: #fff; padding: 20px;
      display: flex; align-items: center; justify-content: center;
    }
    .container { text-align: center; max-width: 400px; }
    h1 { font-size: 3em; margin-bottom: 10px; }
    h2 { color: #60a5fa; margin-bottom: 30px; }
    .version { color: #94a3b8; margin-bottom: 30px; }
    .download-btn {
      display: inline-block; padding: 20px 40px;
      background: linear-gradient(135deg, #22c55e, #16a34a);
      color: white; text-decoration: none; border-radius: 16px;
      font-size: 1.3em; font-weight: bold;
      box-shadow: 0 4px 20px rgba(34, 197, 94, 0.4);
      transition: transform 0.2s;
    }
    .download-btn:active { transform: scale(0.98); }
    .instructions {
      margin-top: 40px; padding: 20px;
      background: rgba(255,255,255,0.1); border-radius: 12px;
      text-align: left; font-size: 0.9em; color: #94a3b8;
    }
    .instructions h3 { color: #fff; margin-bottom: 15px; }
    .instructions ol { padding-left: 20px; }
    .instructions li { margin-bottom: 10px; }
  </style>
</head>
<body>
  <div class="container">
    <h1>❄️</h1>
    <h2>Alerta RIFT</h2>
    <p class="version">Versión 7.0 - Campamento Parametican</p>
    
    <a href="/apk" class="download-btn">📥 DESCARGAR APK</a>
    
    <div class="instructions">
      <h3>📱 Instrucciones:</h3>
      <ol>
        <li>Tocá el botón de descarga</li>
        <li>Abrí el archivo descargado</li>
        <li>Si te pide permiso para instalar apps desconocidas, aceptá</li>
        <li>Instalá la app</li>
        <li>Abrí la app y poné la IP del RIFT</li>
      </ol>
    </div>
  </div>
</body>
</html>`;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  } else if (req.url === '/apk') {
    // Descargar APK
    if (!fs.existsSync(APK_FILE)) {
      res.writeHead(404);
      res.end('APK no encontrado');
      return;
    }
    const stat = fs.statSync(APK_FILE);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': stat.size,
      'Content-Disposition': 'attachment; filename="AlertaRIFT-v7.apk"'
    });
    fs.createReadStream(APK_FILE).pipe(res);
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

// Obtener IP local
const os = require('os');
const interfaces = os.networkInterfaces();
let localIP = 'localhost';
for (const name of Object.keys(interfaces)) {
  for (const iface of interfaces[name]) {
    if (iface.family === 'IPv4' && !iface.internal) {
      localIP = iface.address;
      break;
    }
  }
}

server.listen(PORT, '0.0.0.0', () => {
  console.log('\n');
  console.log('╔════════════════════════════════════════════════════════╗');
  console.log('║                                                        ║');
  console.log('║   📱 SERVIDOR DE DESCARGA - ALERTA RIFT               ║');
  console.log('║                                                        ║');
  console.log('╠════════════════════════════════════════════════════════╣');
  console.log('║                                                        ║');
  console.log(`║   🔗 LINK DE DESCARGA:                                 ║`);
  console.log(`║                                                        ║`);
  console.log(`║   http://${localIP}:${PORT}                            ║`);
  console.log('║                                                        ║');
  console.log('║   Abrí este link desde cualquier celular              ║');
  console.log('║   conectado a la misma red WiFi                       ║');
  console.log('║                                                        ║');
  console.log('╚════════════════════════════════════════════════════════╝');
  console.log('\n');
  console.log('Presioná Ctrl+C para cerrar el servidor\n');
});
