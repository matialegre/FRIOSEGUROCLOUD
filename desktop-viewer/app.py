"""
FrioSeguro Desktop Viewer
Aplicación de escritorio para visualizar la base de datos de Supabase
Pan American Silver × Pandemonium Tech
"""

import requests
import json
import os
import sys
import io
import csv
from datetime import datetime, timedelta
from flask import Flask, render_template, jsonify, request, Response, send_file
import webbrowser
import threading
import time

# Para PyInstaller - encontrar la carpeta de templates
def resource_path(relative_path):
    """Obtener path absoluto para recursos (compatible con PyInstaller)"""
    try:
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(os.path.dirname(__file__))
    return os.path.join(base_path, relative_path)

template_folder = resource_path('templates')
app = Flask(__name__, template_folder=template_folder)

# Configuración de Supabase
SUPABASE_URL = "https://xhdeacnwdzvkivfjzard.supabase.co"
SUPABASE_KEY = "sb_publishable_JhTUv1X2LHMBVILUaysJ3g_Ho11zu-Q"

HEADERS = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json"
}

def api_get(endpoint):
    """Realizar GET a Supabase"""
    try:
        response = requests.get(f"{SUPABASE_URL}{endpoint}", headers=HEADERS, timeout=10)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        print(f"Error API: {e}")
        return []

@app.route('/')
def index():
    """Página principal"""
    return render_template('index.html')

@app.route('/api/devices')
def get_devices():
    """Obtener todos los dispositivos"""
    devices = api_get("/rest/v1/devices?select=*&order=device_id")
    return jsonify(devices)

@app.route('/api/readings')
def get_readings():
    """Obtener últimas lecturas con filtros"""
    device_id = request.args.get('device_id', '')
    limit = request.args.get('limit', '100')
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    
    endpoint = f"/rest/v1/readings?select=*&order=created_at.desc&limit={limit}"
    if device_id:
        endpoint += f"&device_id=eq.{device_id}"
    if date_from:
        endpoint += f"&created_at=gte.{date_from}T00:00:00"
    if date_to:
        endpoint += f"&created_at=lte.{date_to}T23:59:59"
    
    readings = api_get(endpoint)
    return jsonify(readings)

@app.route('/api/alerts')
def get_alerts():
    """Obtener alertas con filtros"""
    resolved = request.args.get('resolved', '')
    device_id = request.args.get('device_id', '')
    limit = request.args.get('limit', '50')
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    
    endpoint = f"/rest/v1/alerts?select=*&order=created_at.desc&limit={limit}"
    if resolved == 'false':
        endpoint += "&resolved=eq.false"
    elif resolved == 'true':
        endpoint += "&resolved=eq.true"
    if device_id:
        endpoint += f"&device_id=eq.{device_id}"
    if date_from:
        endpoint += f"&created_at=gte.{date_from}T00:00:00"
    if date_to:
        endpoint += f"&created_at=lte.{date_to}T23:59:59"
    
    alerts = api_get(endpoint)
    return jsonify(alerts)

@app.route('/api/stats')
def get_stats():
    """Obtener estadísticas generales"""
    devices = api_get("/rest/v1/devices?select=*")
    readings = api_get("/rest/v1/readings?select=*&order=created_at.desc&limit=1000")
    alerts = api_get("/rest/v1/alerts?select=*&resolved=eq.false")
    
    online_count = sum(1 for d in devices if d.get('is_online'))
    
    return jsonify({
        "total_devices": len(devices),
        "online_devices": online_count,
        "total_readings": len(readings),
        "active_alerts": len(alerts)
    })

@app.route('/api/export/excel')
def export_excel():
    """Exportar lecturas a CSV (compatible con Excel)"""
    device_id = request.args.get('device_id', '')
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    limit = request.args.get('limit', '5000')
    
    endpoint = f"/rest/v1/readings?select=*&order=created_at.desc&limit={limit}"
    if device_id:
        endpoint += f"&device_id=eq.{device_id}"
    if date_from:
        endpoint += f"&created_at=gte.{date_from}T00:00:00"
    if date_to:
        endpoint += f"&created_at=lte.{date_to}T23:59:59"
    
    readings = api_get(endpoint)
    
    # Crear CSV con BOM para Excel
    output = io.StringIO()
    output.write('\ufeff')  # BOM para Excel reconozca UTF-8
    writer = csv.writer(output, delimiter=';')
    
    # Header completo
    writer.writerow([
        'Fecha/Hora', 'Dispositivo', 'Temp Promedio (°C)', 'Temp 1 (°C)', 'Temp 2 (°C)',
        'Humedad (%)', 'Puerta', 'Sirena/Relay', 'Alerta', 'Defrost', 'Uptime (seg)',
        'AC Power', 'WiFi RSSI'
    ])
    
    # Data
    for r in readings:
        writer.writerow([
            r.get('created_at', ''),
            r.get('device_id', ''),
            r.get('temp_avg', ''),
            r.get('temp1', ''),
            r.get('temp2', ''),
            r.get('humidity', ''),
            'ABIERTA' if r.get('door1_open') else 'Cerrada',
            'ACTIVA' if r.get('relay_on') else 'OFF',
            'ACTIVA' if r.get('alert_active') else 'OK',
            'ACTIVO' if r.get('defrost_mode') else 'Inactivo',
            r.get('uptime_sec', ''),
            'Sí' if r.get('ac_power') else 'No',
            r.get('wifi_rssi', '')
        ])
    
    output.seek(0)
    
    filename = f"FrioSeguro_Lecturas_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
    
    return Response(
        output.getvalue(),
        mimetype='text/csv; charset=utf-8',
        headers={'Content-Disposition': f'attachment; filename={filename}'}
    )

@app.route('/api/export/alerts-excel')
def export_alerts_excel():
    """Exportar alertas a CSV"""
    device_id = request.args.get('device_id', '')
    resolved = request.args.get('resolved', '')
    date_from = request.args.get('date_from', '')
    date_to = request.args.get('date_to', '')
    limit = '1000'
    
    endpoint = f"/rest/v1/alerts?select=*&order=created_at.desc&limit={limit}"
    if device_id:
        endpoint += f"&device_id=eq.{device_id}"
    if resolved == 'false':
        endpoint += "&resolved=eq.false"
    elif resolved == 'true':
        endpoint += "&resolved=eq.true"
    if date_from:
        endpoint += f"&created_at=gte.{date_from}T00:00:00"
    if date_to:
        endpoint += f"&created_at=lte.{date_to}T23:59:59"
    
    alerts = api_get(endpoint)
    
    output = io.StringIO()
    writer = csv.writer(output, delimiter=';')
    
    writer.writerow([
        'Fecha/Hora', 'Dispositivo', 'Tipo', 'Severidad', 'Mensaje',
        'Reconocida', 'Resuelta', 'Fecha Resolución'
    ])
    
    for a in alerts:
        writer.writerow([
            a.get('created_at', ''),
            a.get('device_id', ''),
            a.get('alert_type', ''),
            a.get('severity', ''),
            a.get('message', ''),
            'Sí' if a.get('acknowledged') else 'No',
            'Sí' if a.get('resolved') else 'No',
            a.get('resolved_at', '')
        ])
    
    output.seek(0)
    filename = f"alertas_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
    
    return Response(
        output.getvalue(),
        mimetype='text/csv',
        headers={'Content-Disposition': f'attachment; filename={filename}'}
    )

def open_browser():
    """Abrir navegador después de que el servidor inicie"""
    time.sleep(1.5)
    webbrowser.open('http://127.0.0.1:8080')

if __name__ == '__main__':
    import webbrowser
    import threading
    
    def open_browser():
        import time
        time.sleep(1.5)
        webbrowser.open('http://127.0.0.1:9000')
    
    print("\n" + "="*60)
    print("FrioSeguro Desktop Viewer")
    print("Pan American Silver x Pandemonium Tech")
    print("="*60)
    print("\nAbriendo en http://127.0.0.1:9000")
    print("Presiona Ctrl+C para cerrar\n")
    
    threading.Thread(target=open_browser, daemon=True).start()
    app.run(host='127.0.0.1', port=9000, debug=False, threaded=True)
