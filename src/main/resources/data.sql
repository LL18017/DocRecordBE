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
-- Debe mantenerse sincronizada con el enum RolesEnum: RoleMapper.toEntity(Integer)
-- construye el Role a partir del enum, no de esta tabla, asi que un id aqui
-- que no coincida con el enum se mapearia con el nombre equivocado.
-- =========================
INSERT INTO public.role (role_id, name) VALUES
    (1, 'ADMIN'),
    (2, 'MEDICO'),
    (3, 'ENFERMERA'),
    (4, 'PACIENTE')
ON CONFLICT (role_id) DO NOTHING;

-- =========================
-- Ajuste de secuencias: tras insertar IDs explicitos, las secuencias deben
-- continuar despues del maximo para no chocar con los catalogos.
-- COALESCE cubre el caso de una tabla vacia (setval no acepta NULL).
-- =========================
SELECT setval('public.event_status_seq', COALESCE((SELECT MAX(event_status_id) FROM public.event_status), 1));
SELECT setval('public.event_types_seq',  COALESCE((SELECT MAX(event_type_id)   FROM public.event_types),  1));
SELECT setval('public.events_seq',       COALESCE((SELECT MAX(event_id)        FROM public.events),       1));
SELECT setval('public.role_role_id_seq', COALESCE((SELECT MAX(role_id)         FROM public.role),         1));
