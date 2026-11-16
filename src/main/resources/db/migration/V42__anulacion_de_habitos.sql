-- ----------------------------------------------------------------------------
-- HU-14 (DRS-86) · DEF-03: un habito se ANULA, no se borra
--
-- Hasta aqui DELETE /api/habitos-estilo-de-vida/{id} borraba la fila y, por el
-- ON DELETE CASCADE de V29, todo su historial de versiones. Un habito es un
-- dato del expediente clinico: si se registro mal, lo honesto es dejar escrito
-- que no vale, por que, quien lo dijo y cuando, igual que las tomas de signos
-- vitales (V28) y las recetas. El historial queda intacto.
--
--   estado            ACTIVO (todo habito nace asi) o ANULADO.
--   motivo_anulacion  por que se anulo (10 a 500 caracteres, lo valida el
--                     servicio).
--   anulado_en        cuando.
--   anulado_por       quien: el autor del habito o un administrador, por eso
--                     apunta a persona y no a medicos/enfermeras (ver V39).
--                     RESTRICT: dar de baja a alguien no borra su huella.
--
-- Las filas existentes quedan ACTIVO por el DEFAULT.
-- ----------------------------------------------------------------------------
ALTER TABLE public.habitos_estilo_de_vida
    ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
    ADD COLUMN motivo_anulacion VARCHAR(500),
    ADD COLUMN anulado_en TIMESTAMP,
    ADD COLUMN anulado_por BIGINT
        REFERENCES public.persona (persona_id) ON DELETE RESTRICT;

ALTER TABLE public.habitos_estilo_de_vida
    ADD CONSTRAINT habitos_estilo_de_vida_estado_check
        CHECK (estado IN ('ACTIVO', 'ANULADO')),
    ADD CONSTRAINT habitos_estilo_de_vida_anulacion_coherente_check
        CHECK (
            (estado = 'ACTIVO'
                AND motivo_anulacion IS NULL AND anulado_en IS NULL AND anulado_por IS NULL)
            OR
            (estado = 'ANULADO'
                AND motivo_anulacion IS NOT NULL AND anulado_en IS NOT NULL AND anulado_por IS NOT NULL)
        );

COMMENT ON COLUMN public.habitos_estilo_de_vida.estado IS
    'ACTIVO o ANULADO. Un habito anulado sigue en el expediente, marcado, con su historial; no se borra.';
