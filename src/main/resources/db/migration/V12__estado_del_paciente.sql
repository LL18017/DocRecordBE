-- ----------------------------------------------------------------------------
-- Un paciente puede darse de baja sin que su expediente desaparezca
--
-- HU-06, criterio 4: "dado un paciente recien creado, cuando lo consulto,
-- entonces tiene un codigo unico asignado y estado ACTIVO".
--
-- El codigo unico ya existia (`expediente`); el estado no existia en ninguna
-- parte. La pantalla de pacientes pintaba igualmente una insignia "Activo" en
-- cada fila, fija en el componente, sin nada detras: decia lo mismo de todos
-- los pacientes porque no leia ningun dato. Un dato que no puede ser falso
-- tampoco puede ser verdadero, y ahi no informaba nada.
--
-- Por que una columna y no un borrado: un expediente clinico no se borra. Si
-- un paciente deja de atenderse, lo que cambia es que no debe aparecer en los
-- listados de trabajo diario -- sus consultas, sus constantes y sus recetas
-- siguen existiendo y siguen siendo consultables. Eso es exactamente un
-- estado, no un DELETE.
-- ----------------------------------------------------------------------------

ALTER TABLE public.pacientes
    ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO';

-- El CHECK y no un ENUM de PostgreSQL: anadir un valor a un tipo ENUM exige
-- ALTER TYPE, que no se puede ejecutar dentro de una transaccion en versiones
-- anteriores a la 12 y complica cualquier migracion futura. Con dos valores
-- previstos, un CHECK dice lo mismo y se modifica sin ceremonia.
ALTER TABLE public.pacientes
    ADD CONSTRAINT pacientes_estado_check
        CHECK (estado IN ('ACTIVO', 'INACTIVO'));

-- Los listados filtran por estado en cuanto hay pacientes dados de baja, y sin
-- indice eso es un recorrido completo de la tabla en cada carga de pantalla.
CREATE INDEX ix_pacientes_estado ON public.pacientes (estado);

COMMENT ON COLUMN public.pacientes.estado IS
    'ACTIVO o INACTIVO. Dar de baja oculta al paciente de los listados de '
    'trabajo, pero su expediente, consultas y constantes siguen intactos.';
