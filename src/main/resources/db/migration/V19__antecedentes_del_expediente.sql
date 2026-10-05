-- ----------------------------------------------------------------------------
-- Antecedentes del expediente: HU-12 (antecedentes patologicos) y HU-13
-- (condiciones hereditarias).
--
-- Las dos son secciones del EXPEDIENTE, asi que cuelgan de `pacientes`
-- (persona_id), igual que consultas y signos_vitales. No de `users`: la
-- mayoria de los pacientes no tiene cuenta en el sistema, y un antecedente que
-- solo se puede registrar a quien tiene usuario deja fuera justo a ellos.
-- ----------------------------------------------------------------------------

-- ── HU-12 · antecedentes_patologicos ───────────────────────────────────────
-- Enfermedades previas, cirugias y hospitalizaciones.
--
-- El tipo es una lista cerrada (nota de diseno de HU-12): con texto libre no
-- se podria filtrar despues, que es para lo que existe. El estado separa lo
-- que sigue presente de lo que ya se resolvio: una hipertension activa y una
-- apendicectomia de hace veinte anos no pesan igual en un diagnostico.
--
-- Quien lo registro apunta a `medicos` y no a `persona`: es un acto clinico,
-- y sale del token (ver MedicoAutenticado), nunca del cuerpo de la peticion.
-- RESTRICT por lo mismo que signos_vitales.enfermera_id en V9: dar de baja a
-- un medico no puede borrar la historia que registro.
CREATE TABLE public.antecedentes_patologicos (
    antecedente_id  BIGSERIAL PRIMARY KEY,

    paciente_id     BIGINT NOT NULL
        REFERENCES public.pacientes (persona_id) ON DELETE CASCADE,

    tipo            VARCHAR(20) NOT NULL
        CONSTRAINT antecedentes_tipo_valido
        CHECK (tipo IN ('ENFERMEDAD', 'CIRUGIA', 'HOSPITALIZACION')),

    descripcion     VARCHAR(500) NOT NULL,

    -- La fecha del antecedente, no la del registro: el dia en que se
    -- diagnostico, se opero o se ingreso.
    fecha           DATE NOT NULL,

    estado          VARCHAR(10) NOT NULL
        CONSTRAINT antecedentes_estado_valido
        CHECK (estado IN ('ACTIVO', 'RESUELTO')),

    medico_id       BIGINT NOT NULL
        REFERENCES public.medicos (persona_id) ON DELETE RESTRICT,

    registrado_en   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- El expediente los lista del mas reciente al mas antiguo (criterio 2).
CREATE INDEX antecedentes_paciente_fecha_idx
    ON public.antecedentes_patologicos (paciente_id, fecha DESC);


-- ── HU-13 · condiciones_hereditarias ──────────────────────────────────────
-- La tabla la creo V18 colgada de users.user_id y con el parentesco como
-- texto libre, tal como venian las entidades de la rama `dev`. Se convierte:
--
--   1. paciente_id en lugar de user_id. Las filas cuyo usuario es paciente se
--      conservan; las demas no pertenecen a ningun expediente -una condicion
--      hereditaria de una cuenta que no es paciente no describe a nadie- y
--      se descartan.
--   2. el parentesco pasa a la lista controlada del criterio 2. Lo que se
--      escribio a mano se traduce cuando se reconoce, y si no, queda como
--      OTRO: se pierde la precision, no la fila.
ALTER TABLE public.condiciones_hereditarias
    ADD COLUMN paciente_id BIGINT
        REFERENCES public.pacientes (persona_id) ON DELETE CASCADE;

UPDATE public.condiciones_hereditarias c
SET paciente_id = u.persona_id
FROM public.users u
JOIN public.pacientes p ON p.persona_id = u.persona_id
WHERE u.user_id = c.user_id;

DELETE FROM public.condiciones_hereditarias WHERE paciente_id IS NULL;

ALTER TABLE public.condiciones_hereditarias ALTER COLUMN paciente_id SET NOT NULL;

DROP INDEX IF EXISTS public.condiciones_hereditarias_user_id_idx;
ALTER TABLE public.condiciones_hereditarias DROP COLUMN user_id;

CREATE INDEX condiciones_hereditarias_paciente_idx
    ON public.condiciones_hereditarias (paciente_id);

UPDATE public.condiciones_hereditarias
SET parentesco = CASE upper(public.sin_tildes(trim(parentesco)))
    WHEN 'PADRE'   THEN 'PADRE'
    WHEN 'MADRE'   THEN 'MADRE'
    WHEN 'ABUELO'  THEN 'ABUELO'
    WHEN 'ABUELA'  THEN 'ABUELA'
    WHEN 'HERMANO' THEN 'HERMANO'
    WHEN 'HERMANA' THEN 'HERMANA'
    ELSE 'OTRO'
END;

ALTER TABLE public.condiciones_hereditarias
    ALTER COLUMN parentesco TYPE VARCHAR(10),
    ADD CONSTRAINT condiciones_parentesco_valido
        CHECK (parentesco IN ('PADRE', 'MADRE', 'ABUELO', 'ABUELA', 'HERMANO', 'HERMANA', 'OTRO'));
