-- ============================================================================
-- Catalogos iniciales de DocRecord.
--
-- Spring lo ejecuta automaticamente en cada arranque, DESPUES de que Hibernate
-- crea o actualiza las tablas (spring.jpa.defer-datasource-initialization=true
-- en application.properties). Por eso NO puede colocarse en la carpeta
-- /docker-entrypoint-initdb.d de PostgreSQL: alli correria antes de que las
-- tablas existan y fallaria.
--
-- Todas las inserciones son idempotentes (ON CONFLICT DO NOTHING) para que
-- reiniciar la aplicacion no rompa por clave duplicada.
-- ============================================================================

-- =========================
-- event_status
-- =========================
INSERT INTO public.event_status (event_status_id, name) VALUES
    (1, 'PENDING'),
    (2, 'PROCESSING'),
    (3, 'PROCESSED'),
    (4, 'FAILED')
ON CONFLICT (event_status_id) DO NOTHING;

-- =========================
-- event_types
-- =========================
INSERT INTO public.event_types (event_type_id, code, description) VALUES
    (1, 'LOGIN', 'Inicio de sesión'),
    (2, 'PASSWORD_CHANGED', 'Cambio de contraseña'),
    (3, 'USER_REGISTERED', 'Registro de usuario'),
    (4, 'APPOINTMENT_CREATED', 'Registro de cita'),
    (5, 'APPOINTMENT_CANCELLED', 'Cancelación de cita')
ON CONFLICT (event_type_id) DO NOTHING;

-- =========================
-- role
--
-- PENDIENTE DE DECISION DEL EQUIPO: esta tabla debe mantenerse sincronizada con
-- el enum RolesEnum, que hoy declara ADMIN(1), DIRECTOR(2), PROFESOR(3) y
-- ESTUDIANTE(4) — roles heredados del proyecto academico anterior, no de
-- DocRecord. Insertar aqui MEDICO/ENFERMERA/PACIENTE sin cambiar el enum
-- provocaria que RoleMapper.toEntity(2) devolviera "DIRECTOR" para una fila
-- llamada "MEDICO". Por eso se deja solo ADMIN hasta que se corrija el enum.
-- =========================
INSERT INTO public.role (role_id, name) VALUES
    (1, 'ADMIN')
ON CONFLICT (role_id) DO NOTHING;

-- =========================
-- user_type
-- =========================
INSERT INTO public.user_type (user_type_id, name) VALUES
    (1, 'DOCTOR'),
    (2, 'ENFERMERA'),
    (3, 'EMPLEADO')
ON CONFLICT (user_type_id) DO NOTHING;

-- =========================
-- Ajuste de secuencias: tras insertar IDs explicitos, las secuencias deben
-- continuar despues del maximo para no chocar con los catalogos.
-- COALESCE cubre el caso de una tabla vacia (setval no acepta NULL).
-- =========================
SELECT setval('public.event_status_seq', COALESCE((SELECT MAX(event_status_id) FROM public.event_status), 1));
SELECT setval('public.event_types_seq',  COALESCE((SELECT MAX(event_type_id)   FROM public.event_types),  1));
SELECT setval('public.events_seq',       COALESCE((SELECT MAX(event_id)        FROM public.events),       1));
SELECT setval('public.role_role_id_seq', COALESCE((SELECT MAX(role_id)         FROM public.role),         1));
SELECT setval('public.user_type_seq',    COALESCE((SELECT MAX(user_type_id)    FROM public.user_type),    1));
