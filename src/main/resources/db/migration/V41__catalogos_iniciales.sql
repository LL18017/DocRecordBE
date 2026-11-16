-- DEF-01: los catalogos de tipos de cita y de tipos de habito nacian vacios en
-- una base nueva, y agendar una cita (tipoCitaId) o registrar un habito
-- (tipoHabitoId) exigen un tipo. Se siembran los valores base.
--
-- Idempotente: cada fila se inserta solo si no existe ya una con el mismo
-- nombre (sin distinguir mayusculas), asi que una base con catalogos cargados a
-- mano no recibe duplicados y la migracion no pisa nada.

-- tipos_cita: tipo_cita_id es IDENTITY, nombre es la unica columna obligatoria.
INSERT INTO public.tipos_cita (nombre, descripcion)
SELECT v.nombre, v.descripcion
FROM (VALUES
    ('Consulta general',             'Atención médica general por un motivo de consulta'),
    ('Control',                      'Control periódico de un paciente ya atendido'),
    ('Primera vez',                  'Primera consulta del paciente en la clínica'),
    ('Seguimiento de crónicos',      'Seguimiento de enfermedades crónicas como diabetes e hipertensión'),
    ('Resultados de laboratorio',    'Revisión de resultados de exámenes de laboratorio')
) AS v (nombre, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM public.tipos_cita t WHERE lower(t.nombre) = lower(v.nombre)
);

-- tipos_habito: id no es IDENTITY; lo da tipos_habito_seq (la misma que usa
-- Hibernate, incremento 50). Se toma con nextval para no chocar con los ids que
-- Hibernate reparte despues. nextval solo corre para las filas que pasan el
-- NOT EXISTS, por eso la clausula va en la misma SELECT.
INSERT INTO public.tipos_habito (id, descripcion)
SELECT nextval('tipos_habito_seq'), v.descripcion
FROM (VALUES
    ('Tabaquismo'),
    ('Consumo de alcohol'),
    ('Actividad física'),
    ('Alimentación'),
    ('Sueño'),
    ('Consumo de agua')
) AS v (descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM public.tipos_habito t WHERE lower(t.descripcion) = lower(v.descripcion)
);
