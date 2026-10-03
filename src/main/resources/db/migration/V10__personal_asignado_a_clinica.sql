-- ----------------------------------------------------------------------------
-- clinica_personal
--
-- QUIEN TRABAJA en una clinica, que no es lo mismo que QUIEN LA REGISTRO.
--
-- ── El problema concreto que resuelve ──────────────────────────────────────
-- `clinicas.user_id` es el dueño: quien dio de alta la sede. De ahi salia todo
-- -/clinics/mias devuelve findByUser(...)-, asi que el sistema solo sabia
-- responder "que clinicas creaste tu". Para un medico que registra su propia
-- consulta eso alcanza. Para enfermeria no: una enfermera NUNCA da de alta una
-- clinica, trabaja en la que otro registro, y con el modelo anterior su lista
-- salia vacia. La pantalla de seleccion de clinica la dejaba encallada antes
-- de poder hacer nada, que es justo donde se atasco al probarla.
--
-- Reasignar `clinicas.user_id` a la enfermera NO era la salida: se la quitaria
-- al medico que la registro, porque esa columna es una sola y significa
-- propiedad.
--
-- ── Por que una tabla y no una columna mas ────────────────────────────────
-- La relacion es de muchos a muchos en los dos sentidos, y los dos se dan en
-- la practica: una clinica tiene varias enfermeras, y en El Salvador es comun
-- que la misma persona cubra turnos en mas de una sede. Cualquier columna
-- -aqui o en `users`- solo podria guardar uno de los dos lados.
--
-- Se guarda contra `users` y no contra `enfermeras` a proposito: un medico que
-- pasa consulta en la clinica de un colega tiene exactamente el mismo
-- problema, y la solucion no deberia tener que reescribirse cuando aparezca.
--
-- ── Borrado en cascada por los dos lados ──────────────────────────────────
-- Esta tabla no guarda ningun hecho clinico, solo una asignacion vigente. Si
-- se borra la clinica o la cuenta, la fila deja de significar nada y estorba;
-- no hay historial que proteger, al reves que en signos_vitales.enfermera_id.
-- ----------------------------------------------------------------------------
CREATE TABLE public.clinica_personal (
    clinica_id INTEGER NOT NULL
        REFERENCES public.clinicas (clinica_id) ON DELETE CASCADE,

    user_id    INTEGER NOT NULL
        REFERENCES public.users (user_id) ON DELETE CASCADE,

    -- La clave compuesta es tambien la regla de negocio: la misma persona no
    -- puede estar asignada dos veces a la misma sede. Sin ella, pulsar dos
    -- veces "asignar" dejaria filas duplicadas y la clinica saldria repetida
    -- en el selector.
    PRIMARY KEY (clinica_id, user_id)
);

-- La consulta que manda es "que clinicas puede operar este usuario", que es lo
-- que dispara la pantalla de seleccion nada mas entrar. La PK ya indexa por
-- (clinica_id, user_id), que no sirve para buscar por user_id solo.
CREATE INDEX idx_clinica_personal_user ON public.clinica_personal (user_id);
