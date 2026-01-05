-- ============================================
-- SCHEMA PARA SISTEMA UNIFICADO RIFT
-- Campamento Parametican Silver
-- ============================================

-- Tabla principal de lecturas de temperatura
CREATE TABLE IF NOT EXISTS temperature_readings (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL DEFAULT 'ESP32_MAIN',
    temperature DECIMAL(5,2) NOT NULL,
    door_open BOOLEAN DEFAULT FALSE,
    relay_active BOOLEAN DEFAULT FALSE,
    alert_level TEXT DEFAULT 'normal', -- normal, warning, critical
    rssi INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Índice para consultas por fecha
CREATE INDEX IF NOT EXISTS idx_temp_created_at ON temperature_readings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_temp_device ON temperature_readings(device_id);

-- Tabla de alertas
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL DEFAULT 'ESP32_MAIN',
    alert_type TEXT NOT NULL, -- temperature_warning, temperature_critical, door_open
    temperature DECIMAL(5,2),
    message TEXT,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_ack ON alerts(acknowledged);

-- Tabla de configuración
CREATE TABLE IF NOT EXISTS device_config (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT UNIQUE NOT NULL DEFAULT 'ESP32_MAIN',
    warning_temp DECIMAL(5,2) DEFAULT -18.0,
    critical_temp DECIMAL(5,2) DEFAULT -10.0,
    alert_delay_minutes INTEGER DEFAULT 5,
    door_alert_minutes INTEGER DEFAULT 3,
    telegram_enabled BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insertar configuración por defecto
INSERT INTO device_config (device_id) VALUES ('ESP32_MAIN') ON CONFLICT (device_id) DO NOTHING;

-- Tabla de eventos de puerta
CREATE TABLE IF NOT EXISTS door_events (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL DEFAULT 'ESP32_MAIN',
    event_type TEXT NOT NULL, -- opened, closed
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_door_created_at ON door_events(created_at DESC);

-- Vista para estadísticas diarias
CREATE OR REPLACE VIEW daily_stats AS
SELECT 
    DATE(created_at) as date,
    device_id,
    MIN(temperature) as min_temp,
    MAX(temperature) as max_temp,
    AVG(temperature)::DECIMAL(5,2) as avg_temp,
    COUNT(*) as readings_count,
    SUM(CASE WHEN door_open THEN 1 ELSE 0 END) as door_open_count,
    SUM(CASE WHEN alert_level != 'normal' THEN 1 ELSE 0 END) as alert_count
FROM temperature_readings
GROUP BY DATE(created_at), device_id
ORDER BY date DESC;

-- Función para obtener historial
CREATE OR REPLACE FUNCTION get_temperature_history(
    p_device_id TEXT DEFAULT 'ESP32_MAIN',
    p_hours INTEGER DEFAULT 24
)
RETURNS TABLE (
    timestamp TIMESTAMPTZ,
    temperature DECIMAL(5,2),
    door_open BOOLEAN,
    alert_level TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        created_at,
        tr.temperature,
        tr.door_open,
        tr.alert_level
    FROM temperature_readings tr
    WHERE tr.device_id = p_device_id
    AND tr.created_at > NOW() - (p_hours || ' hours')::INTERVAL
    ORDER BY tr.created_at DESC
    LIMIT 1000;
END;
$$ LANGUAGE plpgsql;

-- Función para limpiar datos viejos (más de 90 días)
CREATE OR REPLACE FUNCTION cleanup_old_data()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM temperature_readings WHERE created_at < NOW() - INTERVAL '90 days';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    DELETE FROM door_events WHERE created_at < NOW() - INTERVAL '90 days';
    DELETE FROM alerts WHERE created_at < NOW() - INTERVAL '90 days' AND acknowledged = TRUE;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- RLS (Row Level Security) - Habilitar acceso público para lectura
ALTER TABLE temperature_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE door_events ENABLE ROW LEVEL SECURITY;

-- Políticas para permitir acceso con anon key
CREATE POLICY "Allow public read" ON temperature_readings FOR SELECT USING (true);
CREATE POLICY "Allow public insert" ON temperature_readings FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public read alerts" ON alerts FOR SELECT USING (true);
CREATE POLICY "Allow public insert alerts" ON alerts FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update alerts" ON alerts FOR UPDATE USING (true);

CREATE POLICY "Allow public read config" ON device_config FOR SELECT USING (true);
CREATE POLICY "Allow public update config" ON device_config FOR UPDATE USING (true);

CREATE POLICY "Allow public read door" ON door_events FOR SELECT USING (true);
CREATE POLICY "Allow public insert door" ON door_events FOR INSERT WITH CHECK (true);
