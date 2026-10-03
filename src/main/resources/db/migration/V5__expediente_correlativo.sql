-- ============================================================================
-- V5 -- El numero de expediente pasa a ser correlativo, generado por el sistema
--
-- Hasta ahora lo escribia a mano quien daba de alta al paciente. Eso es pedir
-- colisiones: el UNIQUE de la columna rechaza el duplicado, pero el usuario
-- recibe un error incomprensible despues de haber llenado tres pasos de
-- formulario, y no tiene forma de saber cual es el siguiente numero libre.
--
-- Se usa una SECUENCIA de PostgreSQL y no MAX(expediente)+1 a proposito: dos
-- altas simultaneas leerian el mismo maximo y generarian el mismo numero. Una
-- secuencia entrega valores distintos aunque dos transacciones la pidan a la
-- vez, y no retrocede aunque una de ellas haga rollback -- que es justo lo que
-- se quiere de un correlativo de expediente.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS expediente_seq START WITH 1 INCREMENT BY 1;

-- Los expedientes que ya existen se escribieron a mano y no siguen un unico
-- formato (hay 'P-000001', 'P-100001' y 'EXP-0001'). No se renumeran: cambiar
-- el identificador de un expediente clinico ya emitido romperia cualquier
-- referencia en papel. Solo se adelanta la secuencia mas alla de los numeros
-- que ya estan en uso con el formato nuevo, para que no colisione.
SELECT setval(
    'expediente_seq',
    GREATEST(
        1,
        COALESCE(
            (SELECT MAX(CAST(SUBSTRING(expediente FROM '^EXP-([0-9]+)$') AS BIGINT))
             FROM pacientes
             WHERE expediente ~ '^EXP-[0-9]+$'),
            0
        )
    )
);
