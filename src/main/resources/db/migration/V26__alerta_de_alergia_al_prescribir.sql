-- ----------------------------------------------------------------------------
-- HU-24 · Alerta de alergia al prescribir (DRS-93)
--
-- Si el medico receta un medicamento que contiene una sustancia a la que el
-- paciente es alergico, tiene que escribir por que lo hace. Esa justificacion
-- es parte de la receta (es lo que la hace defendible), asi que vive en la
-- propia fila de prescripciones y no en un registro aparte.
-- ----------------------------------------------------------------------------
ALTER TABLE public.prescripciones
    ADD COLUMN justificacion_alergia VARCHAR(500);

COMMENT ON COLUMN public.prescripciones.justificacion_alergia IS
    'Por que se receto pese a una alergia registrada del paciente. NULL si la receta no tuvo coincidencias.';

-- La bitacora: cada receta emitida pese a una alergia deja un evento de este
-- tipo (criterio 5). El id 10 coincide con EventCodeEnums.ALLERGY_ALERT_OVERRIDE.
INSERT INTO public.event_types (event_type_id, code, description)
VALUES (10, 'ALLERGY_ALERT_OVERRIDE', 'Receta emitida pese a una alerta de alergia')
ON CONFLICT (event_type_id) DO NOTHING;
