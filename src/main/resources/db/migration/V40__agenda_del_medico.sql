-- ----------------------------------------------------------------------------
-- HU-31 · Agenda del medico.
--
-- 1. citas.motivo: por que se agenda la cita (texto libre, opcional). La
--    agenda del medico lo muestra junto a la sede. No es motivo_cancelacion,
--    que solo existe en las canceladas.
--
-- 2. consultas.cita_id: la cita desde la que se inicio la consulta. Iniciar
--    la consulta desde la cita la deja ATENDIDA en la misma transaccion, y
--    esta columna guarda la relacion. Anulable: las consultas sin cita (todas
--    las anteriores a esta version y las que se registran sin agenda) siguen
--    igual. UNIQUE: una cita origina a lo sumo una consulta; en PostgreSQL los
--    NULL no chocan entre si.
--
-- El nuevo estado NO_ASISTIO no necesita cambios: citas.estado es
-- VARCHAR(30) sin CHECK (lo pone el servicio, ver V24).
-- ----------------------------------------------------------------------------

ALTER TABLE public.citas ADD COLUMN motivo VARCHAR(500);

ALTER TABLE public.consultas
    ADD COLUMN cita_id INTEGER
        REFERENCES public.citas (cita_id) ON DELETE RESTRICT;

ALTER TABLE public.consultas
    ADD CONSTRAINT consultas_cita_id_uq UNIQUE (cita_id);
