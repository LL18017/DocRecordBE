-- ============================================================================
-- V7 -- El catalogo de roles deja de ser texto libre
--
-- `role.name` se declaro en V1 como `character varying(255)` sin NOT NULL, sin
-- UNIQUE y sin CHECK. Es decir: la base acepta un rol llamado NULL, uno llamado
-- 'JEFASO' y dos filas llamadas 'ADMIN' con ids distintos. Pero el rol NO es
-- texto libre: es un enum cerrado de cuatro valores (RolesEnum), y todo el
-- codigo lo trata asi -- CustomUserDetails compone la autoridad como
-- "ROLE_" + name, y RoleMapper resuelve el id buscando ese name en el enum.
--
-- La consecuencia ya se pago fuera de este repositorio. Un nombre que el enum
-- no conoce hacia que RoleMapper.toDto devolviera `id: null`, ese null viajaba
-- al cliente, y el frontend tuvo que blindarse contra el (`rol.id ?? rol.name`
-- y una guarda para que `nombre.toUpperCase()` no reventara la pantalla de
-- usuarios entera). Eso es curar el sintoma en el ultimo eslabon: el dato
-- invalido ya recorrio la base, el ORM, el servicio y la red antes de que
-- alguien lo notara.
--
-- El arreglo va donde esta la causa. Un rol invalido no deberia poder EXISTIR,
-- y hasta hoy podia. Con NOT NULL + UNIQUE + CHECK, el motor rechaza la fila en
-- el INSERT y ni el mapper ni el frontend tienen que defenderse de nada.
--
-- ── Por que NO se renombran las filas que no cuadren ──────────────────────
-- Esta migracion inserta los cuatro roles que falten, pero NO corrige el
-- nombre de una fila que ya exista con otro valor. Renombrar el rol 2 de
-- 'ADMIN' a 'MEDICO' no es un arreglo de datos: es un cambio de autorizacion
-- silencioso para todos los usuarios que apuntan a ese id en user_roles. Si
-- una fila diverge, el CHECK o el UNIQUE de mas abajo hacen fallar el
-- despliegue con el nombre de la restriccion en el error, y se decide a mano.
-- Detener un despliegue es barato; re-autorizar usuarios sin que nadie lo vea
-- no lo es.
--
-- Se comprobo antes de escribirla: en `datadoc` (desarrollo, con V6 aplicada)
-- la tabla tiene exactamente las cuatro filas canonicas, ningun NULL y ningun
-- nombre repetido, asi que la migracion aplica limpio. Para revisarlo en otro
-- despliegue antes de subir esta version:
--
--   SELECT * FROM public.role
--    WHERE name IS NULL
--       OR name NOT IN ('ADMIN', 'MEDICO', 'ENFERMERA', 'PACIENTE');
--   SELECT name, count(*) FROM public.role GROUP BY name HAVING count(*) > 1;
--
-- ── Por que el catalogo se siembra AQUI y ya no en data.sql ───────────────
-- data.sql corre en cada arranque y no esta versionado: no hay forma de saber
-- que version del catalogo tiene una base. Ademas la semilla y la restriccion
-- que esa semilla debe satisfacer estaban en archivos distintos, de modo que
-- podian desincronizarse. Aqui llegan juntas y una sola vez. La semilla de
-- roles se retiro de data.sql en el mismo commit.
-- ============================================================================

-- 1. Los cuatro roles del dominio. DO NOTHING y no DO UPDATE: ver arriba.
--    Debe coincidir con ues.edu.sv.education.model.enums.RolesEnum; la prueba
--    CatalogoDeRolesIT falla si el enum y esta tabla dejan de coincidir.
INSERT INTO public.role (role_id, name) VALUES
    (1, 'ADMIN'),
    (2, 'MEDICO'),
    (3, 'ENFERMERA'),
    (4, 'PACIENTE')
ON CONFLICT (role_id) DO NOTHING;

-- 2. Un rol sin nombre no es un rol. Antes se podia insertar y solo se
--    descubria cuando el mapper devolvia id nulo, o cuando "ROLE_" + null
--    producia la autoridad "ROLE_null".
ALTER TABLE public.role
    ALTER COLUMN name SET NOT NULL;

-- 3. Dos filas con el mismo nombre y distinto id son dos roles que el codigo
--    no puede distinguir: getIdByName devuelve siempre el mismo id, asi que
--    los usuarios de la segunda fila se mapearian a la primera.
ALTER TABLE public.role
    ADD CONSTRAINT role_name_unico UNIQUE (name);

-- 4. La regla de fondo: el catalogo es cerrado. Sin esto los puntos 2 y 3 solo
--    garantizan que hay un nombre y que no se repite, no que signifique algo.
ALTER TABLE public.role
    ADD CONSTRAINT role_name_del_catalogo
    CHECK (name IN ('ADMIN', 'MEDICO', 'ENFERMERA', 'PACIENTE'));

-- 5. Los ids se insertaron explicitamente, asi que la secuencia de identidad
--    sigue en 1 y el proximo INSERT sin id chocaria contra el rol ADMIN.
--    GREATEST evita retroceder la secuencia si ya iba mas adelante.
SELECT setval(
    'public.role_role_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(role_id) FROM public.role), 1),
        (SELECT last_value FROM public.role_role_id_seq)
    )
);
