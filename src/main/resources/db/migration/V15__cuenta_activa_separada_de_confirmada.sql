-- ----------------------------------------------------------------------------
-- "Confirmo su correo" y "puede entrar hoy" dejan de ser la misma columna
--
-- HU-05, criterio 3: "dado un usuario activo, cuando lo cambio a inactivo,
-- entonces deja de poder iniciar sesion, PERO sus registros clinicos
-- anteriores siguen existiendo".
--
-- ── El problema ──────────────────────────────────────────────────────────────
-- `users.enabled` ya cargaba con dos significados antes de esta historia:
--
--   · "esta cuenta confirmo su correo"   AuthService la pone en false al
--                                        registrar y en true al confirmar.
--   · "esta persona sigue trabajando"    EnfermeraService.darDeBaja la apaga
--                                        al dar de baja a una enfermera.
--
-- Implementar el criterio 3 encima habria anadido un tercero, y dos de ellos
-- se contradicen en un caso concreto y nada raro:
--
--   UserService.asignarContrasena pone enabled = true, porque asignarle una
--   contrasena a alguien equivale a confirmar su cuenta. Con una sola columna,
--   un administrador que le restablece la contrasena a una cuenta DESACTIVADA
--   la estaria reactivando sin pretenderlo ni enterarse. La baja se deshace
--   sola, y en silencio.
--
-- Lo mismo con la recuperacion de contrasena de HU-04: cualquiera a quien
-- acaban de desactivar podria volver a entrar pidiendo recuperar su clave.
--
-- ── La separacion ────────────────────────────────────────────────────────────
--   enabled → el correo esta confirmado. Lo mueve el propio usuario al abrir
--             el enlace, o un administrador al asignarle contrasena.
--   activo  → la organizacion le permite entrar. Solo lo mueve un
--             administrador, y ninguna accion del propio usuario lo toca.
--
-- El login exige las dos, y las distingue: `enabled` viaja en isEnabled() y
-- `activo` en isAccountNonLocked(), que ya daban mensajes distintos
-- -- "no ha confirmado su cuenta" frente a "usuario bloqueado" --. Con una
-- sola columna, a quien acaban de desactivar se le decia que confirmara un
-- correo que confirmo hace meses.
-- ----------------------------------------------------------------------------

-- DEFAULT true: lo que ya existe estaba activo. La alternativa -- copiar
-- `enabled` -- dejaria desactivadas a las cuentas que solo estaban pendientes
-- de confirmar su correo, que es precisamente la confusion que esta migracion
-- viene a deshacer.
ALTER TABLE public.users
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;

-- Las enfermeras dadas de baja son el unico caso en el que `enabled = false`
-- significaba de verdad "no debe entrar" y no "no ha confirmado". Se traslada
-- ese significado a la columna nueva y se les devuelve `enabled`, que es lo
-- que siempre fue cierto de ellas: su correo estaba confirmado.
UPDATE public.users u
SET activo = FALSE
WHERE EXISTS (
    SELECT 1
    FROM public.enfermeras e
    WHERE e.persona_id = u.persona_id
      AND e.activo = FALSE
);

COMMENT ON COLUMN public.users.enabled IS
    'El correo de esta cuenta esta confirmado. Lo mueve el propio usuario al '
    'abrir el enlace de confirmacion, o un administrador al asignarle contrasena.';

COMMENT ON COLUMN public.users.activo IS
    'La organizacion le permite entrar (HU-05 criterio 3). Solo lo mueve un '
    'administrador; ninguna accion del propio usuario lo toca. Desactivar no '
    'borra nada: sus registros clinicos anteriores siguen existiendo.';
