-- Agregar columnas de cooldown a la tabla readings
-- Ejecutar en Supabase SQL Editor

-- Agregar columna cooldown_mode (boolean)
ALTER TABLE readings 
ADD COLUMN IF NOT EXISTS cooldown_mode BOOLEAN DEFAULT false;

-- Agregar columna cooldown_remaining_sec (integer)
ALTER TABLE readings 
ADD COLUMN IF NOT EXISTS cooldown_remaining_sec INTEGER DEFAULT 0;

-- Verificar que las columnas se agregaron
SELECT column_name, data_type, column_default 
FROM information_schema.columns 
WHERE table_name = 'readings' 
AND column_name IN ('cooldown_mode', 'cooldown_remaining_sec');
