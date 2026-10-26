-- ----------------------------------------------------------------------------
-- HU-19 · HU-22 · Consulta con exploracion e indicaciones; receta completa y
-- anulable (DRS-53, DRS-27)
--
-- Una sola migracion para lo de las dos historias: las dos tocan el mismo
-- documento clinico (la consulta y la receta que sale de ella).
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- consultas · exploracion fisica e indicaciones (HU-19, criterio 1)
--
-- Ambas son opcionales: el motivo y el diagnostico son lo que hace valida a una
-- consulta; lo demas la completa. Por eso admiten NULL y no se rellenan en las
-- filas existentes.
-- ----------------------------------------------------------------------------
ALTER TABLE public.consultas
    ADD COLUMN exploracion_fisica TEXT,
    ADD COLUMN indicaciones       TEXT;


-- ----------------------------------------------------------------------------
-- prescripcion_medicamentos · dosis, via, frecuencia y duracion obligatorias
-- (HU-22, criterio 2)
--
-- Las recetas ya emitidas pueden tener esos campos en NULL (V6 los permitia).
-- Se rellenan con 'No especificada' ANTES de poner el NOT NULL: es lo que ya
-- mostraba el PDF para esos huecos, asi que el papel no cambia, y borrar o
-- inventar un dato en una receta entregada seria peor que decir que no consta.
-- ----------------------------------------------------------------------------
ALTER TABLE public.prescripcion_medicamentos
    ADD COLUMN via_administracion VARCHAR(60);

UPDATE public.prescripcion_medicamentos
SET dosis      = COALESCE(NULLIF(BTRIM(dosis), ''),      'No especificada'),
    frecuencia = COALESCE(NULLIF(BTRIM(frecuencia), ''), 'No especificada'),
    duracion   = COALESCE(NULLIF(BTRIM(duracion), ''),   'No especificada'),
    via_administracion = 'No especificada';

ALTER TABLE public.prescripcion_medicamentos
    ALTER COLUMN dosis              SET NOT NULL,
    ALTER COLUMN frecuencia         SET NOT NULL,
    ALTER COLUMN duracion           SET NOT NULL,
    ALTER COLUMN via_administracion SET NOT NULL;


-- ----------------------------------------------------------------------------
-- prescripciones · anulacion logica (HU-22, criterio 3)
--
-- Una receta entregada no se borra ni se edita: se anula, y la anulacion deja
-- rastro (motivo, quien y cuando). Las recetas existentes quedan EMITIDA.
-- ----------------------------------------------------------------------------
ALTER TABLE public.prescripciones
    ADD COLUMN estado           VARCHAR(10) NOT NULL DEFAULT 'EMITIDA',
    ADD COLUMN motivo_anulacion VARCHAR(500),
    ADD COLUMN anulada_en       TIMESTAMP,
    ADD COLUMN anulada_por      INTEGER
        REFERENCES public.users (user_id);

ALTER TABLE public.prescripciones
    ADD CONSTRAINT prescripciones_estado_check
        CHECK (estado IN ('EMITIDA', 'ANULADA')),
    -- La base tambien exige el rastro: una anulacion sin motivo, autor o
    -- fecha no se puede guardar ni por SQL directo.
    ADD CONSTRAINT prescripciones_anulacion_completa_check
        CHECK (estado = 'EMITIDA'
               OR (motivo_anulacion IS NOT NULL
                   AND anulada_en IS NOT NULL
                   AND anulada_por IS NOT NULL));
