-- ============================================================================
-- SCHEMA SUPABASE - Campamento Parametican Silver
-- ============================================================================
-- Ejecutar este SQL en el SQL Editor de Supabase para crear las tablas
-- ============================================================================

-- Tabla principal de logs de temperatura
CREATE TABLE IF NOT EXISTS temperature_logs (
    id BIGSERIAL PRIMARY KEY,
    rift_id INTEGER NOT NULL,
    rift_name VARCHAR(50),
    temperature DECIMAL(5,2),
    temp_sensor1 DECIMAL(5,2),
    temp_sensor2 DECIMAL(5,2),
    door_open BOOLEAN DEFAULT FALSE,
    door_open_duration INTEGER DEFAULT 0,
    rssi INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Índices para consultas rápidas
CREATE INDEX idx_temp_logs_rift ON temperature_logs(rift_id);
CREATE INDEX idx_temp_logs_created ON temperature_logs(created_at);
CREATE INDEX idx_temp_logs_rift_date ON temperature_logs(rift_id, created_at);

-- Tabla de alertas
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    rift_id INTEGER NOT NULL,
    rift_name VARCHAR(50),
    alert_type VARCHAR(50) NOT NULL, -- 'temperature', 'door', 'offline', 'critical'
    message TEXT,
    temperature DECIMAL(5,2),
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_alerts_rift ON alerts(rift_id);
CREATE INDEX idx_alerts_resolved ON alerts(resolved);

-- Tabla de configuración de RIFTs
CREATE TABLE IF NOT EXISTS rifts (
    id INTEGER PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    location VARCHAR(100),
    temp_max DECIMAL(5,2) DEFAULT -18.0,
    temp_critical DECIMAL(5,2) DEFAULT -10.0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insertar los 6 RIFTs
INSERT INTO rifts (id, name, location) VALUES
    (1, 'RIFT-01', 'Deposito Principal'),
    (2, 'RIFT-02', 'Deposito Carnes'),
    (3, 'RIFT-03', 'Deposito Lacteos'),
    (4, 'RIFT-04', 'Deposito Verduras'),
    (5, 'RIFT-05', 'Deposito Bebidas'),
    (6, 'RIFT-06', 'Deposito Reserva')
ON CONFLICT (id) DO NOTHING;

-- Tabla de eventos de puerta
CREATE TABLE IF NOT EXISTS door_events (
    id BIGSERIAL PRIMARY KEY,
    rift_id INTEGER NOT NULL,
    event_type VARCHAR(20) NOT NULL, -- 'open', 'close'
    duration_seconds INTEGER, -- Solo para eventos 'close'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_door_events_rift ON door_events(rift_id);
CREATE INDEX idx_door_events_date ON door_events(created_at);

-- Tabla de estado del sistema (para monitoreo)
CREATE TABLE IF NOT EXISTS system_status (
    id BIGSERIAL PRIMARY KEY,
    receptor_ip VARCHAR(50),
    uptime_seconds BIGINT,
    total_data_received BIGINT,
    total_alerts_sent BIGINT,
    internet_available BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- VISTAS ÚTILES
-- ============================================================================

-- Vista de últimas temperaturas por RIFT
CREATE OR REPLACE VIEW latest_temperatures AS
SELECT DISTINCT ON (rift_id)
    rift_id,
    rift_name,
    temperature,
    door_open,
    created_at
FROM temperature_logs
ORDER BY rift_id, created_at DESC;

-- Vista de promedios diarios
CREATE OR REPLACE VIEW daily_averages AS
SELECT 
    rift_id,
    DATE(created_at) as date,
    AVG(temperature) as avg_temp,
    MIN(temperature) as min_temp,
    MAX(temperature) as max_temp,
    COUNT(*) as readings
FROM temperature_logs
WHERE temperature IS NOT NULL
GROUP BY rift_id, DATE(created_at)
ORDER BY date DESC;

-- Vista de alertas activas
CREATE OR REPLACE VIEW active_alerts AS
SELECT * FROM alerts
WHERE resolved = FALSE
ORDER BY created_at DESC;

-- ============================================================================
-- FUNCIONES
-- ============================================================================

-- Función para obtener historial de un RIFT
CREATE OR REPLACE FUNCTION get_rift_history(
    p_rift_id INTEGER,
    p_period VARCHAR DEFAULT 'day'
)
RETURNS TABLE (
    timestamp TIMESTAMPTZ,
    temperature DECIMAL,
    door_open BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        tl.created_at,
        tl.temperature,
        tl.door_open
    FROM temperature_logs tl
    WHERE tl.rift_id = p_rift_id
    AND tl.created_at > CASE p_period
        WHEN 'hour' THEN NOW() - INTERVAL '1 hour'
        WHEN 'day' THEN NOW() - INTERVAL '1 day'
        WHEN 'week' THEN NOW() - INTERVAL '1 week'
        WHEN 'month' THEN NOW() - INTERVAL '1 month'
        ELSE NOW() - INTERVAL '1 day'
    END
    ORDER BY tl.created_at DESC
    LIMIT 1000;
END;
$$ LANGUAGE plpgsql;

-- Función para estadísticas de un RIFT
CREATE OR REPLACE FUNCTION get_rift_stats(p_rift_id INTEGER)
RETURNS TABLE (
    total_readings BIGINT,
    avg_temperature DECIMAL,
    min_temperature DECIMAL,
    max_temperature DECIMAL,
    door_open_count BIGINT,
    last_reading TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::BIGINT,
        AVG(temperature),
        MIN(temperature),
        MAX(temperature),
        COUNT(*) FILTER (WHERE door_open = TRUE)::BIGINT,
        MAX(created_at)
    FROM temperature_logs
    WHERE rift_id = p_rift_id
    AND created_at > NOW() - INTERVAL '24 hours';
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- POLÍTICAS RLS (Row Level Security)
-- ============================================================================

-- Habilitar RLS en las tablas
ALTER TABLE temperature_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE door_events ENABLE ROW LEVEL SECURITY;

-- Política para permitir lectura pública (con API key)
CREATE POLICY "Allow public read" ON temperature_logs FOR SELECT USING (true);
CREATE POLICY "Allow public insert" ON temperature_logs FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public read alerts" ON alerts FOR SELECT USING (true);
CREATE POLICY "Allow public insert alerts" ON alerts FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update alerts" ON alerts FOR UPDATE USING (true);

CREATE POLICY "Allow public read door" ON door_events FOR SELECT USING (true);
CREATE POLICY "Allow public insert door" ON door_events FOR INSERT WITH CHECK (true);

-- ============================================================================
-- TRIGGER para actualizar timestamp
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER rifts_updated_at
    BEFORE UPDATE ON rifts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();
