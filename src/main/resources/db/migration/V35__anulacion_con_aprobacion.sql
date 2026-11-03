-- ----------------------------------------------------------------------------
-- HU-21 · HU-22 · Anulacion de consultas y recetas con ventana y aprobacion
-- (DRS-91, DRS-27)
--
-- Anular un documento clinico deja de ser un acto de una sola persona en
-- cualquier momento. La regla, igual para consultas y recetas:
--
--   · Dentro de la ventana (minutos desde que se registro), quien lo firmo o
--     un administrador lo anula directamente, con motivo. Es el caso del error
--     que se nota al momento: el paciente equivocado, la dosis mal tecleada.
--   · Pasada la ventana, la anulacion es una SOLICITUD que necesita N
--     aprobaciones de administradores distintos de quien la pide. Para
--     entonces el documento ya pudo haberse usado (una receta despachada, una
--     consulta leida por otro medico), y deshacerlo tiene que pasar por mas de
--     un par de ojos.
--   · Pasado el tope de dias, si esta configurado, ya no se anula.
--
-- Los tres valores son parametros por tipo de documento que el administrador
-- cambia sin redesplegar.
--
-- Numero de version: V35 esta reservado para esta tarea en el reparto (V33 y
-- V34 son de Mario). FlywayBootstrap aplica fuera de orden por eso mismo.
-- ----------------------------------------------------------------------------


-- ── 1. Parametros de anulacion, uno por tipo de documento ──────────────────
CREATE TABLE public.parametros_de_anulacion (
    tipo                     VARCHAR(20) PRIMARY KEY,
    -- 0 = no hay anulacion directa: todo pasa por aprobacion.
    minutos_anulacion_directa INTEGER NOT NULL,
    aprobaciones_requeridas  INTEGER NOT NULL,
    -- NULL = sin tope.
    dias_maximos             INTEGER,
    actualizado_en           TIMESTAMP,
    actualizado_por          INTEGER REFERENCES public.users (user_id),

    CONSTRAINT parametros_de_anulacion_tipo_check
        CHECK (tipo IN ('CONSULTA', 'RECETA')),
    CONSTRAINT parametros_de_anulacion_minutos_check
        CHECK (minutos_anulacion_directa BETWEEN 0 AND 1440),
    CONSTRAINT parametros_de_anulacion_aprobaciones_check
        CHECK (aprobaciones_requeridas BETWEEN 1 AND 5),
    CONSTRAINT parametros_de_anulacion_dias_check
        CHECK (dias_maximos IS NULL OR dias_maximos BETWEEN 1 AND 3650)
);

-- 10 minutos y 2 aprobaciones: lo acordado como punto de partida. Sin tope de
-- dias, porque un error en el expediente se tiene que poder corregir aunque se
-- descubra tarde; el administrador lo fija si la clinica lo pide.
INSERT INTO public.parametros_de_anulacion (tipo, minutos_anulacion_directa, aprobaciones_requeridas)
VALUES ('CONSULTA', 10, 2),
       ('RECETA',   10, 2);


-- ── 2. consultas · anulacion logica (HU-21) ────────────────────────────────
-- registrada_en es el instante en que se guardo la consulta, que no siempre
-- es `fecha`: el cliente puede registrar una atencion de hace un rato. La
-- ventana se cuenta desde que existe en el sistema. Las filas existentes toman
-- su fecha, que es lo mas cercano que se sabe.
ALTER TABLE public.consultas
    ADD COLUMN registrada_en    TIMESTAMP,
    ADD COLUMN motivo_anulacion VARCHAR(500),
    ADD COLUMN anulada_en       TIMESTAMP,
    ADD COLUMN anulada_por      INTEGER REFERENCES public.users (user_id);

UPDATE public.consultas SET registrada_en = fecha;

ALTER TABLE public.consultas
    ALTER COLUMN registrada_en SET NOT NULL,
    ALTER COLUMN registrada_en SET DEFAULT now();

ALTER TABLE public.consultas DROP CONSTRAINT consultas_estado_check;
ALTER TABLE public.consultas
    ADD CONSTRAINT consultas_estado_check
        CHECK (estado IN ('PENDIENTE', 'FINALIZADA', 'ANULADA')),
    -- Mismo candado que las recetas (V31): sin motivo, autor y fecha no hay
    -- anulacion, ni por SQL directo.
    ADD CONSTRAINT consultas_anulacion_completa_check
        CHECK (estado <> 'ANULADA'
               OR (motivo_anulacion IS NOT NULL
                   AND anulada_en IS NOT NULL
                   AND anulada_por IS NOT NULL));


-- ── 3. Solicitudes de anulacion ────────────────────────────────────────────
CREATE TABLE public.solicitudes_de_anulacion (
    solicitud_id            BIGSERIAL PRIMARY KEY,
    tipo                    VARCHAR(20) NOT NULL,
    consulta_id             BIGINT NOT NULL REFERENCES public.consultas (consulta_id),
    -- Solo en las de tipo RECETA. La consulta va siempre: es por donde se
    -- llega al paciente y al expediente.
    prescripcion_id         BIGINT REFERENCES public.prescripciones (prescripcion_id),
    motivo                  VARCHAR(500) NOT NULL,
    solicitada_por          INTEGER NOT NULL REFERENCES public.users (user_id),
    solicitada_en           TIMESTAMP NOT NULL,
    -- Copia del parametro al momento de pedirla: cambiar la configuracion no
    -- mueve la meta de las solicitudes que ya estan en curso.
    aprobaciones_requeridas INTEGER NOT NULL,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    resuelta_en             TIMESTAMP,
    resuelta_por            INTEGER REFERENCES public.users (user_id),
    motivo_rechazo          VARCHAR(500),

    CONSTRAINT solicitudes_de_anulacion_tipo_check
        CHECK (tipo IN ('CONSULTA', 'RECETA')),
    CONSTRAINT solicitudes_de_anulacion_documento_check
        CHECK ((tipo = 'CONSULTA' AND prescripcion_id IS NULL)
            OR (tipo = 'RECETA' AND prescripcion_id IS NOT NULL)),
    CONSTRAINT solicitudes_de_anulacion_estado_check
        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA')),
    CONSTRAINT solicitudes_de_anulacion_aprobaciones_check
        CHECK (aprobaciones_requeridas BETWEEN 1 AND 5),
    CONSTRAINT solicitudes_de_anulacion_rechazo_check
        CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL),
    CONSTRAINT solicitudes_de_anulacion_resuelta_check
        CHECK (estado = 'PENDIENTE' OR resuelta_en IS NOT NULL)
);

-- Una sola solicitud en curso por documento. Las resueltas no cuentan: una
-- rechazada se puede volver a pedir con mejores razones.
CREATE UNIQUE INDEX uq_solicitud_pendiente_consulta
    ON public.solicitudes_de_anulacion (consulta_id)
    WHERE estado = 'PENDIENTE' AND tipo = 'CONSULTA';
CREATE UNIQUE INDEX uq_solicitud_pendiente_receta
    ON public.solicitudes_de_anulacion (prescripcion_id)
    WHERE estado = 'PENDIENTE' AND tipo = 'RECETA';

CREATE INDEX idx_solicitudes_de_anulacion_estado
    ON public.solicitudes_de_anulacion (estado, solicitada_en DESC);


-- ── 4. Aprobaciones ────────────────────────────────────────────────────────
-- La clave primaria es la regla: un administrador aprueba una vez.
CREATE TABLE public.aprobaciones_de_anulacion (
    solicitud_id BIGINT  NOT NULL
        REFERENCES public.solicitudes_de_anulacion (solicitud_id),
    user_id      INTEGER NOT NULL REFERENCES public.users (user_id),
    aprobada_en  TIMESTAMP NOT NULL,
    PRIMARY KEY (solicitud_id, user_id)
);


-- ----------------------------------------------------------------------------
-- HU-02 · Invitaciones a crear la cuenta (DRS-77)
--
-- Va en esta misma migracion porque V35 todavia no se aplico en ningun
-- entorno compartido y es el unico numero reservado para esta tarea.
--
-- La cuenta de un medico ya no la crea el medico (se retiro el registro
-- publico): el administrador registra su ficha y despues le da acceso. Darle
-- acceso crea la cuenta sin contrasena utilizable y manda una INVITACION con
-- un enlace para que la persona elija la suya. Al canjearla la cuenta queda
-- habilitada. Sirve igual para el paciente: es la cuenta que se activa sobre
-- un expediente que ya existe.
--
-- Tabla propia y no token_restablecimiento ni verification_token por la misma
-- razon que V14 separo esas dos: cada tipo de enlace concede algo distinto, y
-- con tablas separadas uno no puede servir para lo del otro por un descuido en
-- una consulta.
-- ----------------------------------------------------------------------------
CREATE TABLE public.invitaciones (
    invitacion_id BIGSERIAL PRIMARY KEY,
    token         VARCHAR(255) NOT NULL UNIQUE,
    user_id       INTEGER NOT NULL REFERENCES public.users (user_id),
    vence_en      TIMESTAMP NOT NULL,
    usada         BOOLEAN NOT NULL DEFAULT FALSE,
    creada_en     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_invitaciones_user ON public.invitaciones (user_id);
