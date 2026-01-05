const http = require('http');
const url = require('url');

// 6 RIFTs - Solo el primero está online
const rifts = [
  { id: 'RIFT-01', name: 'RIFT Principal', online: true, temp1: -22.3, temp2: -22.7, doorOpen: false, alertActive: false },
  { id: 'RIFT-02', name: 'RIFT Carnes', online: false, temp1: -20.0, temp2: -20.5, doorOpen: false, alertActive: false },
  { id: 'RIFT-03', name: 'RIFT Lácteos', online: false, temp1: -18.5, temp2: -18.8, doorOpen: false, alertActive: false },
  { id: 'RIFT-04', name: 'RIFT Verduras', online: false, temp1: -15.0, temp2: -15.2, doorOpen: false, alertActive: false },
  { id: 'RIFT-05', name: 'RIFT Bebidas', online: false, temp1: -5.0, temp2: -5.5, doorOpen: false, alertActive: false },
  { id: 'RIFT-06', name: 'RIFT Backup', online: false, temp1: -25.0, temp2: -25.3, doorOpen: false, alertActive: false },
];

// Estado del RIFT principal (el que está online)
let state = {
  temperature: -22.5,
  temp1: -22.3,
  temp2: -22.7,
  tempDHT: 0,
  humidity: 0,
  doorOpen: false,
  doorOpenSince: 0,
  alertActive: false,
  criticalAlert: false,
  alertMessage: "",
  relayOn: false,
  simulationMode: true,
  uptime: Date.now()
};

// Configuración
let config = {
  temp_max: -18.0,
  temp_critical: -10.0,
  alert_delay_sec: 300,
  door_open_max_sec: 180,
  relay_enabled: true,
  buzzer_enabled: true,
  telegram_enabled: true,
  sensor1_enabled: true,
  sensor2_enabled: true,
  dht22_enabled: false,
  simulation_mode: true,
  sim_temp1: -22.0,
  sim_temp2: -22.0
};

// Simular cambios de temperatura
setInterval(() => {
  // Variación aleatoria pequeña
  state.temperature += (Math.random() - 0.5) * 0.5;
  state.temp1 = state.temperature + (Math.random() - 0.5) * 0.3;
  state.temp2 = state.temperature - (Math.random() - 0.5) * 0.3;
  
  // Verificar alertas
  if (state.temperature > config.temp_critical) {
    state.alertActive = true;
    state.criticalAlert = true;
    state.alertMessage = `🚨 CRÍTICO: Temperatura ${state.temperature.toFixed(1)}°C`;
  } else if (state.temperature > config.temp_max) {
    state.alertActive = true;
    state.criticalAlert = false;
    state.alertMessage = `⚠️ Advertencia: Temperatura ${state.temperature.toFixed(1)}°C`;
  } else {
    state.alertActive = false;
    state.criticalAlert = false;
    state.alertMessage = "";
  }
  
  console.log(`[RIFT] Temp: ${state.temperature.toFixed(1)}°C | Puerta: ${state.doorOpen ? 'ABIERTA' : 'cerrada'} | Alerta: ${state.alertActive}`);
}, 5000);

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const path = parsedUrl.pathname;
  
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }
  
  // API Status
  if (path === '/api/status') {
    const response = {
      sensor: {
        temp1: state.temp1,
        temp2: state.temp2,
        temp_avg: state.temperature,
        temp_dht: state.tempDHT,
        humidity: state.humidity,
        door_open: state.doorOpen,
        door_open_sec: state.doorOpen ? Math.floor((Date.now() - state.doorOpenSince) / 1000) : 0,
        sensor_count: 2,
        valid: true
      },
      system: {
        alert_active: state.alertActive,
        critical: state.criticalAlert,
        alert_message: state.alertMessage,
        relay_on: state.relayOn,
        internet: true,
        wifi_connected: true,
        ap_mode: false,
        uptime_sec: Math.floor((Date.now() - state.uptime) / 1000),
        total_alerts: state.alertActive ? 1 : 0,
        wifi_rssi: -45,
        simulation_mode: state.simulationMode
      },
      device: {
        id: 'RIFT-01',
        name: 'RIFT Principal',
        ip: '192.168.0.11',
        mdns: 'rift.local'
      },
      location: {
        name: "Campamento Parametican Silver",
        detail: "Cerro Moro, Santa Cruz, Argentina",
        lat: -48.130438,
        lon: -66.652895
      },
      timestamp: new Date().toISOString()
    };
    
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(response));
    return;
  }
  
  // API RIFTs - Lista de todos los RIFTs
  if (path === '/api/rifts') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ rifts: rifts }));
    return;
  }
  
  // API Config GET
  if (path === '/api/config' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(config));
    return;
  }
  
  // API Config POST
  if (path === '/api/config' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const newConfig = JSON.parse(body);
        Object.assign(config, newConfig);
        console.log('[CONFIG] Actualizada:', config);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true }));
      } catch (e) {
        res.writeHead(400);
        res.end('Invalid JSON');
      }
    });
    return;
  }
  
  // API Alert Acknowledge
  if (path === '/api/alert/ack') {
    state.alertActive = false;
    state.relayOn = false;
    console.log('[ALERTA] Silenciada por usuario');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true }));
    return;
  }
  
  // API Test Alert
  if (path === '/api/alert/test') {
    state.alertActive = true;
    state.criticalAlert = true;
    state.alertMessage = "🔔 ALERTA DE PRUEBA";
    state.relayOn = true;
    console.log('[ALERTA] Test activado');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true }));
    return;
  }
  
  // API Relay
  if (path === '/api/relay') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const data = JSON.parse(body);
        state.relayOn = data.state === true || data.state === 'on';
        console.log('[RELAY]', state.relayOn ? 'ON' : 'OFF');
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, relay: state.relayOn }));
      } catch (e) {
        res.writeHead(400);
        res.end('Invalid JSON');
      }
    });
    return;
  }
  
  // Comandos de simulación
  if (path === '/sim/temp' && parsedUrl.query.v) {
    state.temperature = parseFloat(parsedUrl.query.v);
    console.log(`[SIM] Temperatura forzada a ${state.temperature}°C`);
    res.writeHead(200);
    res.end(`Temperatura: ${state.temperature}°C`);
    return;
  }
  
  if (path === '/sim/door') {
    state.doorOpen = !state.doorOpen;
    if (state.doorOpen) state.doorOpenSince = Date.now();
    console.log(`[SIM] Puerta: ${state.doorOpen ? 'ABIERTA' : 'CERRADA'}`);
    res.writeHead(200);
    res.end(`Puerta: ${state.doorOpen ? 'ABIERTA' : 'CERRADA'}`);
    return;
  }
  
  if (path === '/sim/alert') {
    state.alertActive = !state.alertActive;
    state.criticalAlert = state.alertActive;
    state.alertMessage = state.alertActive ? "🚨 ALERTA SIMULADA" : "";
    console.log(`[SIM] Alerta: ${state.alertActive ? 'ACTIVA' : 'INACTIVA'}`);
    res.writeHead(200);
    res.end(`Alerta: ${state.alertActive ? 'ACTIVA' : 'INACTIVA'}`);
    return;
  }
  
  // Página principal con controles
  if (path === '/' || path === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(getControlPage());
    return;
  }
  
  res.writeHead(404);
  res.end('Not Found');
});

function getControlPage() {
  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>🧊 Simulador RIFT Pro</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
      min-height: 100vh; color: #fff; padding: 20px;
    }
    .container { max-width: 900px; margin: 0 auto; }
    h1 { text-align: center; margin-bottom: 10px; font-size: 2.5em; }
    h1 span { background: linear-gradient(90deg, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .subtitle { text-align: center; color: #64748b; margin-bottom: 30px; }
    .card {
      background: rgba(30, 41, 59, 0.8); border-radius: 20px;
      padding: 24px; margin-bottom: 20px; 
      border: 1px solid rgba(255,255,255,0.1);
      transition: transform 0.3s, box-shadow 0.3s;
    }
    .card:hover { transform: translateY(-2px); box-shadow: 0 10px 40px rgba(0,0,0,0.3); }
    .card h2 { margin-bottom: 20px; color: #f1f5f9; font-size: 1.3em; display: flex; align-items: center; gap: 10px; }
    .status-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; }
    @media (max-width: 600px) { .status-grid { grid-template-columns: repeat(2, 1fr); } }
    .status-item { 
      background: linear-gradient(145deg, rgba(0,0,0,0.3), rgba(0,0,0,0.1)); 
      padding: 20px; border-radius: 16px; text-align: center;
      border: 1px solid rgba(255,255,255,0.05);
    }
    .status-item .value { font-size: 2.2em; font-weight: bold; margin: 10px 0; transition: all 0.3s; }
    .status-item .label { color: #94a3b8; font-size: 0.85em; text-transform: uppercase; letter-spacing: 1px; }
    .temp-ok { color: #22d3ee; text-shadow: 0 0 20px rgba(34, 211, 238, 0.5); }
    .temp-warn { color: #fbbf24; text-shadow: 0 0 20px rgba(251, 191, 36, 0.5); animation: pulse-warn 1s infinite; }
    .temp-crit { color: #ef4444; text-shadow: 0 0 20px rgba(239, 68, 68, 0.5); animation: pulse-crit 0.5s infinite; }
    @keyframes pulse-warn { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }
    @keyframes pulse-crit { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }
    .controls { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 15px; }
    @media (max-width: 600px) { .controls { grid-template-columns: 1fr; } }
    button {
      padding: 16px 20px; border: none; border-radius: 14px;
      font-size: 1em; font-weight: 600; cursor: pointer; 
      transition: all 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px;
    }
    button:hover { transform: translateY(-3px); box-shadow: 0 5px 20px rgba(0,0,0,0.3); }
    button:active { transform: translateY(0); }
    .btn-blue { background: linear-gradient(135deg, #3b82f6, #2563eb); color: white; }
    .btn-purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); color: white; }
    .btn-red { background: linear-gradient(135deg, #ef4444, #dc2626); color: white; }
    .btn-green { background: linear-gradient(135deg, #22c55e, #16a34a); color: white; }
    .btn-yellow { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #1e293b; }
    .btn-cyan { background: linear-gradient(135deg, #22d3ee, #06b6d4); color: #1e293b; }
    .btn-gray { background: linear-gradient(135deg, #475569, #334155); color: white; }
    .slider-container { margin: 20px 0; }
    .slider-value { text-align: center; font-size: 3em; font-weight: bold; margin: 10px 0; }
    input[type="range"] { 
      width: 100%; height: 12px; border-radius: 6px; 
      background: linear-gradient(90deg, #22d3ee 0%, #fbbf24 50%, #ef4444 100%);
      -webkit-appearance: none; cursor: pointer;
    }
    input[type="range"]::-webkit-slider-thumb {
      -webkit-appearance: none; width: 28px; height: 28px; border-radius: 50%;
      background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.3); cursor: pointer;
    }
    .info-box { 
      background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(139, 92, 246, 0.2)); 
      padding: 20px; border-radius: 16px; border: 1px solid rgba(59, 130, 246, 0.3);
    }
    .info-box code { 
      background: rgba(0,0,0,0.4); padding: 8px 16px; border-radius: 8px; 
      font-size: 1.2em; font-weight: bold; color: #60a5fa;
    }
    .alert-banner {
      background: linear-gradient(90deg, #ef4444, #dc2626);
      padding: 20px; border-radius: 16px; text-align: center;
      animation: alert-pulse 0.5s infinite alternate; 
      margin-bottom: 20px; display: none;
      box-shadow: 0 0 30px rgba(239, 68, 68, 0.5);
    }
    .alert-banner.active { display: block; }
    .alert-banner h3 { font-size: 1.5em; margin-bottom: 5px; }
    @keyframes alert-pulse { 
      0% { transform: scale(1); box-shadow: 0 0 30px rgba(239, 68, 68, 0.5); } 
      100% { transform: scale(1.02); box-shadow: 0 0 50px rgba(239, 68, 68, 0.8); } 
    }
    .scenarios { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
    @media (max-width: 600px) { .scenarios { grid-template-columns: 1fr; } }
    .scenario-btn { 
      padding: 20px; text-align: left; background: rgba(0,0,0,0.2);
      border: 1px solid rgba(255,255,255,0.1); border-radius: 14px;
    }
    .scenario-btn:hover { background: rgba(255,255,255,0.1); }
    .scenario-btn .title { font-weight: bold; margin-bottom: 5px; }
    .scenario-btn .desc { font-size: 0.85em; color: #94a3b8; }
    .log-container { 
      background: #0f172a; border-radius: 12px; padding: 15px; 
      max-height: 200px; overflow-y: auto; font-family: monospace; font-size: 0.9em;
    }
    .log-entry { padding: 4px 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
    .log-time { color: #64748b; }
    .log-ok { color: #22c55e; }
    .log-warn { color: #fbbf24; }
    .log-error { color: #ef4444; }
    .badge { 
      display: inline-block; padding: 4px 12px; border-radius: 20px; 
      font-size: 0.8em; font-weight: bold; margin-left: 10px;
    }
    .badge-online { background: #22c55e; color: white; }
    .badge-sim { background: #8b5cf6; color: white; }
    .two-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    @media (max-width: 700px) { .two-cols { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <div class="container">
    <h1>🧊 Simulador <span>RIFT Pro</span></h1>
    <p class="subtitle">Panel de Control y Testing - Campamento Parametican Silver</p>
    
    <div class="alert-banner" id="alertBanner">
      <h3>🚨 ¡ALERTA ACTIVA!</h3>
      <div id="alertMsg">Temperatura fuera de rango</div>
      <button class="btn-green" onclick="clearAlert()" style="margin-top:15px;width:auto;display:inline-flex;">
        🔕 SILENCIAR AHORA
      </button>
    </div>
    
    <div class="card">
      <h2>📊 Estado en Tiempo Real <span class="badge badge-online">ONLINE</span><span class="badge badge-sim">SIMULACIÓN</span></h2>
      <div class="status-grid">
        <div class="status-item">
          <div class="label">Temperatura</div>
          <div class="value temp-ok" id="temp">-22.5°C</div>
        </div>
        <div class="status-item">
          <div class="label">Puerta</div>
          <div class="value" id="door">🔒</div>
          <div class="label" id="doorText">Cerrada</div>
        </div>
        <div class="status-item">
          <div class="label">Sirena</div>
          <div class="value" id="relay">⚫</div>
          <div class="label" id="relayText">Apagada</div>
        </div>
        <div class="status-item">
          <div class="label">Uptime</div>
          <div class="value" id="uptime" style="font-size:1.5em">0:00</div>
        </div>
      </div>
    </div>
    
    <div class="two-cols">
      <div class="card">
        <h2>🌡️ Control de Temperatura</h2>
        <div class="slider-container">
          <div class="slider-value temp-ok" id="sliderValue">-22.5°C</div>
          <input type="range" id="tempSlider" min="-40" max="10" step="0.5" value="-22.5">
          <div style="display:flex;justify-content:space-between;color:#64748b;font-size:0.85em;margin-top:5px;">
            <span>-40°C</span><span>-18°C (límite)</span><span>+10°C</span>
          </div>
        </div>
        <div class="controls" style="grid-template-columns: repeat(3, 1fr);">
          <button class="btn-cyan" onclick="setTemp(-25)">❄️ -25°C</button>
          <button class="btn-yellow" onclick="setTemp(-15)">⚠️ -15°C</button>
          <button class="btn-red" onclick="setTemp(-5)">🔥 -5°C</button>
        </div>
      </div>
      
      <div class="card">
        <h2>🎛️ Controles Rápidos</h2>
        <div class="controls" style="grid-template-columns: 1fr;">
          <button class="btn-purple" onclick="toggleDoor()">🚪 Abrir/Cerrar Puerta</button>
          <button class="btn-red" onclick="triggerAlert()">🚨 Activar Alerta</button>
          <button class="btn-green" onclick="clearAlert()">✅ Silenciar Todo</button>
          <button class="btn-gray" onclick="toggleRelay()">🔔 Toggle Sirena</button>
        </div>
      </div>
    </div>
    
    <div class="card">
      <h2>🎬 Escenarios de Prueba</h2>
      <div class="scenarios">
        <button class="scenario-btn" onclick="runScenario('normal')">
          <div class="title">✅ Operación Normal</div>
          <div class="desc">Temp: -22°C, Puerta cerrada, Sin alertas</div>
        </button>
        <button class="scenario-btn" onclick="runScenario('doorOpen')">
          <div class="title">🚪 Puerta Abierta 30s</div>
          <div class="desc">Simula puerta abierta por 30 segundos</div>
        </button>
        <button class="scenario-btn" onclick="runScenario('tempRise')">
          <div class="title">📈 Subida de Temperatura</div>
          <div class="desc">Temp sube gradualmente hasta alerta</div>
        </button>
        <button class="scenario-btn" onclick="runScenario('critical')">
          <div class="title">🚨 Emergencia Crítica</div>
          <div class="desc">Temp: -5°C, Puerta abierta, Alerta máxima</div>
        </button>
        <button class="scenario-btn" onclick="runScenario('recovery')">
          <div class="title">🔄 Recuperación</div>
          <div class="desc">Vuelve a valores normales gradualmente</div>
        </button>
        <button class="scenario-btn" onclick="runScenario('powerCut')">
          <div class="title">⚡ Corte de Energía</div>
          <div class="desc">Simula pérdida de refrigeración</div>
        </button>
      </div>
    </div>
    
    <div class="card">
      <h2>📋 Log de Eventos</h2>
      <div class="log-container" id="logContainer">
        <div class="log-entry"><span class="log-time">[--:--:--]</span> <span class="log-ok">Sistema iniciado</span></div>
      </div>
    </div>
    
    <div class="card">
      <h2>📱 Conectar App Android</h2>
      <div class="info-box">
        <p style="margin-bottom:15px;"><strong>IP del servidor:</strong></p>
        <p><code id="serverIP">192.168.0.11:3000</code></p>
        <p style="margin-top:15px;color:#94a3b8">
          1. Abrí la app <strong>Alerta RIFT</strong> en tu celular<br>
          2. Presioná "Buscar RIFT automáticamente"<br>
          3. O ingresá esta IP manualmente
        </p>
      </div>
    </div>
  </div>
  
  <script>
    const serverIP = window.location.hostname + ':3000';
    document.getElementById('serverIP').textContent = serverIP;
    
    function log(msg, type = 'ok') {
      const container = document.getElementById('logContainer');
      const time = new Date().toLocaleTimeString();
      const entry = document.createElement('div');
      entry.className = 'log-entry';
      entry.innerHTML = '<span class="log-time">[' + time + ']</span> <span class="log-' + type + '">' + msg + '</span>';
      container.insertBefore(entry, container.firstChild);
      if (container.children.length > 50) container.removeChild(container.lastChild);
    }
    
    async function updateStatus() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        
        const temp = data.sensor.temp_avg.toFixed(1);
        const tempEl = document.getElementById('temp');
        const sliderVal = document.getElementById('sliderValue');
        tempEl.textContent = temp + '°C';
        sliderVal.textContent = temp + '°C';
        
        // Colores según temperatura
        tempEl.className = 'value';
        sliderVal.className = 'slider-value';
        if (parseFloat(temp) > -10) {
          tempEl.classList.add('temp-crit');
          sliderVal.classList.add('temp-crit');
        } else if (parseFloat(temp) > -18) {
          tempEl.classList.add('temp-warn');
          sliderVal.classList.add('temp-warn');
        } else {
          tempEl.classList.add('temp-ok');
          sliderVal.classList.add('temp-ok');
        }
        
        // Puerta
        document.getElementById('door').textContent = data.sensor.door_open ? '🔓' : '🔒';
        document.getElementById('doorText').textContent = data.sensor.door_open ? 'ABIERTA' : 'Cerrada';
        document.getElementById('doorText').style.color = data.sensor.door_open ? '#fbbf24' : '#94a3b8';
        
        // Relay
        document.getElementById('relay').textContent = data.system.relay_on ? '🔴' : '⚫';
        document.getElementById('relayText').textContent = data.system.relay_on ? 'ACTIVA' : 'Apagada';
        document.getElementById('relayText').style.color = data.system.relay_on ? '#ef4444' : '#94a3b8';
        
        // Uptime
        document.getElementById('uptime').textContent = formatUptime(data.system.uptime_sec);
        
        // Alerta
        const banner = document.getElementById('alertBanner');
        const alertMsg = document.getElementById('alertMsg');
        if (data.system.alert_active) {
          banner.classList.add('active');
          alertMsg.textContent = data.system.alert_message || 'Alerta activa';
        } else {
          banner.classList.remove('active');
        }
        
        document.getElementById('tempSlider').value = temp;
      } catch (e) {
        console.error('Error:', e);
      }
    }
    
    function formatUptime(sec) {
      const h = Math.floor(sec / 3600);
      const m = Math.floor((sec % 3600) / 60);
      const s = sec % 60;
      if (h > 0) return h + 'h ' + m + 'm';
      if (m > 0) return m + 'm ' + s + 's';
      return s + 's';
    }
    
    document.getElementById('tempSlider').addEventListener('input', async (e) => {
      const val = e.target.value;
      document.getElementById('sliderValue').textContent = val + '°C';
      await fetch('/sim/temp?v=' + val);
    });
    
    async function setTemp(t) {
      document.getElementById('tempSlider').value = t;
      await fetch('/sim/temp?v=' + t);
      log('Temperatura ajustada a ' + t + '°C', t > -18 ? 'warn' : 'ok');
    }
    
    async function toggleDoor() { 
      await fetch('/sim/door'); 
      log('Puerta toggle', 'warn');
    }
    
    async function triggerAlert() { 
      await fetch('/api/alert/test', { method: 'POST' }); 
      log('🚨 ALERTA ACTIVADA', 'error');
    }
    
    async function clearAlert() { 
      await fetch('/api/alert/ack', { method: 'POST' }); 
      log('✅ Alerta silenciada', 'ok');
    }
    
    async function toggleRelay() {
      const res = await fetch('/api/status');
      const data = await res.json();
      await fetch('/api/relay', { 
        method: 'POST', 
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ state: !data.system.relay_on })
      });
      log('Sirena toggle', 'warn');
    }
    
    async function runScenario(name) {
      log('▶️ Ejecutando escenario: ' + name, 'ok');
      
      switch(name) {
        case 'normal':
          await fetch('/sim/temp?v=-22');
          await fetch('/api/alert/ack', { method: 'POST' });
          if ((await (await fetch('/api/status')).json()).sensor.door_open) await fetch('/sim/door');
          log('Sistema en operación normal', 'ok');
          break;
          
        case 'doorOpen':
          await fetch('/sim/door');
          log('Puerta abierta - cerrará en 30s', 'warn');
          setTimeout(async () => {
            await fetch('/sim/door');
            log('Puerta cerrada automáticamente', 'ok');
          }, 30000);
          break;
          
        case 'tempRise':
          log('Iniciando subida de temperatura...', 'warn');
          let t = -22;
          const interval = setInterval(async () => {
            t += 2;
            await fetch('/sim/temp?v=' + t);
            if (t >= -5) {
              clearInterval(interval);
              log('Temperatura crítica alcanzada', 'error');
            }
          }, 2000);
          break;
          
        case 'critical':
          await fetch('/sim/temp?v=-5');
          const status = await (await fetch('/api/status')).json();
          if (!status.sensor.door_open) await fetch('/sim/door');
          await fetch('/api/alert/test', { method: 'POST' });
          log('🚨 EMERGENCIA CRÍTICA SIMULADA', 'error');
          break;
          
        case 'recovery':
          log('Iniciando recuperación...', 'ok');
          await fetch('/api/alert/ack', { method: 'POST' });
          let tr = -5;
          const intRec = setInterval(async () => {
            tr -= 3;
            await fetch('/sim/temp?v=' + tr);
            if (tr <= -22) {
              clearInterval(intRec);
              const s = await (await fetch('/api/status')).json();
              if (s.sensor.door_open) await fetch('/sim/door');
              log('Sistema recuperado', 'ok');
            }
          }, 1500);
          break;
          
        case 'powerCut':
          log('⚡ Simulando corte de energía...', 'error');
          let tp = -22;
          const intPower = setInterval(async () => {
            tp += 1;
            await fetch('/sim/temp?v=' + tp);
            if (tp >= 5) {
              clearInterval(intPower);
              log('Temperatura ambiente alcanzada', 'error');
            }
          }, 3000);
          break;
      }
    }
    
    setInterval(updateStatus, 1000);
    updateStatus();
    log('Panel de control iniciado', 'ok');
  </script>
</body>
</html>`;
}

const PORT = 3000;
const interfaces = require('os').networkInterfaces();
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
  console.log('');
  console.log('========================================================');
  console.log('     SIMULADOR RIFT - Servidor de Prueba Local');
  console.log('========================================================');
  console.log('');
  console.log('  Panel de control: http://localhost:' + PORT);
  console.log('  IP para la app:   ' + localIP + ':' + PORT);
  console.log('');
  console.log('  Comandos de simulacion:');
  console.log('    /sim/temp?v=-15  -> Cambiar temperatura');
  console.log('    /sim/door        -> Toggle puerta');
  console.log('    /sim/alert       -> Toggle alerta');
  console.log('');
  console.log('========================================================');
  console.log('');
});
