-- ----------------------------------------------------------------------------
-- HU-11 · Alergias del paciente (DRS-83).
--
-- La tabla la creo V18 colgada de users.user_id y con todo como texto libre,
-- tal como venia la entidad de la rama `dev`. Es la misma conversion que V19
-- le hizo a condiciones_hereditarias, por las mismas razones:
--
--   1. paciente_id en lugar de user_id. Una alergia es parte del EXPEDIENTE,
--      y la mayoria de los pacientes no tiene cuenta en el sistema. Las filas
--      cuyo usuario es paciente se conservan; las demas no pertenecen a ningun
--      expediente y se descartan.
--   2. la severidad pasa a lista cerrada (LEVE, MODERADA, SEVERA). Con texto
--      libre no se puede destacar "las severas" en la ficha (criterio 2) ni,
--      mas adelante, alertar al prescribir (HU-24).
--   3. nombre -> sustancia y reaccion_reportada -> reaccion: es lo que la
--      historia pide registrar. La columna `tipo` (medicamento, alimento...)
--      no forma parte de la historia y se retira.
--   4. fecha de deteccion y auditoria: quien registro y cuando, y -porque
--      eliminar es baja logica- quien la dio de baja y cuando (criterio 4).
-- ----------------------------------------------------------------------------

-- ── 1. Del usuario al paciente ────────────────────────────────────────────
ALTER TABLE public.alergias
    ADD COLUMN paciente_id BIGINT
        REFERENCES public.pacientes (persona_id) ON DELETE CASCADE;

UPDATE public.alergias a
SET paciente_id = u.persona_id
FROM public.users u
JOIN public.pacientes p ON p.persona_id = u.persona_id
WHERE u.user_id = a.user_id;

DELETE FROM public.alergias WHERE paciente_id IS NULL;

ALTER TABLE public.alergias ALTER COLUMN paciente_id SET NOT NULL;

DROP INDEX IF EXISTS public.alergias_user_id_idx;
ALTER TABLE public.alergias DROP COLUMN user_id;

-- ── 2. Severidad de lista cerrada ─────────────────────────────────────────
-- Lo escrito a mano se traduce cuando se reconoce. Lo que no se reconoce
-- queda como SEVERA y no como LEVE: con una alergia, equivocarse hacia el
-- lado de la precaucion cuesta una advertencia de mas; equivocarse hacia el
-- otro lado puede costar una reaccion grave. Quien la revise puede bajarla.
UPDATE public.alergias
SET severidad = CASE upper(public.sin_tildes(trim(severidad)))
    WHEN 'LEVE'     THEN 'LEVE'
    WHEN 'BAJA'     THEN 'LEVE'
    WHEN 'MODERADA' THEN 'MODERADA'
    WHEN 'MODERADO' THEN 'MODERADA'
    WHEN 'MEDIA'    THEN 'MODERADA'
    WHEN 'SEVERA'   THEN 'SEVERA'
    WHEN 'SEVERO'   THEN 'SEVERA'
    WHEN 'GRAVE'    THEN 'SEVERA'
    WHEN 'ALTA'     THEN 'SEVERA'
    ELSE 'SEVERA'
END;

ALTER TABLE public.alergias
    ALTER COLUMN severidad TYPE VARCHAR(10),
    ADD CONSTRAINT alergias_severidad_valida
        CHECK (severidad IN ('LEVE', 'MODERADA', 'SEVERA'));

-- ── 3. Sustancia y reaccion ───────────────────────────────────────────────
ALTER TABLE public.alergias RENAME COLUMN nombre TO sustancia;
ALTER TABLE public.alergias RENAME COLUMN reaccion_reportada TO reaccion;
ALTER TABLE public.alergias DROP COLUMN tipo;

-- ── 4. Fecha de deteccion y auditoria ─────────────────────────────────────
-- Las filas que vienen de V18 no tienen fecha de deteccion. Se les pone la de
-- esta migracion: no es la real, pero es cierta como cota -ese dia ya se
-- sabia- y la columna puede ser NOT NULL para todo lo que se registre despues.
ALTER TABLE public.alergias
    ADD COLUMN fecha_deteccion DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE public.alergias ALTER COLUMN fecha_deteccion DROP DEFAULT;

-- Quien la registro apunta a `persona` y no a `medicos` como en HU-12: aqui
-- registran el medico Y la enfermera, y lo que los dos comparten es la
-- persona. El servicio solo deja pasar a quien tiene fila en `medicos` o en
-- `enfermeras` (ver AlergiaService), asi que la persona siempre es personal
-- clinico. RESTRICT por lo mismo que en V9 y V19: dar de baja a quien la
-- registro no puede borrar el rastro.
--
-- Es NULL solo en las filas heredadas de V18, que nunca guardaron autor. Un
-- autor inventado seria peor que ninguno.
ALTER TABLE public.alergias
    ADD COLUMN registrada_por BIGINT
        REFERENCES public.persona (persona_id) ON DELETE RESTRICT,
    ADD COLUMN registrada_en TIMESTAMP NOT NULL DEFAULT NOW();

-- Eliminar es baja logica: la fila se queda y se anota quien y cuando. Una
-- alergia que desaparece sin dejar rastro es justo lo que no puede pasar en
-- un expediente: si alguien la quito por error, tiene que poder verse.
ALTER TABLE public.alergias
    ADD COLUMN eliminada_por BIGINT
        REFERENCES public.persona (persona_id) ON DELETE RESTRICT,
    ADD COLUMN eliminada_en TIMESTAMP;

-- ── 5. Una sustancia, una vez, entre las vigentes (criterio 3) ────────────
-- Si lo heredado ya trae la misma sustancia repetida para un paciente, se
-- queda vigente la mas severa (a igual severidad, la primera) y las demas se
-- dan de baja. No se borran: siguen en la tabla para quien quiera revisarlas.
WITH repetidas AS (
    SELECT alergia_id,
           row_number() OVER (
               PARTITION BY paciente_id, lower(public.sin_tildes(trim(sustancia)))
               ORDER BY CASE severidad WHEN 'SEVERA' THEN 0 WHEN 'MODERADA' THEN 1 ELSE 2 END,
                        alergia_id
           ) AS orden
    FROM public.alergias
)
UPDATE public.alergias a
SET eliminada_en = NOW()
FROM repetidas r
WHERE r.alergia_id = a.alergia_id AND r.orden > 1;

-- El servicio ya rechaza el duplicado con un mensaje que nombra el registro
-- existente; este indice es la segunda cerradura, para dos altas simultaneas
-- que pasaran las dos la comprobacion antes de que cualquiera guardara. Es
-- parcial: una alergia dada de baja no impide volver a registrarla.
CREATE UNIQUE INDEX alergias_sustancia_vigente_unica
    ON public.alergias (paciente_id, lower(public.sin_tildes(trim(sustancia))))
    WHERE eliminada_en IS NULL;

CREATE INDEX alergias_paciente_idx ON public.alergias (paciente_id);
