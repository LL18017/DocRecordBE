-- Script para insertar datos en la base de datos NUEVA (limpia)
-- Datos extraídos del dump de la base original
-- Excluye: user_roles, users, facultad_carreras, carrera
-- Base de datos: education

-- =========================
-- event_status
-- =========================
INSERT INTO public.event_status (event_status_id, name) VALUES
                                                            (1, 'PENDING'),
                                                            (2, 'PROCESSING'),
                                                            (3, 'PROCESSED'),
                                                            (4, 'FAILED');

-- =========================
-- event_types
-- =========================
INSERT INTO public.event_types (event_type_id, code, description) VALUES
                                                                      (1, 'LOGIN', 'Inicio de sesión'),
                                                                      (2, 'PASSWORD_CHANGED', 'Cambio de contraseña'),
                                                                      (3, 'USER_REGISTERED', 'Registro de usuario'),
                                                                      (4, 'APPOINTMENT_CREATED', 'Registro de cita'),
                                                                      (5, 'APPOINTMENT_CANCELLED', 'Cancelación de cita');


-- =========================
-- role
-- =========================
INSERT INTO public.role (role_id, name) VALUES
    (1, 'ADMIN');

-- =========================
-- user_type
-- =========================
INSERT INTO public.user_type (user_type_id, name) VALUES
                                                      (1, 'DOCTOR'),
                                                      (2, 'ENFERMERA'),
                                                      (3, 'EMPLEADO');

-- =========================
-- events
-- (depende de event_status y event_types, por eso va al final)
-- =========================
INSERT INTO public.events (event_id, created_at, description, ip_address, processed_at, user_email, event_status_id, event_type_id) VALUES
    (202, '2026-08-14 12:04:17.687196', 'Se ha detectado un nuevo inicio de sesión', NULL, '2026-08-14 12:43:02.615727', 'mario992lopez@gmail.com', 3, 1);

-- =========================
-- Ajustar las secuencias para que sigan generando IDs correctamente
-- después de insertar valores explícitos
-- =========================
SELECT setval('public.event_status_seq', (SELECT MAX(event_status_id) FROM public.event_status));
SELECT setval('public.event_types_seq', (SELECT MAX(event_type_id) FROM public.event_types));
SELECT setval('public.events_seq', (SELECT MAX(event_id) FROM public.events));
SELECT setval('public.role_role_id_seq', (SELECT MAX(role_id) FROM public.role));
SELECT setval('public.user_type_seq', (SELECT MAX(user_type_id) FROM public.user_type));