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
-- Ya NO se siembra aqui: el catalogo de roles se mudo a la migracion
-- V7__catalogo_de_roles_cerrado.sql, junto con el NOT NULL, el UNIQUE y el
-- CHECK que lo cierran a los cuatro valores de RolesEnum.
--
-- El motivo es que la semilla y la restriccion que esa semilla debe cumplir
-- tienen que viajar juntas. Separadas, este archivo podia insertar un nombre
-- que la restriccion rechazara y tumbar el arranque, o peor, quedarse
-- desincronizado del enum sin que nada lo detectara. Ademas data.sql corre en
-- cada arranque y no esta versionado: no habia forma de saber que catalogo
-- tiene una base concreta.
-- =========================

-- =========================
-- Ajuste de secuencias: tras insertar IDs explicitos, las secuencias deben
-- continuar despues del maximo para no chocar con los catalogos.
-- COALESCE cubre el caso de una tabla vacia (setval no acepta NULL).
--
-- GREATEST con last_value es lo que impide que este script RETROCEDA una
-- secuencia, y no es un adorno. Las secuencias tienen INCREMENT BY 50 porque
-- Hibernate reserva los identificadores de 50 en 50 y los va repartiendo en
-- memoria: la secuencia ya va por 63 aunque la tabla solo llegue a 13. Un
-- setval al MAX de la tabla devolveria el contador a 13 y los siguientes
-- INSERT chocarian contra filas que ya existen ("duplicate key value violates
-- unique constraint"). Se vio en la suite: dos contextos de Spring contra la
-- misma base, el segundo rebobinaba la secuencia que el primero ya habia
-- repartido. Con GREATEST la secuencia solo avanza.
-- =========================
SELECT setval('public.event_status_seq', GREATEST(COALESCE((SELECT MAX(event_status_id) FROM public.event_status), 1), (SELECT last_value FROM public.event_status_seq)));
SELECT setval('public.event_types_seq',  GREATEST(COALESCE((SELECT MAX(event_type_id)   FROM public.event_types),  1), (SELECT last_value FROM public.event_types_seq)));
SELECT setval('public.events_seq',       GREATEST(COALESCE((SELECT MAX(event_id)        FROM public.events),       1), (SELECT last_value FROM public.events_seq)));
-- role_role_id_seq no aparece aqui: su ajuste se fue con la semilla a la V7.
