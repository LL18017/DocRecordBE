-- ============================================================================
-- Modelo clinico: `persona` es la identidad base; medicos/enfermeras/pacientes
-- son especializaciones que comparten su clave primaria con persona (no usan
-- herencia de tabla unica) porque una misma persona puede ser, por ejemplo,
-- enfermera Y paciente a la vez -- el personal tambien se atiende donde
-- trabaja, y con herencia JPA una fila solo podria pertenecer a un subtipo.
-- ============================================================================

CREATE TABLE public.persona (
    persona_id       BIGSERIAL PRIMARY KEY,
    dui              VARCHAR(10) UNIQUE NULL,
    nombres          VARCHAR(80) NOT NULL,
    apellidos        VARCHAR(80) NOT NULL,
    -- Nulos a proposito: al autoregistrarse un medico solo se conocen su
    -- nombre y su correo. Que a un paciente le falten estos datos es un
    -- error de negocio (lo exige el servicio al dar de alta), no una
    -- violacion de esquema.
    fecha_nacimiento DATE NULL,
    sexo             CHAR(1) NULL,
    telefono         VARCHAR(20) NULL,
    direccion        VARCHAR(255) NULL,
    CONSTRAINT persona_sexo_check CHECK (sexo IS NULL OR sexo IN ('M', 'F'))
);

-- Buscar por apellido es la operacion mas frecuente del sistema.
CREATE INDEX idx_persona_apellidos ON public.persona (apellidos);


--
-- especialidades
--

CREATE TABLE public.especialidades (
    especialidad_id BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(80) NOT NULL UNIQUE,
    activa          BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO public.especialidades (nombre) VALUES
    ('Medicina General'),
    ('Pediatría'),
    ('Ginecología'),
    ('Cardiología'),
    ('Dermatología'),
    ('Neurología'),
    ('Ortopedia');


--
-- medicos / enfermeras / pacientes
--
-- persona_id es a la vez PK y FK: la fila SOLO existe si hay una persona
-- duena de ella, y se borra si la persona se borra.
--

CREATE TABLE public.medicos (
    persona_id      BIGINT PRIMARY KEY REFERENCES public.persona (persona_id) ON DELETE CASCADE,
    registro_junta  VARCHAR(20) UNIQUE,
    especialidad_id BIGINT NOT NULL REFERENCES public.especialidades (especialidad_id),
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE public.enfermeras (
    persona_id     BIGINT PRIMARY KEY REFERENCES public.persona (persona_id) ON DELETE CASCADE,
    registro_junta VARCHAR(20) UNIQUE,
    activo         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE public.pacientes (
    persona_id  BIGINT PRIMARY KEY REFERENCES public.persona (persona_id) ON DELETE CASCADE,
    expediente  VARCHAR(12) NOT NULL UNIQUE,
    tipo_sangre VARCHAR(3),
    creado_en   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pacientes_tipo_sangre_check
        CHECK (tipo_sangre IS NULL OR tipo_sangre IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'))
);


--
-- users gana persona_id: toda cuenta pertenece a una persona (aunque esa
-- persona tenga el resto de sus datos en NULL todavia).
--

ALTER TABLE public.users ADD COLUMN persona_id BIGINT;

-- Migracion de datos, escrita para N filas (hoy hay una sola: "Naun Flores").
-- `users.name` es un solo campo de texto libre; se divide en la primera
-- palabra (-> nombres) y el resto (-> apellidos) porque es el unico dato que
-- existe para filas heredadas. Es una decision de una sola vez para este
-- dato historico: el registro de medicos ya envia nombres/apellidos por
-- separado y no pasa por aqui.
WITH nuevas_personas AS (
    INSERT INTO public.persona (nombres, apellidos)
    SELECT
        split_part(trim(u.name), ' ', 1),
        COALESCE(
            NULLIF(trim(substring(trim(u.name) FROM position(' ' IN trim(u.name)))), ''),
            split_part(trim(u.name), ' ', 1)
        )
    FROM public.users u
    ORDER BY u.user_id
    RETURNING persona_id
),
usuarios_en_orden AS (
    SELECT user_id, row_number() OVER (ORDER BY user_id) AS rn
    FROM public.users
),
personas_en_orden AS (
    SELECT persona_id, row_number() OVER (ORDER BY persona_id) AS rn
    FROM nuevas_personas
)
UPDATE public.users u
SET persona_id = po.persona_id
FROM usuarios_en_orden uo
JOIN personas_en_orden po ON po.rn = uo.rn
WHERE u.user_id = uo.user_id;

ALTER TABLE public.users ALTER COLUMN persona_id SET NOT NULL;
ALTER TABLE public.users ADD CONSTRAINT uk_users_persona_id UNIQUE (persona_id);
ALTER TABLE public.users ADD CONSTRAINT fk_users_persona FOREIGN KEY (persona_id) REFERENCES public.persona (persona_id);

-- `users.name` NO se elimina en esta migracion a proposito: la entidad User
-- (tarea aparte) todavia la mapea, y con ddl-auto=validate borrarla aqui
-- dejaria la aplicacion sin arrancar hasta ese commit. La columna queda
-- huerfana (con los datos ya migrados a persona) hasta la migracion V3, que
-- la retira en el mismo commit que actualiza la entidad.
