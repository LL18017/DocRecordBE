-- ----------------------------------------------------------------------------
-- DRS-93 (HU-24, criterio 5) y DRS-86 (HU-14, criterios 1 y 2).
-- ----------------------------------------------------------------------------

-- ── HU-24 · Alerta mostrada y decision de desistir ────────────────────────
-- V26 dejo en la bitacora las recetas emitidas pese a la alerta (tipo 10).
-- Falta la otra decision: el medico vio la alerta y NO receto. El id 11
-- coincide con EventCodeEnums.ALLERGY_ALERT_DECLINED.
INSERT INTO public.event_types (event_type_id, code, description)
VALUES (11, 'ALLERGY_ALERT_DECLINED', 'Receta no emitida tras una alerta de alergia')
ON CONFLICT (event_type_id) DO NOTHING;

-- ── HU-14 · Frecuencia, observaciones y rastro de quien/cuando ────────────
-- La descripcion venia mezclando todo. Se separan la frecuencia (obligatoria:
-- "a diario", "3 veces por semana") y las observaciones (libres, opcionales).
-- Las filas existentes no tienen frecuencia: se les pone un texto honesto que
-- dice que no se registro, y el DEFAULT se retira para que todo lo nuevo
-- tenga que traerla.
ALTER TABLE public.habitos_estilo_de_vida
    ADD COLUMN frecuencia VARCHAR(100) NOT NULL DEFAULT 'No registrada',
    ADD COLUMN observaciones VARCHAR(500),
    ADD COLUMN actualizado_en TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN actualizado_por BIGINT
        REFERENCES public.persona (persona_id) ON DELETE RESTRICT;
ALTER TABLE public.habitos_estilo_de_vida ALTER COLUMN frecuencia DROP DEFAULT;

COMMENT ON COLUMN public.habitos_estilo_de_vida.actualizado_por IS
    'Quien lo registro o lo edito por ultima vez. NULL en los habitos anteriores a esta migracion, que no guardaron autor: uno inventado seria peor que ninguno.';

-- ── HU-14, criterio 2 · Historial de versiones ────────────────────────────
-- Al editar un habito, la version que se reemplaza se copia aqui antes de
-- sobrescribirla: el valor clinico esta en ver que el paciente paso de fumar
-- a diario a no fumar, no solo en su estado actual. Cada fila es una version
-- ANTERIOR (con su propio autor y fecha) mas quien la reemplazo y cuando.
-- Si el habito se elimina, su historial se va con el: sin habito no hay
-- evolucion que ver.
CREATE TABLE public.habitos_estilo_de_vida_historial (
    historial_id    BIGSERIAL PRIMARY KEY,
    habito_id       BIGINT NOT NULL
        REFERENCES public.habitos_estilo_de_vida (id) ON DELETE CASCADE,
    tipo_habito_id  INTEGER NOT NULL REFERENCES public.tipos_habito (id),
    descripcion     VARCHAR(255) NOT NULL,
    nivel           VARCHAR(10) NOT NULL,
    frecuencia      VARCHAR(100) NOT NULL,
    observaciones   VARCHAR(500),
    actualizado_en  TIMESTAMP NOT NULL,
    actualizado_por BIGINT REFERENCES public.persona (persona_id) ON DELETE RESTRICT,
    reemplazado_en  TIMESTAMP NOT NULL DEFAULT NOW(),
    reemplazado_por BIGINT REFERENCES public.persona (persona_id) ON DELETE RESTRICT
);

CREATE INDEX idx_habito_historial_habito
    ON public.habitos_estilo_de_vida_historial (habito_id, historial_id DESC);
