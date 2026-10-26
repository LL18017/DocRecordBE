-- ----------------------------------------------------------------------------
-- HU-25, HU-33, HU-30 y HU-32 · Avisos por correo (DRS-94, DRS-100, DRS-29, DRS-99)
-- ----------------------------------------------------------------------------

-- ── 1. Recordatorio de cita, una sola vez (HU-33, criterio 1) ──────────────
-- La marca vive en la propia cita: es lo que permite que el trabajo programado
-- corra cada hora sin repetir el aviso. NULL = todavia no se avisa. Es
-- anulable a proposito: las citas existentes no tienen recordatorio enviado, y
-- eso es exactamente lo que NULL significa.
ALTER TABLE public.citas
    ADD COLUMN recordatorio_enviado_en TIMESTAMP(6) WITHOUT TIME ZONE;

COMMENT ON COLUMN public.citas.recordatorio_enviado_en IS
    'Cuando se mando el recordatorio de 24 horas. NULL si aun no se ha mandado. '
    'Evita que el trabajo programado, que corre cada hora, avise dos veces.';

-- El trabajo busca solo las AGENDADAS sin recordatorio en una ventana de
-- fechas; el indice parcial deja fuera las ya avisadas, que son la mayoria.
CREATE INDEX idx_citas_pendientes_de_recordatorio
    ON public.citas (fecha_hora)
    WHERE recordatorio_enviado_en IS NULL AND estado = 'AGENDADA';

-- ── 2. Estado del ultimo envio de la receta (HU-25, criterio 4) ────────────
-- El estado se lee del ultimo evento PREESCRIPTION_SEND de la consulta; este
-- indice evita recorrer toda la bitacora al listar recetas.
CREATE INDEX idx_events_tipo_ref
    ON public.events (event_type_id, ref);

-- ── 3. Tipos de evento de los avisos de cita (HU-32, criterio 4) ───────────
-- El 5 (APPOINTMENT_CANCELLED) lo siembra data.sql; se repite aqui con ON
-- CONFLICT para que la migracion no dependa de esa carga. El 12 es nuevo y
-- coincide con EventCodeEnums.APPOINTMENT_RESCHEDULED.
INSERT INTO public.event_types (event_type_id, code, description) VALUES
    (5,  'APPOINTMENT_CANCELLED',   'Cancelación de cita'),
    (12, 'APPOINTMENT_RESCHEDULED', 'Reprogramación de cita')
ON CONFLICT DO NOTHING;
