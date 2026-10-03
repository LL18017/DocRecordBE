-- ============================================================================
-- V6 -- Consultas medicas y prescripciones (epicas E6 y E7)
--
-- Es el nucleo clinico del sistema: hasta aqui el modelo sabia QUIENES son
-- (personas, medicos, enfermeras, pacientes) y DONDE trabajan (clinicas), pero
-- no guardaba ni un solo acto medico. Una consulta es el hecho de que un
-- medico atendio a un paciente en una fecha; una prescripcion es el documento
-- que sale de esa consulta.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- consultas
--
-- paciente_id y medico_id apuntan a pacientes(persona_id) y medicos(persona_id)
-- y no a persona(persona_id): la fila de persona existe para cualquiera, pero
-- una consulta solo tiene sentido entre alguien que ESTA registrado como
-- paciente y alguien que ESTA registrado como medico. Apuntando a persona, la
-- base aceptaria una consulta donde el que atiende es el conserje.
-- ----------------------------------------------------------------------------
CREATE TABLE public.consultas (
    consulta_id BIGSERIAL PRIMARY KEY,

    paciente_id BIGINT NOT NULL
        REFERENCES public.pacientes (persona_id),

    medico_id   BIGINT NOT NULL
        REFERENCES public.medicos (persona_id),

    -- NULABLE a proposito. Las clinicas de este sistema son sucursales de un
    -- mismo dueno y "donde se atendio" es un dato clinico que conviene
    -- guardar, asi que la columna existe. Pero un medico que todavia no ha
    -- registrado ninguna sucursal debe poder atender igual; obligarlo a
    -- elegir una lo forzaria a inventarsela, y un dato falso en el
    -- expediente es peor que un dato ausente. Es la misma decision que se
    -- tomo con clinicas.latitud/longitud: se prefiere el hueco a la mentira.
    clinica_id  INTEGER NULL
        REFERENCES public.clinicas (clinica_id),

    fecha       TIMESTAMP NOT NULL DEFAULT NOW(),
    motivo      TEXT,

    -- El diagnostico es el campo que distingue este sistema de una libreta, y
    -- solo lo escribe un medico. Eso NO se puede imponer con una restriccion
    -- de esquema (la base no sabe quien manda el UPDATE), asi que la regla
    -- vive en ConsultaService, que resuelve la identidad desde el token.
    diagnostico TEXT,

    -- El estado no lo declara el cliente: lo deriva el servicio del propio
    -- diagnostico (sin diagnostico -> PENDIENTE, con diagnostico ->
    -- FINALIZADA). El CHECK esta aqui de todos modos porque una regla de
    -- negocio en Java protege lo que pasa por Java, y este esquema tambien lo
    -- tocan cargas y correcciones a mano.
    estado      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',

    CONSTRAINT consultas_estado_check
        CHECK (estado IN ('PENDIENTE', 'FINALIZADA'))
);

-- "El historial de este paciente" es LA consulta del sistema y siempre sale
-- ordenada de la mas reciente a la mas antigua; el indice lleva la fecha
-- descendente para que el ORDER BY no tenga que ordenar nada.
CREATE INDEX idx_consultas_paciente ON public.consultas (paciente_id, fecha DESC);

-- "Lo que atendi yo", para la agenda del medico.
CREATE INDEX idx_consultas_medico ON public.consultas (medico_id);

-- Rangos de fecha sin filtrar por paciente ni medico: reportes del periodo.
CREATE INDEX idx_consultas_fecha ON public.consultas (fecha);


-- ----------------------------------------------------------------------------
-- prescripciones
--
-- ON DELETE CASCADE hacia consultas: una receta no existe por su cuenta, es
-- parte del acto medico que la origino. Si la consulta se borra, dejar la
-- receta colgando produciria un documento sin contexto -- una lista de
-- medicamentos sin saber por que se recetaron.
-- ----------------------------------------------------------------------------
CREATE TABLE public.prescripciones (
    prescripcion_id BIGSERIAL PRIMARY KEY,

    consulta_id     BIGINT NOT NULL
        REFERENCES public.consultas (consulta_id) ON DELETE CASCADE,

    -- Redundante con consultas.medico_id hoy, y aun asi necesaria: es la FIRMA
    -- de la receta. Una receta es un documento con un responsable legal, y ese
    -- responsable es un dato propio del documento, no algo que se deduzca de
    -- otra tabla. Deducirlo funciona mientras receta siempre el mismo que
    -- atendio; el dia que firme el medico de turno -- o que la consulta se
    -- corrija -- la firma cambiaria sola, que es justo lo que una firma no
    -- puede hacer.
    medico_id       BIGINT NOT NULL
        REFERENCES public.medicos (persona_id),

    fecha           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prescripciones_consulta ON public.prescripciones (consulta_id);


-- ----------------------------------------------------------------------------
-- prescripcion_medicamentos
--
-- Tabla aparte y no columnas repetidas ni un texto largo en prescripciones:
-- una receta lleva varios medicamentos y cada uno tiene su propia dosis,
-- frecuencia y duracion. Guardarlos como texto libre haria imposible
-- responder "que pacientes toman X", que es justo lo que se le pide a un
-- expediente electronico.
--
-- dosis, frecuencia y duracion admiten NULL: hay indicaciones que
-- legitimamente no las llevan ("suspender el medicamento anterior").
-- ----------------------------------------------------------------------------
CREATE TABLE public.prescripcion_medicamentos (
    id              BIGSERIAL PRIMARY KEY,

    prescripcion_id BIGINT NOT NULL
        REFERENCES public.prescripciones (prescripcion_id) ON DELETE CASCADE,

    medicamento     VARCHAR(160) NOT NULL,
    dosis           VARCHAR(80),
    frecuencia      VARCHAR(80),
    duracion        VARCHAR(80)
);

CREATE INDEX idx_prescripcion_medicamentos_prescripcion
    ON public.prescripcion_medicamentos (prescripcion_id);
