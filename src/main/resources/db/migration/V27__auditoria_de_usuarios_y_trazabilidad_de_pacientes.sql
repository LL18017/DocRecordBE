-- ----------------------------------------------------------------------------
-- HU-05 criterio 5 · Auditoria de usuarios (DRS-78)
-- HU-07 criterio 4 / HU-09 criterio 4 · Trazabilidad del paciente (DRS-15, DRS-81)
-- ----------------------------------------------------------------------------

-- Bitacora de cambios sobre cuentas: quien hizo que, sobre quien y cuando.
--
-- Sin FK hacia users, a proposito: UserService.deleteUser borra la fila de
-- usuario, y una restriccion (o un CASCADE) haria que borrar la cuenta borrara
-- tambien la huella de lo que se hizo con ella, que es justo lo que una
-- bitacora tiene que conservar. Por la misma razon `realizado_por` guarda el
-- correo de quien opero tal como era en ese momento, no un id que pueda quedar
-- huerfano.
CREATE TABLE public.bitacora_de_usuarios (
    id                  BIGSERIAL    PRIMARY KEY,
    usuario_afectado_id INTEGER      NOT NULL,
    accion              VARCHAR(30)  NOT NULL,
    detalle             VARCHAR(255),
    realizado_por       VARCHAR(100) NOT NULL,
    realizado_en        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT bitacora_de_usuarios_accion_check
        CHECK (accion IN ('ALTA', 'ACTIVACION', 'DESACTIVACION', 'ROL_ASIGNADO', 'ROL_RETIRADO'))
);

CREATE INDEX ix_bitacora_de_usuarios_afectado
    ON public.bitacora_de_usuarios (usuario_afectado_id, realizado_en DESC);

COMMENT ON TABLE public.bitacora_de_usuarios IS
    'Altas, cambios de estado y cambios de rol de cuentas, con quien los hizo y cuando.';

-- Trazabilidad del paciente: quien lo registro, en que clinica y quien lo
-- modifico por ultima vez. Las filas anteriores a esta migracion quedan en NULL:
-- no se inventa un autor que no se guardo.
--
-- ON DELETE SET NULL: dar de baja una cuenta o una clinica no debe impedir
-- borrarla ni arrastrar al paciente; solo se pierde el vinculo.
ALTER TABLE public.pacientes
    ADD COLUMN registrado_por_user_id   INTEGER REFERENCES public.users (user_id) ON DELETE SET NULL,
    ADD COLUMN registrado_en_clinica_id INTEGER REFERENCES public.clinicas (clinica_id) ON DELETE SET NULL,
    ADD COLUMN modificado_por_user_id   INTEGER REFERENCES public.users (user_id) ON DELETE SET NULL,
    ADD COLUMN modificado_en            TIMESTAMP;

COMMENT ON COLUMN public.pacientes.registrado_en_clinica_id IS
    'Sede activa de quien registro al paciente. NULL si no la indico (p. ej. un administrador).';
