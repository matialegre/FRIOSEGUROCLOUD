-- ============================================================================
-- SCHEMA SUPABASE v2.0 - Sistema Monitoreo Reefer
-- Campamento Parametican Silver - Pan American Silver
-- ============================================================================
-- Diseñado para 5+ dispositivos con historial completo de todos los datos
-- Ejecutar en SQL Editor de Supabase
-- ============================================================================

-- ============================================================================
-- 1. TABLA DEVICES - Registro de todos los dispositivos
-- ============================================================================
CREATE TABLE IF NOT EXISTS devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id VARCHAR(50) UNIQUE NOT NULL,      -- "REEFER-01", "REEFER-02", etc.
    name VARCHAR(100) NOT NULL,                  -- "Reefer Principal"
    location VARCHAR(200),                       -- "Campamento Base - Sector A"
    location_lat DECIMAL(10, 7),                 -- Latitud GPS
    location_lon DECIMAL(10, 7),                 -- Longitud GPS
    
    -- Configuración
    temp_max DECIMAL(5,2) DEFAULT -15.0,
    temp_critical DECIMAL(5,2) DEFAULT -10.0,
    alert_delay_sec INTEGER DEFAULT 300,
    door_open_max_sec INTEGER DEFAULT 120,
    defrost_cooldown_sec INTEGER DEFAULT 1800,
    
    -- Estado actual
    is_online BOOLEAN DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ,
    firmware_version VARCHAR(20),
    wifi_rssi INTEGER,
    ip_address VARCHAR(50),
    
    -- Flags de funcionalidades habilitadas
    telegram_enabled BOOLEAN DEFAULT TRUE,
    supabase_enabled BOOLEAN DEFAULT TRUE,
    sim800_enabled BOOLEAN DEFAULT FALSE,
    power_monitor_enabled BOOLEAN DEFAULT FALSE,
    current_sensor_enabled BOOLEAN DEFAULT FALSE,
    multi_door_enabled BOOLEAN DEFAULT FALSE,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_devices_device_id ON devices(device_id);
CREATE INDEX idx_devices_online ON devices(is_online);

-- ============================================================================
-- 2. TABLA READINGS - Lecturas de sensores (cada 5 segundos)
-- ============================================================================
-- Esta es la tabla principal de datos, se llenará rápido
-- Considerar particionamiento por fecha si crece mucho
CREATE TABLE IF NOT EXISTS readings (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    -- Temperaturas
    temp1 DECIMAL(5,2),                          -- Sensor DS18B20 #1
    temp2 DECIMAL(5,2),                          -- Sensor DS18B20 #2
    temp3 DECIMAL(5,2),                          -- Sensor DS18B20 #3 (futuro)
    temp4 DECIMAL(5,2),                          -- Sensor DS18B20 #4 (futuro)
    temp_avg DECIMAL(5,2),                       -- Promedio calculado
    temp_dht DECIMAL(5,2),                       -- DHT22 temperatura ambiente
    humidity DECIMAL(5,2),                       -- DHT22 humedad %
    
    -- Estado de puertas (hasta 4)
    door1_open BOOLEAN DEFAULT FALSE,
    door2_open BOOLEAN DEFAULT FALSE,
    door3_open BOOLEAN DEFAULT FALSE,
    door4_open BOOLEAN DEFAULT FALSE,
    
    -- Estado eléctrico
    ac_power BOOLEAN DEFAULT TRUE,               -- Hay luz de red
    battery_voltage DECIMAL(4,2),                -- Voltaje batería backup
    battery_percent INTEGER,                     -- % batería
    
    -- Corriente del compresor
    current_amps DECIMAL(6,2),                   -- Corriente actual
    compressor_running BOOLEAN,                  -- Compresor encendido
    
    -- Estado del sistema
    relay_on BOOLEAN DEFAULT FALSE,              -- Sirena activa
    buzzer_on BOOLEAN DEFAULT FALSE,
    alert_active BOOLEAN DEFAULT FALSE,
    defrost_mode BOOLEAN DEFAULT FALSE,
    simulation_mode BOOLEAN DEFAULT FALSE,
    
    -- Conectividad
    wifi_rssi INTEGER,                           -- Señal WiFi dBm
    gsm_signal INTEGER,                          -- Señal GSM (0-31)
    
    -- Metadata
    uptime_sec BIGINT,                           -- Segundos desde boot
    free_heap INTEGER,                           -- Memoria libre
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Índices optimizados para consultas frecuentes
CREATE INDEX idx_readings_device ON readings(device_id);
CREATE INDEX idx_readings_created ON readings(created_at DESC);
CREATE INDEX idx_readings_device_date ON readings(device_id, created_at DESC);
CREATE INDEX idx_readings_alerts ON readings(device_id, alert_active) WHERE alert_active = TRUE;

-- ============================================================================
-- 3. TABLA ALERTS - Historial de alertas
-- ============================================================================
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    alert_type VARCHAR(50) NOT NULL,             -- 'temperature', 'door', 'power', 'current', 'offline', 'maintenance'
    severity VARCHAR(20) NOT NULL,               -- 'info', 'warning', 'critical', 'emergency'
    message TEXT,
    
    -- Datos al momento de la alerta
    temperature DECIMAL(5,2),
    current_amps DECIMAL(6,2),
    door_open_seconds INTEGER,
    
    -- Resolución
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(100),                -- Usuario o "auto"
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT,
    
    -- Notificaciones enviadas
    telegram_sent BOOLEAN DEFAULT FALSE,
    sms_sent BOOLEAN DEFAULT FALSE,
    push_sent BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alerts_device ON alerts(device_id);
CREATE INDEX IF NOT EXISTS idx_alerts_type ON alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_active ON alerts(resolved) WHERE resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_alerts_date ON alerts(created_at DESC);

-- ============================================================================
-- 4. TABLA POWER_EVENTS - Eventos de corte/restauración de luz
-- ============================================================================
CREATE TABLE IF NOT EXISTS power_events (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    event_type VARCHAR(20) NOT NULL,             -- 'power_lost', 'power_restored'
    
    -- Para eventos de restauración
    outage_duration_sec INTEGER,                 -- Duración del corte
    battery_used_percent INTEGER,                -- % batería consumida
    min_battery_voltage DECIMAL(4,2),            -- Voltaje mínimo durante corte
    
    -- Notificaciones
    sms_sent BOOLEAN DEFAULT FALSE,
    telegram_sent BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_power_device ON power_events(device_id);
CREATE INDEX idx_power_date ON power_events(created_at DESC);

-- ============================================================================
-- 5. TABLA DOOR_EVENTS - Eventos de apertura/cierre de puertas
-- ============================================================================
CREATE TABLE IF NOT EXISTS door_events (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    door_number INTEGER NOT NULL DEFAULT 1,      -- 1-4
    door_name VARCHAR(50),                       -- "Principal", "Lateral", etc.
    event_type VARCHAR(20) NOT NULL,             -- 'opened', 'closed'
    
    -- Para eventos de cierre
    open_duration_sec INTEGER,                   -- Cuánto tiempo estuvo abierta
    temp_at_open DECIMAL(5,2),                   -- Temp cuando se abrió
    temp_at_close DECIMAL(5,2),                  -- Temp cuando se cerró
    temp_rise DECIMAL(5,2),                      -- Aumento de temperatura
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_door_device ON door_events(device_id);
CREATE INDEX idx_door_number ON door_events(door_number);
CREATE INDEX idx_door_date ON door_events(created_at DESC);

-- ============================================================================
-- 6. TABLA MAINTENANCE_LOGS - Registro de mantenimiento preventivo
-- ============================================================================
CREATE TABLE IF NOT EXISTS maintenance_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    maintenance_type VARCHAR(50) NOT NULL,       -- 'compressor_hours', 'filter_change', 'refrigerant_check', 'inspection'
    
    -- Datos del compresor
    compressor_hours DECIMAL(10,1),              -- Horas totales
    compressor_starts INTEGER,                   -- Cantidad de arranques
    max_current_ever DECIMAL(6,2),               -- Máxima corriente registrada
    
    -- Notas
    notes TEXT,
    performed_by VARCHAR(100),
    
    -- Próximo mantenimiento
    next_maintenance_at TIMESTAMPTZ,
    next_maintenance_hours DECIMAL(10,1),
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_maintenance_device ON maintenance_logs(device_id);
CREATE INDEX idx_maintenance_type ON maintenance_logs(maintenance_type);

-- ============================================================================
-- 7. TABLA COMMANDS - Comandos remotos pendientes/ejecutados
-- ============================================================================
CREATE TABLE IF NOT EXISTS commands (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    command VARCHAR(50) NOT NULL,                -- 'restart', 'defrost_on', 'defrost_off', 'ack_alert', 'update_config'
    parameters JSONB,                            -- Parámetros del comando
    
    -- Estado
    status VARCHAR(20) DEFAULT 'pending',        -- 'pending', 'sent', 'executed', 'failed'
    sent_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    result TEXT,
    
    -- Origen
    source VARCHAR(50),                          -- 'web', 'app', 'api', 'scheduled'
    created_by VARCHAR(100),
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_commands_device ON commands(device_id);
CREATE INDEX idx_commands_pending ON commands(status) WHERE status = 'pending';

-- ============================================================================
-- 8. TABLA CONFIG_HISTORY - Historial de cambios de configuración
-- ============================================================================
CREATE TABLE IF NOT EXISTS config_history (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    config_key VARCHAR(50) NOT NULL,             -- 'temp_critical', 'alert_delay', etc.
    old_value TEXT,
    new_value TEXT,
    
    changed_by VARCHAR(100),                     -- Usuario o 'device'
    change_source VARCHAR(50),                   -- 'web', 'app', 'serial', 'api'
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_config_device ON config_history(device_id);
CREATE INDEX idx_config_date ON config_history(created_at DESC);

-- ============================================================================
-- 9. TABLA DEFROST_SESSIONS - Sesiones de descongelamiento
-- ============================================================================
CREATE TABLE IF NOT EXISTS defrost_sessions (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_minutes INTEGER,
    
    -- Temperaturas
    temp_at_start DECIMAL(5,2),
    temp_at_end DECIMAL(5,2),
    temp_max_during DECIMAL(5,2),
    
    -- Quién lo activó
    triggered_by VARCHAR(50),                    -- 'manual', 'scheduled', 'relay_signal'
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_defrost_device ON defrost_sessions(device_id);
CREATE INDEX idx_defrost_date ON defrost_sessions(created_at DESC);

-- ============================================================================
-- 10. TABLA DAILY_STATS - Estadísticas diarias (agregadas)
-- ============================================================================
-- Esta tabla se llena con un cron job para no tener que calcular siempre
CREATE TABLE IF NOT EXISTS daily_stats (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    stats_date DATE NOT NULL,
    
    -- Temperatura
    temp_avg DECIMAL(5,2),
    temp_min DECIMAL(5,2),
    temp_max DECIMAL(5,2),
    temp_readings_count INTEGER,
    
    -- Alertas
    alerts_count INTEGER DEFAULT 0,
    critical_alerts_count INTEGER DEFAULT 0,
    
    -- Puertas
    door_opens_count INTEGER DEFAULT 0,
    door_total_open_minutes INTEGER DEFAULT 0,
    
    -- Energía
    power_outages_count INTEGER DEFAULT 0,
    power_outage_total_minutes INTEGER DEFAULT 0,
    
    -- Compresor
    compressor_hours DECIMAL(5,2),
    compressor_starts INTEGER,
    avg_current DECIMAL(6,2),
    max_current DECIMAL(6,2),
    
    -- Uptime
    online_percent DECIMAL(5,2),
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(device_id, stats_date)
);

CREATE INDEX idx_daily_device ON daily_stats(device_id);
CREATE INDEX idx_daily_date ON daily_stats(stats_date DESC);

-- ============================================================================
-- VISTAS ÚTILES
-- ============================================================================

-- Vista: Estado actual de todos los dispositivos
CREATE OR REPLACE VIEW v_devices_status AS
SELECT 
    d.device_id,
    d.name,
    d.location,
    d.is_online,
    d.last_seen_at,
    d.wifi_rssi,
    r.temp_avg AS current_temp,
    r.humidity AS current_humidity,
    r.door1_open,
    r.ac_power,
    r.alert_active,
    r.defrost_mode,
    r.compressor_running,
    r.current_amps,
    EXTRACT(EPOCH FROM (NOW() - d.last_seen_at)) AS seconds_since_seen
FROM devices d
LEFT JOIN LATERAL (
    SELECT * FROM readings 
    WHERE device_id = d.device_id 
    ORDER BY created_at DESC 
    LIMIT 1
) r ON true;

-- Vista: Alertas activas
CREATE OR REPLACE VIEW v_active_alerts AS
SELECT 
    a.*,
    d.name AS device_name,
    d.location
FROM alerts a
JOIN devices d ON d.device_id = a.device_id
WHERE a.resolved = FALSE
ORDER BY 
    CASE a.severity 
        WHEN 'emergency' THEN 1 
        WHEN 'critical' THEN 2 
        WHEN 'warning' THEN 3 
        ELSE 4 
    END,
    a.created_at DESC;

-- Vista: Últimas lecturas por dispositivo
CREATE OR REPLACE VIEW v_latest_readings AS
SELECT DISTINCT ON (device_id)
    *
FROM readings
ORDER BY device_id, created_at DESC;

-- Vista: Resumen de hoy por dispositivo
CREATE OR REPLACE VIEW v_today_summary AS
SELECT 
    device_id,
    COUNT(*) AS readings_count,
    AVG(temp_avg) AS avg_temp,
    MIN(temp_avg) AS min_temp,
    MAX(temp_avg) AS max_temp,
    COUNT(*) FILTER (WHERE alert_active) AS alert_readings,
    COUNT(*) FILTER (WHERE door1_open) AS door_open_readings,
    COUNT(*) FILTER (WHERE NOT ac_power) AS power_out_readings
FROM readings
WHERE created_at >= CURRENT_DATE
GROUP BY device_id;

-- ============================================================================
-- FUNCIONES
-- ============================================================================

-- Función: Obtener historial de un dispositivo
CREATE OR REPLACE FUNCTION get_device_history(
    p_device_id VARCHAR,
    p_hours INTEGER DEFAULT 24
)
RETURNS TABLE (
    reading_time TIMESTAMPTZ,
    temp_avg DECIMAL,
    humidity DECIMAL,
    door_open BOOLEAN,
    ac_power BOOLEAN,
    alert_active BOOLEAN,
    current_amps DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        r.created_at AS reading_time,
        r.temp_avg,
        r.humidity,
        r.door1_open,
        r.ac_power,
        r.alert_active,
        r.current_amps
    FROM readings r
    WHERE r.device_id = p_device_id
    AND r.created_at > NOW() - (p_hours || ' hours')::INTERVAL
    ORDER BY r.created_at DESC
    LIMIT 10000;
END;
$$ LANGUAGE plpgsql;

-- Función: Calcular estadísticas diarias (para cron)
CREATE OR REPLACE FUNCTION calculate_daily_stats(p_date DATE DEFAULT CURRENT_DATE - 1)
RETURNS void AS $$
BEGIN
    INSERT INTO daily_stats (
        device_id, stats_date, temp_avg, temp_min, temp_max, temp_readings_count,
        alerts_count, critical_alerts_count, door_opens_count
    )
    SELECT 
        device_id,
        p_date AS stats_date,
        AVG(temp_avg),
        MIN(temp_avg),
        MAX(temp_avg),
        COUNT(*),
        0, 0, 0  -- Se actualizan después
    FROM readings
    WHERE DATE(created_at) = p_date
    GROUP BY device_id
    ON CONFLICT (device_id, stats_date) DO UPDATE SET
        temp_avg = EXCLUDED.temp_avg,
        temp_min = EXCLUDED.temp_min,
        temp_max = EXCLUDED.temp_max,
        temp_readings_count = EXCLUDED.temp_readings_count;
END;
$$ LANGUAGE plpgsql;

-- Función: Marcar dispositivo como online
CREATE OR REPLACE FUNCTION update_device_online(p_device_id VARCHAR)
RETURNS void AS $$
BEGIN
    UPDATE devices 
    SET is_online = TRUE, last_seen_at = NOW(), updated_at = NOW()
    WHERE device_id = p_device_id;
    
    -- Si no existe, crearlo
    IF NOT FOUND THEN
        INSERT INTO devices (device_id, name, is_online, last_seen_at)
        VALUES (p_device_id, p_device_id, TRUE, NOW());
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Trigger: Actualizar updated_at en devices
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_devices_updated
    BEFORE UPDATE ON devices
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

-- Trigger: Actualizar last_seen cuando llega una lectura
CREATE OR REPLACE FUNCTION update_device_last_seen()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE devices 
    SET last_seen_at = NOW(), is_online = TRUE
    WHERE device_id = NEW.device_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_readings_update_device
    AFTER INSERT ON readings
    FOR EACH ROW
    EXECUTE FUNCTION update_device_last_seen();

-- ============================================================================
-- POLÍTICAS RLS (Row Level Security)
-- ============================================================================

ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE power_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE door_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE maintenance_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE config_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE defrost_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE daily_stats ENABLE ROW LEVEL SECURITY;

-- Políticas para acceso con API key (anon)
CREATE POLICY "anon_select_devices" ON devices FOR SELECT USING (true);
CREATE POLICY "anon_insert_devices" ON devices FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_devices" ON devices FOR UPDATE USING (true);

CREATE POLICY "anon_select_readings" ON readings FOR SELECT USING (true);
CREATE POLICY "anon_insert_readings" ON readings FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_alerts" ON alerts FOR SELECT USING (true);
CREATE POLICY "anon_insert_alerts" ON alerts FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_alerts" ON alerts FOR UPDATE USING (true);

CREATE POLICY "anon_select_power" ON power_events FOR SELECT USING (true);
CREATE POLICY "anon_insert_power" ON power_events FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_door" ON door_events FOR SELECT USING (true);
CREATE POLICY "anon_insert_door" ON door_events FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_commands" ON commands FOR SELECT USING (true);
CREATE POLICY "anon_insert_commands" ON commands FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_commands" ON commands FOR UPDATE USING (true);

CREATE POLICY "anon_select_maintenance" ON maintenance_logs FOR SELECT USING (true);
CREATE POLICY "anon_insert_maintenance" ON maintenance_logs FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_config" ON config_history FOR SELECT USING (true);
CREATE POLICY "anon_insert_config" ON config_history FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_defrost" ON defrost_sessions FOR SELECT USING (true);
CREATE POLICY "anon_insert_defrost" ON defrost_sessions FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_defrost" ON defrost_sessions FOR UPDATE USING (true);

CREATE POLICY "anon_select_daily" ON daily_stats FOR SELECT USING (true);
CREATE POLICY "anon_insert_daily" ON daily_stats FOR INSERT WITH CHECK (true);

-- ============================================================================
-- DATOS INICIALES - Dispositivos
-- ============================================================================
INSERT INTO devices (device_id, name, location) VALUES
    ('REEFER-01', 'Reefer Principal', 'Campamento Base - Sector A'),
    ('REEFER-02', 'Reefer Carnes', 'Campamento Base - Sector A'),
    ('REEFER-03', 'Reefer Lácteos', 'Campamento Base - Sector B'),
    ('REEFER-04', 'Reefer Verduras', 'Campamento Base - Sector B'),
    ('REEFER-05', 'Reefer Bebidas', 'Campamento Base - Sector C')
ON CONFLICT (device_id) DO NOTHING;

-- ============================================================================
-- COMENTARIOS
-- ============================================================================
COMMENT ON TABLE devices IS 'Registro de todos los dispositivos de monitoreo';
COMMENT ON TABLE readings IS 'Lecturas de sensores cada 5 segundos - tabla principal';
COMMENT ON TABLE alerts IS 'Historial completo de alertas';
COMMENT ON TABLE power_events IS 'Eventos de corte y restauración de energía';
COMMENT ON TABLE door_events IS 'Eventos de apertura/cierre de puertas';
COMMENT ON TABLE maintenance_logs IS 'Registro de mantenimiento preventivo';
COMMENT ON TABLE commands IS 'Comandos remotos para los dispositivos';
COMMENT ON TABLE config_history IS 'Historial de cambios de configuración';
COMMENT ON TABLE defrost_sessions IS 'Sesiones de descongelamiento';
COMMENT ON TABLE daily_stats IS 'Estadísticas agregadas por día';
