-- ----------------------------------------------------------------------------
-- HU-30 · Agendamiento de una cita (DRS-29), criterio 5: una franja de un
-- medico admite una sola cita no cancelada, aun con dos reservas a la vez.
--
-- V24 creo el indice unico parcial citas_franja_del_medico_uq SOLO si en ese
-- momento no habia dos citas vivas del mismo medico a la misma hora: con
-- choques previos el indice no se puede crear, y fallar ahi habria dejado la
-- API sin arrancar. En una base donde eso paso, el indice no existe y nada lo
-- vuelve a intentar.
--
-- Esta migracion lo vuelve a intentar. Si ya existe, no hace nada. Si faltan
-- y ya no hay choques, lo crea. Si todavia hay choques, no falla: deja un
-- WARNING con cuantas franjas chocan, para resolverlas a mano (cancelar con
-- motivo la que sobra) y que el indice se cree en el siguiente despliegue
-- con una migracion nueva.
--
-- La garantia no depende de esto: desde esta misma version, CitaService toma
-- un candado de la franja (pg_advisory_xact_lock) antes de comprobar la
-- disponibilidad, asi que dos reservas simultaneas ya no pueden colarse
-- aunque el indice falte. El indice queda como segunda barrera, en la base.
-- ----------------------------------------------------------------------------

DO $$
DECLARE
    choques INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes
               WHERE schemaname = 'public' AND indexname = 'citas_franja_del_medico_uq') THEN
        RETURN;
    END IF;

    SELECT COUNT(*) INTO choques FROM (
        SELECT 1 FROM public.citas
        WHERE estado <> 'CANCELADA'
        GROUP BY medico_user_id, fecha_hora
        HAVING COUNT(*) > 1
    ) AS franjas_repetidas;

    IF choques = 0 THEN
        CREATE UNIQUE INDEX citas_franja_del_medico_uq
            ON public.citas (medico_user_id, fecha_hora)
            WHERE estado <> 'CANCELADA';
    ELSE
        RAISE WARNING 'citas_franja_del_medico_uq no se creo: % franja(s) con mas de una cita viva del mismo medico', choques;
    END IF;
END $$;
