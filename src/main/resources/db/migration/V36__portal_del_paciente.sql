-- ----------------------------------------------------------------------------
-- HU-34 · Portal del paciente (DRS-101)
--
-- El paciente entra con su propia cuenta (rol PACIENTE, abierta por invitacion
-- sobre su expediente, ver PacienteService.darAccesoAlPortal) y ve SU
-- expediente en solo lectura: alergias, consultas, recetas y proximas citas,
-- y puede bajarlo en PDF. Lo que esta migracion agrega es el criterio 6: que
-- cada vez que lo abre o lo descarga quede registrado.
--
-- Numero de version: V36 es el reservado para esta tarea en el reparto
-- (V33 y V34 son de Mario). FlywayBootstrap aplica fuera de orden por eso.
-- ----------------------------------------------------------------------------


-- ── Bitacora de accesos al expediente ─────────────────────────────────────
-- Por que una tabla nueva y no bitacora_de_usuarios (V27): aquella registra lo
-- que alguien HIZO sobre una CUENTA (altas, roles, estado), con un CHECK que
-- cierra sus acciones a esas cinco. Esto registra quien LEYO un EXPEDIENTE.
-- Son preguntas distintas de auditoria -"quien le cambio el rol a esta
-- cuenta" frente a "quien vio los datos de salud de este paciente"- y
-- mezclarlas obligaria a buscar al paciente por un usuario_afectado_id que no
-- es suyo (la mayoria de los pacientes no tiene cuenta).
--
-- usuario_id SIN FK hacia users, por lo mismo que bitacora_de_usuarios: borrar
-- una cuenta no puede borrar el rastro de lo que esa cuenta leyo. Por eso
-- tambien se guarda el correo de la sesion tal como era en ese momento.
--
-- paciente_id SI lleva FK: un expediente no se borra nunca (la baja es
-- logica, V12), asi que la restriccion no estorba y evita filas huerfanas.
--
-- tipo dice QUE se hizo con el expediente: VISTA (se abrio en el portal) o PDF
-- (se descargo). Lista cerrada; si despues se registran tambien los accesos
-- del personal clinico, se agrega aqui el valor nuevo.
CREATE TABLE public.accesos_al_expediente (
    acceso_id        BIGSERIAL    PRIMARY KEY,
    paciente_id      BIGINT       NOT NULL REFERENCES public.pacientes (persona_id),
    usuario_id       INTEGER      NOT NULL,
    correo_de_sesion VARCHAR(255) NOT NULL,
    tipo             VARCHAR(10)  NOT NULL,
    accedido_en      TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT accesos_al_expediente_tipo_check
        CHECK (tipo IN ('VISTA', 'PDF'))
);

-- La pregunta de la auditoria es siempre "los accesos a ESTE expediente, lo
-- mas reciente primero".
CREATE INDEX ix_accesos_al_expediente_paciente
    ON public.accesos_al_expediente (paciente_id, accedido_en DESC);

COMMENT ON TABLE public.accesos_al_expediente IS
    'Cada vez que se abrio (VISTA) o se descargo (PDF) un expediente desde el portal del paciente.';
