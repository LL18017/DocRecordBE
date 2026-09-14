-- ----------------------------------------------------------------------------
-- El correo identifica una cuenta sin distinguir mayusculas
--
-- HU-02, criterio 4: "dado que me registre escribiendo el correo con mayusculas,
-- cuando inicio sesion escribiendolo igual, entonces el acceso funciona (el
-- correo se normaliza al guardar y al buscar)".
--
-- Se cumplia solo la mitad -- al buscar -- y ademas de la peor forma: la
-- consulta era `findByEmailContainingIgnoreCase`, es decir LIKE '%correo%', una
-- busqueda POR SUBCADENA usada para autenticar. Un correo inexistente que fuera
-- subcadena de uno real devolvia esa cuenta. Comprobado ejecutandolo:
-- `osa.portillo@ues.edu.sv` emitia un JWT valido de `rosa.portillo@ues.edu.sv`.
--
-- Arreglar solo la consulta no basta, y por eso existe esta migracion. El
-- UNIQUE de users.email distingue mayusculas, asi que `Ana@ues.edu.sv` y
-- `ana@ues.edu.sv` pueden coexistir. En cuanto coexisten, una busqueda que
-- ignora mayusculas encuentra DOS filas donde el codigo espera una, y Spring
-- Data lanza IncorrectResultSizeDataAccessException: el login de las dos
-- cuentas pasa a responder 500 sin explicacion. Se arregla una forma de entrar
-- indebida y se abre una forma de no poder entrar.
--
-- Por eso van juntas las tres cosas: normalizar lo que ya hay, impedir que
-- vuelva a pasar, y normalizar al guardar (eso ultimo, en los servicios de alta).
-- ----------------------------------------------------------------------------

-- (1) Lo que ya esta guardado. Se hace ANTES del indice: si hubiera dos correos
-- que solo difieren en mayusculas, el indice no podria crearse y la migracion
-- fallaria dejando claro que hay un conflicto que resolver a mano -- que es
-- justo lo que debe pasar, porque decidir cual de las dos cuentas se queda no
-- es algo que pueda hacer una migracion.
UPDATE public.users SET email = LOWER(email) WHERE email <> LOWER(email);

-- (2) Que no se repita. Indice UNICO sobre LOWER(email), no sobre la columna:
-- el UNIQUE que ya tiene la columna compara byte a byte y deja pasar
-- `Ana@ues.edu.sv` junto a `ana@ues.edu.sv`. Este los rechaza en la base, sin
-- depender de que ningun servicio se acuerde de normalizar.
CREATE UNIQUE INDEX ux_users_email_lower ON public.users (LOWER(email));
