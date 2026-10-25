-- ----------------------------------------------------------------------------
-- HU-30 y HU-32 · Agendar, reprogramar y cancelar citas (DRS-29, DRS-99).
--
-- La tabla la creo V18 colgada de users.user_id, tal como venia la entidad de
-- la rama `dev`, y dejo dicho que moverla al paciente era trabajo pendiente.
-- Es la misma conversion que V19 y V20 les hicieron a las condiciones
-- hereditarias y a las alergias:
--
--   1. paciente_persona_id en lugar de paciente_user_id. La mayoria de los
--      pacientes no tiene cuenta en el sistema: colgada del usuario, una cita
--      solo podia agendarse a quien se hubiera registrado. Las filas cuyo
--      usuario es paciente se convierten; la columna vieja queda anulable y
--      sin uso, para no descartar ninguna fila.
--   2. clinica_id: la sede donde se atiende (criterio 1 de HU-30). Anulable
--      porque las citas anteriores no la guardaron.
--   3. motivo_cancelacion: una cita cancelada no se borra, conserva su estado
--      y su motivo (criterios 1 y 5 de HU-32).
--   4. una franja de un medico solo puede tenerla UNA cita viva. El servicio
--      lo valida antes de guardar, pero dos reservas simultaneas pasan las dos
--      la validacion; el indice unico hace que la segunda falle (criterio 5
--      de HU-30). Las canceladas no cuentan: liberan la franja.
-- ----------------------------------------------------------------------------

-- ── 1. Del usuario al paciente ────────────────────────────────────────────
ALTER TABLE public.citas
    ADD COLUMN paciente_persona_id BIGINT
        REFERENCES public.pacientes (persona_id) ON DELETE RESTRICT;

UPDATE public.citas c
SET paciente_persona_id = u.persona_id
FROM public.users u
JOIN public.pacientes p ON p.persona_id = u.persona_id
WHERE u.user_id = c.paciente_user_id;

ALTER TABLE public.citas ALTER COLUMN paciente_user_id DROP NOT NULL;

CREATE INDEX citas_paciente_persona_id_idx ON public.citas (paciente_persona_id);

-- ── 2. La sede ────────────────────────────────────────────────────────────
ALTER TABLE public.citas
    ADD COLUMN clinica_id INTEGER
        REFERENCES public.clinicas (clinica_id) ON DELETE RESTRICT;

CREATE INDEX citas_clinica_id_idx ON public.citas (clinica_id);

-- ── 3. Estado y motivo ────────────────────────────────────────────────────
-- El estado era texto libre que mandaba el cliente. Desde aqui lo pone el
-- servicio: AGENDADA al crear, CANCELADA al cancelar, ATENDIDA al atender.
UPDATE public.citas SET estado = UPPER(TRIM(estado));

ALTER TABLE public.citas ADD COLUMN motivo_cancelacion VARCHAR(255);

-- ── 4. Una franja, una cita viva ──────────────────────────────────────────
-- Solo si los datos ya lo cumplen: con dos citas vivas en la misma franja el
-- indice no se puede crear, y fallar aqui dejaria la API sin arrancar. En ese
-- caso queda la validacion del servicio y hay que resolver el choque a mano.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.citas
        WHERE estado <> 'CANCELADA'
        GROUP BY medico_user_id, fecha_hora
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX citas_franja_del_medico_uq
            ON public.citas (medico_user_id, fecha_hora)
            WHERE estado <> 'CANCELADA';
    END IF;
END $$;
