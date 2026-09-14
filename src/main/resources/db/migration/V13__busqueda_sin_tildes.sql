-- ----------------------------------------------------------------------------
-- Buscar "Martinez" encuentra a "Martinez" y a "Martínez"
--
-- HU-07, criterio 3: "dado que escribo el nombre con distinta capitalizacion o
-- SIN TILDES, cuando busco, entonces igualmente encuentro al paciente".
--
-- Se cumplia la mitad: LOWER() resolvia las mayusculas, pero nada tocaba las
-- tildes, asi que `Martinez` no encontraba a `Martínez`. Y esa es justo la
-- forma en que se escribe cuando se busca deprisa -- en una consulta, con el
-- paciente enfrente -- que es el escenario que describe la historia.
--
-- Resolverlo en Java no sirve. La comparacion ocurre DENTRO de la base, en el
-- WHERE; normalizar el texto que teclea el usuario antes de mandarlo solo
-- cambia un lado de la comparacion, y el que esta guardado sigue con tildes.
-- Tiene que ser la base la que ignore los acentos en ambos lados.
-- ----------------------------------------------------------------------------

-- unaccent viene con el paquete contrib de PostgreSQL, presente en la imagen
-- oficial. Es una extension, no una funcion propia: por eso hace falta crearla
-- una vez por base de datos y por eso vive en una migracion.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ── Por que se envuelve en una funcion propia ────────────────────────────────
-- unaccent() se declara STABLE, no IMMUTABLE, porque depende del diccionario
-- que tenga instalado el servidor. PostgreSQL solo admite funciones IMMUTABLE
-- dentro de un indice, asi que usarla directamente en un CREATE INDEX falla.
-- Esta envoltura fija el diccionario explicitamente ('unaccent'), lo que la
-- hace inmutable de verdad, y entonces si puede indexarse.
CREATE OR REPLACE FUNCTION public.sin_tildes(texto text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, texto)
$$;

-- Indices sobre la expresion exacta que usa la consulta. Sin ellos, cada
-- busqueda recorreria la tabla entera aplicando la funcion fila por fila.
CREATE INDEX ix_persona_nombres_sin_tildes
    ON public.persona (LOWER(public.sin_tildes(nombres)));

CREATE INDEX ix_persona_apellidos_sin_tildes
    ON public.persona (LOWER(public.sin_tildes(apellidos)));

COMMENT ON FUNCTION public.sin_tildes(text) IS
    'unaccent con el diccionario fijado, para que sea IMMUTABLE y pueda usarse '
    'en indices. La usa la busqueda de pacientes (HU-07 criterio 3).';
