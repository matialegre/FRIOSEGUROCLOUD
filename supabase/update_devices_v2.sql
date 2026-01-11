-- ============================================================================
-- ACTUALIZACIÓN DE DISPOSITIVOS - FrioSeguro Multi-Dispositivo
-- Ejecutar en SQL Editor de Supabase
-- ============================================================================

-- Primero, actualizar los dispositivos existentes con el nuevo formato de ID
-- y agregar los que faltan

-- Limpiar dispositivos viejos si existen con formato antiguo
DELETE FROM devices WHERE device_id IN ('REEFER-01', 'REEFER-02', 'REEFER-03', 'REEFER-04', 'REEFER-05');

-- Insertar los 7 dispositivos con el nuevo formato
INSERT INTO devices (device_id, name, location, temp_max, temp_critical, is_online) VALUES
    ('REEFER_01_SCZ', 'Reefer Principal', 'Cerro Moro, Santa Cruz - Sector A', -18.0, -10.0, FALSE),
    ('REEFER_02_SCZ', 'Reefer Carnes', 'Cerro Moro, Santa Cruz - Sector A', -18.0, -10.0, FALSE),
    ('REEFER_03_SCZ', 'Reefer Lácteos', 'Cerro Moro, Santa Cruz - Sector B', -18.0, -10.0, FALSE),
    ('REEFER_04_SCZ', 'Reefer Verduras', 'Cerro Moro, Santa Cruz - Sector B', -18.0, -10.0, FALSE),
    ('REEFER_05_SCZ', 'Reefer Bebidas', 'Cerro Moro, Santa Cruz - Sector C', -18.0, -10.0, FALSE),
    ('REEFER_06_SCZ', 'Reefer Backup', 'Cerro Moro, Santa Cruz - Sector C', -18.0, -10.0, FALSE),
    ('REEFER_DEV_BHI', 'Reefer Desarrollo', 'Bahía Blanca, Buenos Aires - Testing', -18.0, -10.0, FALSE)
ON CONFLICT (device_id) DO UPDATE SET
    name = EXCLUDED.name,
    location = EXCLUDED.location,
    updated_at = NOW();

-- Verificar los dispositivos insertados
SELECT device_id, name, location, is_online, last_seen_at FROM devices ORDER BY device_id;
