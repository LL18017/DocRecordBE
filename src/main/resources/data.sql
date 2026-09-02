-- ============================================================
-- DATOS INICIALES / DEMO
-- DocRecordBE
-- ============================================================

BEGIN;

-- ============================================================
-- 1. LIMPIAR DATOS ANTERIORES
-- ============================================================

TRUNCATE TABLE
    public.verification_token,
    public.clinicas,
    public.events,
    public.user_roles,
    public.users,
    public.role,
    public.user_type,
    public.event_types,
    public.event_status
RESTART IDENTITY CASCADE;


-- ============================================================
-- 2. TIPOS DE USUARIO
-- ============================================================

INSERT INTO public.user_type (name)
VALUES
    ('ADMINISTRADOR'),
    ('MEDICO'),
    ('ENFERMERO'),
    ('PACIENTE');


-- ============================================================
-- 3. ROLES
-- ============================================================

INSERT INTO public.role (name)
VALUES
    ('ROLE_ADMIN'),
    ('ROLE_MEDICO'),
    ('ROLE_ENFERMERO'),
    ('ROLE_PACIENTE');


-- ============================================================
-- 4. ESTADOS DE EVENTOS
-- ============================================================

INSERT INTO public.event_status (name)
VALUES
    ('PENDIENTE'),
    ('PROCESADO'),
    ('ERROR');


-- ============================================================
-- 5. TIPOS DE EVENTOS
-- ============================================================

INSERT INTO public.event_types (code, description)
VALUES
    ('USER_LOGIN', 'Inicio de sesión de usuario'),
    ('USER_LOGOUT', 'Cierre de sesión de usuario'),
    ('USER_REGISTER', 'Registro de nuevo usuario'),
    ('USER_UPDATE', 'Actualización de usuario'),
    ('CLINICA_CREATE', 'Creación de clínica'),
    ('CLINICA_UPDATE', 'Actualización de clínica'),
    ('TOKEN_CREATED', 'Creación de token de verificación'),
    ('TOKEN_USED', 'Uso de token de verificación');


-- ============================================================
-- 6. USUARIOS
-- ============================================================
--
-- NOTA:
-- El password es un hash ficticio.
-- Si quieres utilizar estos usuarios para login real,
-- debes reemplazarlo por un hash Argon2 válido.
--
-- Usuarios:
--
-- admin@docrecord.com
-- medico@docrecord.com
-- enfermero@docrecord.com
-- paciente@docrecord.com
-- ============================================================

INSERT INTO public.users
(email, name, password, user_type_id, enabled)
VALUES
    (
        'admin@docrecord.com',
        'Administrador del Sistema',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'ADMINISTRADOR'),
        TRUE
    ),
    (
        'carlos.medico@docrecord.com',
        'Dr. Carlos Martínez',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'MEDICO'),
        TRUE
    ),
    (
        'ana.medico@docrecord.com',
        'Dra. Ana Rodríguez',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'MEDICO'),
        TRUE
    ),
    (
        'maria.enfermera@docrecord.com',
        'María López',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'ENFERMERO'),
        TRUE
    ),
    (
        'jose.enfermero@docrecord.com',
        'José Hernández',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'ENFERMERO'),
        TRUE
    ),
    (
        'juan.paciente@docrecord.com',
        'Juan Pérez',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'PACIENTE'),
        TRUE
    ),
    (
        'sofia.paciente@docrecord.com',
        'Sofía Ramírez',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'PACIENTE'),
        TRUE
    ),
    (
        'pedro.paciente@docrecord.com',
        'Pedro González',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'PACIENTE'),
        TRUE
    ),
    (
        'lucia.paciente@docrecord.com',
        'Lucía Torres',
        '$argon2id$v=19$m=65536,t=3,p=1$MOCKSALTVALUEDONOTUSE$MOCKHASHVALUEDONOTUSEINPRODUCTIONxx',
        (SELECT user_type_id
         FROM public.user_type
         WHERE name = 'PACIENTE'),
        FALSE
    );


-- ============================================================
-- 7. RELACIÓN USUARIO - ROL
-- ============================================================

-- Administrador
INSERT INTO public.user_roles (user_id, role_id)
SELECT
    u.user_id,
    r.role_id
FROM public.users u
         CROSS JOIN public.role r
WHERE u.email = 'admin@docrecord.com'
  AND r.name = 'ROLE_ADMIN';


-- Médicos
INSERT INTO public.user_roles (user_id, role_id)
SELECT
    u.user_id,
    r.role_id
FROM public.users u
         CROSS JOIN public.role r
WHERE u.email IN (
                  'carlos.medico@docrecord.com',
                  'ana.medico@docrecord.com'
    )
  AND r.name = 'ROLE_MEDICO';


-- Enfermeros
INSERT INTO public.user_roles (user_id, role_id)
SELECT
    u.user_id,
    r.role_id
FROM public.users u
         CROSS JOIN public.role r
WHERE u.email IN (
                  'maria.enfermera@docrecord.com',
                  'jose.enfermero@docrecord.com'
    )
  AND r.name = 'ROLE_ENFERMERO';


-- Pacientes
INSERT INTO public.user_roles (user_id, role_id)
SELECT
    u.user_id,
    r.role_id
FROM public.users u
         CROSS JOIN public.role r
WHERE u.email IN (
                  'juan.paciente@docrecord.com',
                  'sofia.paciente@docrecord.com',
                  'pedro.paciente@docrecord.com',
                  'lucia.paciente@docrecord.com'
    )
  AND r.name = 'ROLE_PACIENTE';


-- ============================================================
-- 8. CLÍNICAS
-- ============================================================

INSERT INTO public.clinicas
(latitud, longitud, name, user_id)
VALUES
    (
        13.692940,
        -89.218191,
        'Clínica Central San Salvador',
        (
            SELECT user_id
            FROM public.users
            WHERE email = 'carlos.medico@docrecord.com'
        )
    ),
    (
        13.703120,
        -89.235870,
        'Clínica Médica Escalón',
        (
            SELECT user_id
            FROM public.users
            WHERE email = 'ana.medico@docrecord.com'
        )
    ),
    (
        13.700000,
        -89.200000,
        'Centro Médico La Salud',
        (
            SELECT user_id
            FROM public.users
            WHERE email = 'admin@docrecord.com'
        )
    );


-- ============================================================
-- 9. EVENTOS
-- ============================================================

INSERT INTO public.events
(
    created_at,
    description,
    ip_address,
    processed_at,
    ref,
    user_email,
    event_status_id,
    event_type_id
)
VALUES
    (
                CURRENT_TIMESTAMP - INTERVAL '5 days',
                'Inicio de sesión del administrador',
                '192.168.1.10',
                CURRENT_TIMESTAMP - INTERVAL '5 days',
                'LOGIN-0001',
                'admin@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_LOGIN'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '4 days',
                'Inicio de sesión del médico Carlos Martínez',
                '192.168.1.20',
                CURRENT_TIMESTAMP - INTERVAL '4 days',
                'LOGIN-0002',
                'carlos.medico@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_LOGIN'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '3 days',
                'Registro de nuevo paciente',
                '192.168.1.30',
                CURRENT_TIMESTAMP - INTERVAL '3 days',
                'REGISTER-0001',
                'juan.paciente@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_REGISTER'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '2 days',
                'Actualización de información del usuario',
                '192.168.1.31',
                CURRENT_TIMESTAMP - INTERVAL '2 days',
                'UPDATE-0001',
                'sofia.paciente@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_UPDATE'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '1 day',
                'Creación de clínica médica',
                '192.168.1.20',
                CURRENT_TIMESTAMP - INTERVAL '1 day',
                'CLINIC-0001',
                'carlos.medico@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'CLINICA_CREATE'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '12 hours',
                'Cierre de sesión',
                '192.168.1.20',
                CURRENT_TIMESTAMP - INTERVAL '12 hours',
                'LOGOUT-0001',
                'carlos.medico@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PROCESADO'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_LOGOUT'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '2 hours',
                'Token de verificación generado para paciente',
                '192.168.1.40',
                NULL,
                'TOKEN-0001',
                'pedro.paciente@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'PENDIENTE'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'TOKEN_CREATED'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '30 minutes',
                'Error al procesar solicitud de actualización',
                '192.168.1.50',
                CURRENT_TIMESTAMP - INTERVAL '29 minutes',
                'ERROR-0001',
                'lucia.paciente@docrecord.com',
                (
                    SELECT event_status_id
                    FROM public.event_status
                    WHERE name = 'ERROR'
                ),
                (
                    SELECT event_type_id
                    FROM public.event_types
                    WHERE code = 'USER_UPDATE'
                )
    );


-- ============================================================
-- 10. TOKENS DE VERIFICACIÓN
-- ============================================================

INSERT INTO public.verification_token
(
    expires_at,
    token,
    used,
    user_id
)
VALUES
    (
                CURRENT_TIMESTAMP + INTERVAL '24 hours',
                'token-demo-juan-001',
                FALSE,
                (
                    SELECT user_id
                    FROM public.users
                    WHERE email = 'juan.paciente@docrecord.com'
                )
    ),
    (
                CURRENT_TIMESTAMP + INTERVAL '24 hours',
                'token-demo-sofia-002',
                FALSE,
                (
                    SELECT user_id
                    FROM public.users
                    WHERE email = 'sofia.paciente@docrecord.com'
                )
    ),
    (
                CURRENT_TIMESTAMP - INTERVAL '2 days',
                'token-demo-pedro-003',
                TRUE,
                (
                    SELECT user_id
                    FROM public.users
                    WHERE email = 'pedro.paciente@docrecord.com'
                )
    );


COMMIT;