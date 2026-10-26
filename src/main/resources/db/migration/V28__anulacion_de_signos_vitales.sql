-- ----------------------------------------------------------------------------
-- Anulacion de una toma de signos vitales
--
-- HU-16 (DRS-26), criterio 4: "dado una toma ya registrada, cuando intento
-- eliminarla, entonces el sistema no lo permite: solo puedo anularla dejando
-- constancia del motivo".
--
-- ── Por que anular y no borrar ─────────────────────────────────────────────
-- Una toma de constantes es un registro clinico: dice que a esa hora alguien
-- midio esos valores. Si la enfermera se equivoco al teclear, lo honesto no es
-- hacer desaparecer la fila sino dejar escrito que esa toma no vale, por que, y
-- quien lo dijo y cuando. Asi quien audita el expediente ve el error y su
-- correccion, y el medico que ya leyo el valor equivocado puede saber que lo
-- leyo. Un DELETE borraria justo esa huella.
--
-- ── Que se guarda ──────────────────────────────────────────────────────────
--   estado            ACTIVA (toda toma nace asi) o ANULADA.
--   motivo_anulacion  por que se anulo; obligatorio al anular.
--   anulada_en        cuando.
--   anulada_por       quien: una enfermera. RESTRICT, como enfermera_id en V9:
--                     dar de baja a una enfermera no puede borrar la huella de
--                     sus anulaciones.
--
-- Las filas existentes quedan ACTIVA por el DEFAULT: produccion tiene tomas y
-- ninguna esta anulada.
--
-- El CHECK final impide estados incoherentes -- una ANULADA sin motivo, o una
-- ACTIVA con motivo colgando -- aunque alguien escriba en la tabla sin pasar por
-- el servicio.
-- ----------------------------------------------------------------------------
ALTER TABLE public.signos_vitales
    ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    ADD COLUMN motivo_anulacion VARCHAR(500),
    ADD COLUMN anulada_en TIMESTAMP,
    ADD COLUMN anulada_por BIGINT
        REFERENCES public.enfermeras (persona_id) ON DELETE RESTRICT;

ALTER TABLE public.signos_vitales
    ADD CONSTRAINT signos_vitales_estado_check
        CHECK (estado IN ('ACTIVA', 'ANULADA')),
    ADD CONSTRAINT signos_vitales_anulacion_coherente_check
        CHECK (
            (estado = 'ACTIVA'
                AND motivo_anulacion IS NULL AND anulada_en IS NULL AND anulada_por IS NULL)
            OR
            (estado = 'ANULADA'
                AND motivo_anulacion IS NOT NULL AND anulada_en IS NOT NULL AND anulada_por IS NOT NULL)
        );

-- "La ultima toma" ahora es la ultima ACTIVA (una anulada no cuenta). El indice
-- de V9 (paciente_id, tomado_en DESC) sigue sirviendo: el filtro por estado
-- descarta muy pocas filas.
COMMENT ON COLUMN public.signos_vitales.estado IS
    'ACTIVA o ANULADA. Una toma anulada sigue visible pero no cuenta como ultima ni habilita una consulta.';
