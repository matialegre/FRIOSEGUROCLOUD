/*
 * html_ui.h - Página HTML embebida
 * Sistema Monitoreo Reefer v3.0
 */

#ifndef HTML_UI_H
#define HTML_UI_H

String getEmbeddedHTML() {
  return R"rawliteral(
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Alerta REEFER</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#0f172a;min-height:100vh;color:#fff;padding:16px}
    .container{max-width:500px;margin:0 auto}
    h1{text-align:center;margin-bottom:4px;font-size:1.8em;color:#60a5fa}
    .subtitle{text-align:center;color:#64748b;margin-bottom:20px;font-size:.9em}
    .card{background:#1e293b;border-radius:12px;padding:16px;margin-bottom:12px}
    .card h2{margin-bottom:12px;color:#fff;font-size:1em}
    .temp-big{text-align:center;font-size:4em;font-weight:bold;margin:16px 0}
    .temp-ok{color:#22d3ee}
    .temp-warn{color:#fbbf24}
    .temp-crit{color:#ef4444;animation:pulse .5s infinite}
    @keyframes pulse{0%,100%{opacity:1}50%{opacity:.7}}
    .status-row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #334155}
    .status-row:last-child{border-bottom:none}
    .status-label{color:#94a3b8}
    .status-value{font-weight:bold}
    .alert-banner{background:#ef4444;padding:16px;border-radius:12px;text-align:center;margin-bottom:12px;display:none}
    .alert-banner.active{display:block}
    button{width:100%;padding:14px;border:none;border-radius:10px;font-size:1em;font-weight:600;cursor:pointer;margin-top:8px}
    .btn-green{background:#22c55e;color:#fff}
    .btn-blue{background:#3b82f6;color:#fff}
    .btn-orange{background:#f97316;color:#fff}
    .btn-red{background:#ef4444;color:#fff}
    .input-row{margin-bottom:12px}
    .input-row label{display:block;margin-bottom:6px;color:#94a3b8;font-size:.85em}
    .input-group{display:flex;gap:8px}
    .input-group input{flex:1;padding:12px;border-radius:8px;border:1px solid #334155;background:#0f172a;color:#fff;font-size:1em}
    .input-group span{color:#64748b;padding:12px 0;min-width:40px}
    .footer{text-align:center;padding:20px;color:#64748b;font-size:.75em}
  </style>
</head>
<body>
  <div class="container">
    <h1>❄️ Alerta REEFER</h1>
    <p class="subtitle">PANDEMONIUM TECH × PAN AMERICAN SILVER</p>
    
    <div class="alert-banner" id="alertBanner">
      <div style="font-size:1.2em;font-weight:bold">🚨 ALERTA ACTIVA</div>
      <div id="alertMsg" style="margin:8px 0">Temperatura crítica</div>
      <button class="btn-green" onclick="stopAlert()">🛑 DETENER ALERTA</button>
    </div>
    
    <div class="card" id="mainTempCard">
      <h2 id="mainCardTitle">🌡️ TEMPERATURA</h2>
      <div class="temp-big temp-ok" id="temp">--.-°C</div>
      <div id="defrostBigStatus" style="display:none;text-align:center;margin-top:10px;color:#90CAF9;font-size:0.9em">
        Alertas suspendidas durante descongelamiento
      </div>
      <div id="cooldownBigStatus" style="display:none;text-align:center;margin-top:10px;color:#FFB74D;font-size:0.9em">
        Alertas suspendidas - Sistema estabilizándose
      </div>
    </div>
    
    <div class="card">
      <h2>📊 Estado del Sistema</h2>
      <div class="status-row"><span class="status-label">Sirena</span><span class="status-value" id="relayStatus">Apagada</span></div>
      <div class="status-row"><span class="status-label">Señal Descongelamiento</span><span class="status-value" id="defrostSignal">--</span></div>
      <div class="status-row"><span class="status-label">Supabase</span><span class="status-value" id="supabaseStatus">--</span></div>
      <div class="status-row"><span class="status-label">Uptime</span><span class="status-value" id="uptime">--</span></div>
      <div class="status-row"><span class="status-label">WiFi</span><span class="status-value" id="wifiRssi">-- dBm</span></div>
      <div class="status-row"><span class="status-label">Internet</span><span class="status-value" id="internetStatus">--</span></div>
      <div class="status-row"><span class="status-label">IP</span><span class="status-value" id="deviceIp">--</span></div>
    </div>
    
    <div class="card">
      <h2>📋 Valores Configurados</h2>
      <div class="status-row"><span class="status-label">🌡️ Temp. Crítica</span><span class="status-value" id="cfgTempCrit">--°C</span></div>
      <div class="status-row"><span class="status-label">⏱️ Tiempo espera</span><span class="status-value" id="cfgAlertDelay">-- min</span></div>
      <div class="status-row"><span class="status-label">🧊 Post-descongelación</span><span class="status-value" id="cfgDefrostCooldown">-- min</span></div>
      <div class="status-row"><span class="status-label">🔌 Relé Descong.</span><span class="status-value" id="cfgDefrostRelay">--</span></div>
    </div>
    
    <div class="card">
      <h2>⚙️ Configuración</h2>
      <div class="input-row">
        <label>🌡️ Temperatura Crítica (°C)</label>
        <div class="input-group"><input type="number" id="inTempCrit" value="-10" step="0.5"><span>°C</span></div>
      </div>
      <div class="input-row">
        <label>⏱️ Tiempo de espera (minutos)</label>
        <div class="input-group"><input type="number" id="inAlertDelay" value="5" step="1" min="1"><span>min</span></div>
      </div>
      <div class="input-row">
        <label>🧊 Post-descongelación (minutos)</label>
        <div class="input-group"><input type="number" id="inDefrostCooldown" value="30" step="5" min="5"><span>min</span></div>
      </div>
      <button class="btn-blue" onclick="saveConfig()">💾 GUARDAR</button>
    </div>
    
    <div class="card" id="defrostCard">
      <h2>🧊 Modo Descongelamiento</h2>
      <div id="defrostStatus" style="display:none;background:#f97316;padding:10px;border-radius:8px;margin-bottom:10px;text-align:center">
        ⏱️ Activo: <strong id="defrostTime">0</strong> min
      </div>
      <button class="btn-orange" id="btnDefrost" onclick="toggleDefrost()">🧊 ACTIVAR DESCONGELAMIENTO</button>
    </div>
    
    <div class="card" id="controlsCard" style="display:none">
      <h2>🎛️ Controles de Emergencia</h2>
      <button class="btn-green" onclick="silenceAlert()" id="btnSilence">🔕 SILENCIAR ALERTA</button>
      <button class="btn-red" onclick="turnOffRelay()" style="margin-top:8px" id="btnRelayOff">🔌 APAGAR RELÉ/SIRENA</button>
    </div>
    
    <div class="card">
      <h2>📱 Acciones</h2>
      <button class="btn-blue" onclick="testTelegram()">📲 Probar Telegram</button>
      <button class="btn-red" onclick="resetWifi()" style="margin-top:8px">📡 Reset WiFi</button>
    </div>
    
    <div class="footer">
      <div>Última actualización: <span id="lastUpdate">--</span></div>
      <div style="margin-top:8px"><strong>PANDEMONIUM TECH</strong> × <strong>PAN AMERICAN SILVER</strong></div>
    </div>
  </div>
  
  <script>
    let alertActive=false,defrostMode=false;
    
    async function fetchStatus(){
      try{
        const r=await fetch('/api/status');
        const d=await r.json();
        const t=d.sensor.temp_avg.toFixed(1);
        const tempEl=document.getElementById('temp');
        tempEl.textContent=t+'°C';
        tempEl.className='temp-big';
        if(parseFloat(t)>-10)tempEl.classList.add('temp-crit');
        else if(parseFloat(t)>-18)tempEl.classList.add('temp-warn');
        else tempEl.classList.add('temp-ok');
        
        alertActive=d.system.alert_active;
        const alertAck=d.system.alert_acknowledged||false;
        document.getElementById('relayStatus').textContent=(alertActive&&!alertAck)?'PRENDIDA':'Apagada';
        document.getElementById('relayStatus').style.color=(alertActive&&!alertAck)?'#ef4444':'#94a3b8';
        
        defrostMode=d.system.defrost_mode||false;
        const cooldownMode=d.system.cooldown_mode||false;
        const cooldownSec=d.system.cooldown_remaining_sec||0;
        
        document.getElementById('defrostSignal').textContent=defrostMode?'ACTIVA':(cooldownMode?'COOLDOWN':'Normal');
        document.getElementById('defrostSignal').style.color=defrostMode?'#f97316':(cooldownMode?'#FF9800':'#22c55e');
        
        // Mostrar estado grande según modo
        const mainCard=document.getElementById('mainTempCard');
        const mainTitle=document.getElementById('mainCardTitle');
        const defrostBig=document.getElementById('defrostBigStatus');
        const cooldownBig=document.getElementById('cooldownBigStatus');
        
        if(defrostMode){
          mainTitle.textContent='🧊 MODO DESCONGELAMIENTO';
          mainTitle.style.color='#2196F3';
          tempEl.textContent='ACTIVO';
          tempEl.style.color='#2196F3';
          tempEl.style.fontSize='2.5em';
          tempEl.className='temp-big';
          defrostBig.style.display='block';
          cooldownBig.style.display='none';
          mainCard.style.borderLeft='4px solid #2196F3';
        }else if(cooldownMode && cooldownSec>0){
          const minR=Math.floor(cooldownSec/60);
          const secR=cooldownSec%60;
          mainTitle.textContent='⏳ ESPERANDO POST-DESCONGELAMIENTO';
          mainTitle.style.color='#FF9800';
          tempEl.textContent=minR+':'+(secR<10?'0':'')+secR;
          tempEl.style.color='#FF9800';
          tempEl.style.fontSize='3em';
          tempEl.className='temp-big';
          defrostBig.style.display='none';
          cooldownBig.style.display='block';
          mainCard.style.borderLeft='4px solid #FF9800';
        }else{
          mainTitle.textContent='🌡️ TEMPERATURA';
          mainTitle.style.color='#fff';
          tempEl.style.fontSize='4em';
          defrostBig.style.display='none';
          cooldownBig.style.display='none';
          mainCard.style.borderLeft='none';
        }
        
        document.getElementById('supabaseStatus').textContent=d.system.supabase_enabled?'✓ Activo':'Deshabilitado';
        document.getElementById('supabaseStatus').style.color=d.system.supabase_enabled?'#22c55e':'#64748b';
        
        document.getElementById('uptime').textContent=formatUptime(d.system.uptime_sec);
        document.getElementById('wifiRssi').textContent=d.system.wifi_rssi+' dBm';
        document.getElementById('internetStatus').textContent=d.system.internet?'✓ Online':'✗ Offline';
        document.getElementById('internetStatus').style.color=d.system.internet?'#22c55e':'#ef4444';
        document.getElementById('deviceIp').textContent=d.device.ip;
        document.getElementById('lastUpdate').textContent=new Date().toLocaleTimeString();
        
        const banner=document.getElementById('alertBanner');
        if(alertActive){banner.classList.add('active');document.getElementById('alertMsg').textContent=d.system.alert_message;}
        else{banner.classList.remove('active');}
        
        // Mostrar controles de emergencia si hay alerta o relé activo
        const relayOn=d.system.relay_on||false;
        const controlsCard=document.getElementById('controlsCard');
        if(alertActive||relayOn){controlsCard.style.display='block';}
        else{controlsCard.style.display='none';}
        
        document.getElementById('defrostStatus').style.display=defrostMode?'block':'none';
        document.getElementById('defrostTime').textContent=d.system.defrost_minutes||0;
        document.getElementById('btnDefrost').textContent=defrostMode?'✅ DESACTIVAR':'🧊 ACTIVAR DESCONGELAMIENTO';
        document.getElementById('btnDefrost').className=defrostMode?'btn-green':'btn-orange';
      }catch(e){console.error(e);}
    }
    
    async function loadConfig(){
      try{
        const r=await fetch('/api/config');
        const c=await r.json();
        document.getElementById('inTempCrit').value=c.temp_critical;
        document.getElementById('inAlertDelay').value=Math.round(c.alert_delay_sec/60);
        document.getElementById('inDefrostCooldown').value=Math.round((c.defrost_cooldown_sec||1800)/60);
        document.getElementById('cfgTempCrit').textContent=c.temp_critical+'°C';
        document.getElementById('cfgAlertDelay').textContent=Math.round(c.alert_delay_sec/60)+' min';
        document.getElementById('cfgDefrostCooldown').textContent=Math.round((c.defrost_cooldown_sec||1800)/60)+' min';
        document.getElementById('cfgDefrostRelay').textContent=c.defrost_relay_nc?'Normal Cerrado (NC)':'Normal Abierto (NO)';
      }catch(e){}
    }
    
    async function saveConfig(){
      const tc=parseFloat(document.getElementById('inTempCrit').value);
      const ad=parseInt(document.getElementById('inAlertDelay').value)*60;
      const dc=parseInt(document.getElementById('inDefrostCooldown').value)*60;
      await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({temp_critical:tc,alert_delay_sec:ad,defrost_cooldown_sec:dc})});
      alert('✅ Configuración guardada');
      loadConfig();
    }
    
    function formatUptime(s){const h=Math.floor(s/3600),m=Math.floor((s%3600)/60);if(h>0)return h+'h '+m+'m';if(m>0)return m+'m '+(s%60)+'s';return s+'s';}
    
    async function stopAlert(){await fetch('/api/alert/ack',{method:'POST'});fetchStatus();}
    async function silenceAlert(){await fetch('/api/alert/ack',{method:'POST'});alert('✅ Alerta silenciada');fetchStatus();}
    async function turnOffRelay(){
      const r=await fetch('/api/relay',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({state:false})});
      alert(r.ok?'✅ Relé APAGADO':'❌ Error');
      fetchStatus();
    }
    async function testTelegram(){const r=await fetch('/api/telegram/test',{method:'POST'});alert(r.ok?'✅ Mensaje enviado':'❌ Error');}
    async function resetWifi(){if(confirm('¿Resetear WiFi?'))await fetch('/api/wifi/reset',{method:'POST'});}
    async function toggleDefrost(){
      if(confirm(defrostMode?'¿Desactivar descongelamiento?':'¿Activar descongelamiento?\n\nLas alertas se deshabilitarán.')){
        await fetch('/api/defrost',{method:'POST'});
        fetchStatus();
      }
    }
    
    setInterval(fetchStatus,2000);
    fetchStatus();
    loadConfig();
  </script>
</body>
</html>
)rawliteral";
}

#endif
