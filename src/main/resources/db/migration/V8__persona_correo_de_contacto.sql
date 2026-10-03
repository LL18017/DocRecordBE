-- ============================================================================
-- V8 -- La persona gana un correo de contacto
--
-- Hasta hoy el correo solo existe en `users.email`, es decir solo lo tiene
-- quien YA tiene cuenta en el sistema. Un paciente no se autoregistra y casi
-- nunca tiene cuenta, asi que en la practica ningun paciente tiene correo: el
-- formulario de alta ni siquiera lo pide.
--
-- Eso bloquea DRS-94 (mandarle la receta al paciente por correo) por una razon
-- artificial. La receta se le manda a la PERSONA, tenga o no cuenta; exigirle
-- una cuenta para poder recibir un PDF es atarle un requisito que no le
-- corresponde. Y `persona` ya es la raiz comun de medicos, enfermeras y
-- pacientes, asi que es donde el dato pertenece.
--
-- ── Por que es NULLABLE ───────────────────────────────────────────────────
-- Hay pacientes sin correo y es legitimo -- un adulto mayor que llega a
-- consulta y no usa correo existe, y su expediente tiene que poder crearse
-- igual. Lo unico que no podra es tener portal, que es justo la regla de
-- negocio que se quiere: el acceso lo concede la clinica y solo si hay correo.
-- Una columna obligatoria no conseguiria mas correos, conseguiria correos
-- inventados por el personal para poder guardar el formulario, y un correo
-- inventado es peor que ninguno: DRS-94 mandaria la receta de un paciente a
-- una direccion que no es suya.
--
-- ── Por que NO lleva UNIQUE ───────────────────────────────────────────────
-- Fue la decision menos obvia de esta migracion. Se descarto el UNIQUE por
-- tres razones, en orden de peso:
--
-- 1. Este correo es un CANAL DE CONTACTO, no una identidad. La identidad de
--    una persona en este modelo es `persona_id`, y su documento es `dui`, que
--    si es UNIQUE desde V2. Los canales de contacto se comparten de forma
--    legitima: por eso `telefono` tampoco es unico -- una familia comparte el
--    fijo de la casa y nadie considera que eso sea un dato corrupto.
--
-- 2. El caso que un UNIQUE romperia es real y frecuente: una madre y su hijo
--    menor de edad atendidos en la misma clinica, con el correo de la madre
--    como contacto de ambos. Con UNIQUE, el personal no podria registrar al
--    segundo y haria lo unico que puede hacer para terminar su trabajo:
--    inventarse una direccion. Volvemos al correo falso, que es exactamente
--    lo que la nulabilidad de arriba intenta evitar.
--
-- 3. El invariante que de verdad importa YA esta garantizado, y en el lugar
--    correcto. Lo que no puede pasar es que dos personas entren al portal con
--    la misma cuenta, y eso lo impide `users`: `email` es UNIQUE desde V1
--    (uk6dotkott2kjsp8vw4d0m25fb7) y `persona_id` es UNIQUE desde V2
--    (uk_users_persona_id). Cuando la clinica conceda el acceso se creara una
--    fila en `users`, y ahi el motor rechazara el duplicado. Poner UNIQUE
--    tambien en `persona.email` no anadiria ninguna garantia nueva; solo
--    adelantaria el rechazo a un momento en el que todavia es legitimo.
--
-- Un cuarto caso que conviene descartar explicitamente porque suena parecido y
-- no lo es: que una persona sea enfermera Y paciente a la vez -- previsto por
-- el diseno de este sistema -- NO produce correos repetidos. Es una sola fila
-- de `persona` con varios papeles, no dos filas.
--
-- Coste de equivocarse, en cada direccion:
--   sin UNIQUE y resultando que hacia falta -> se deduplica y se agrega la
--     restriccion en una migracion posterior. Molesto, pero reversible.
--   con UNIQUE y apareciendo la madre y el hijo -> el personal fabrica
--     correos, DRS-94 manda recetas a direcciones ajenas y el dato malo ya se
--     repartio. Silencioso y no reversible.
-- Ante la duda se elige el error del que se puede volver.
--
-- ── Por que si lleva un CHECK, y por que tan flojo ────────────────────────
-- El formato se valida con Bean Validation (@Email) en el DTO y en la entidad,
-- que es donde produce un 400 legible para el frontend. El CHECK de aqui es la
-- red de abajo, en la linea de V7: un dato invalido no deberia poder EXISTIR,
-- y no todo camino de escritura pasa por el validador (un script, una carga
-- masiva, una consola de psql).
--
-- Es deliberadamente flojo -- exige una arroba con algo a cada lado y nada
-- mas. Un CHECK que intente validar RFC 5322 acaba rechazando direcciones
-- validas raras y no se puede cambiar sin otra migracion. El trabajo fino lo
-- hace @Email; esto solo ataja la basura evidente ('n/a', 'no tiene', '').
-- ============================================================================

ALTER TABLE public.persona
    ADD COLUMN email VARCHAR(255) NULL;

ALTER TABLE public.persona
    ADD CONSTRAINT persona_email_check
        CHECK (email IS NULL OR email LIKE '%_@_%');

COMMENT ON COLUMN public.persona.email IS
    'Correo de contacto de la persona. Opcional y NO unico: es un canal, no una '
    'identidad, y puede compartirse (madre e hijo menor). La unicidad de la '
    'cuenta la garantiza users.email. Es el prerrequisito del portal del '
    'paciente y el destino de la receta en DRS-94.';

-- Nota: NO se rellena `persona.email` a partir de `users.email` para las
-- personas que ya tienen cuenta. Seria facil y parece gratis, pero es un
-- cambio de datos que nadie pidio y que no se puede distinguir despues de un
-- correo capturado a mano. Si el equipo lo quiere, que sea una migracion
-- propia y explicita.
