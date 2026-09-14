-- ----------------------------------------------------------------------------
-- token_restablecimiento
--
-- HU-04: recuperar la contrasena desde un enlace enviado al correo. El token
-- que viaja en ese enlace es lo unico que separa a cualquiera de la contrasena
-- de una cuenta ajena, asi que necesita su propia fila con vencimiento y marca
-- de uso.
--
-- ── Por que una tabla nueva y no una columna `tipo` en verification_token ──
-- La tabla que ya existe guarda tokens de CONFIRMACION DE CORREO, y la
-- tentacion era anadirle un discriminador y reutilizarla. Se descarto, y no
-- por gusto: los dos tokens no valen lo mismo. Confirmar un correo habilita
-- una cuenta; restablecer una contrasena la TOMA. Si los dos viven en la misma
-- tabla, lo unico que impide que un token de confirmacion sirva para cambiar
-- una contrasena es que cada consulta se acuerde de filtrar por el tipo.
--
-- Y hay tres consultas que hoy NO filtran nada, escritas cuando el tipo no
-- existia:
--
--   VerificationTokenRepository.findByToken(token)      -- AuthService.confirmToken
--   VerificationTokenRepository.deleteAllByUser(user)   -- UserService.asignarContrasena
--   VerificationTokenRepository.deleteAllByUser(user)   -- AuthService.registrarMedico
--
-- La primera es la peligrosa: sin filtro, confirmToken aceptaria un token de
-- restablecimiento, y el flujo de restablecimiento aceptaria uno de
-- confirmacion -- es decir, quien reciba un correo de "confirma tu cuenta"
-- podria usar ese mismo token para ponerle contrasena a la cuenta. Las otras
-- dos son el fallo silencioso: un administrador asignando una contrasena, o un
-- registro repetido, borrarian de paso los enlaces de recuperacion pendientes.
--
-- Con dos tablas nada de eso puede ocurrir por olvido: confirmToken consulta
-- `verification_token` y ahi no hay tokens de restablecimiento que encontrar.
-- La separacion la sostiene el esquema, no la disciplina de quien escriba la
-- siguiente consulta.
--
-- ── ON DELETE CASCADE, al contrario que verification_token ────────────────
-- Esta fila no es un hecho que haya que conservar: es un permiso temporal. Si
-- la cuenta se borra, el enlace pendiente no significa nada y ademas conviene
-- que desaparezca. No hay historial clinico que proteger aqui, al reves que en
-- signos_vitales.enfermera_id (ver V9), donde el RESTRICT es deliberado.
--
-- Nota aparte: `verification_token.user_id` NO tiene cascada, asi que borrar
-- un usuario con un token de verificacion pendiente falla por clave foranea.
-- Esta migracion no lo toca -- es un problema anterior y de otra historia.
-- ----------------------------------------------------------------------------
CREATE TABLE public.token_restablecimiento (
    token_id  BIGSERIAL PRIMARY KEY,

    -- UNIQUE porque el token ES la credencial: dos filas con el mismo valor
    -- harian que la busqueda devolviera dos resultados donde el codigo espera
    -- uno. Es el mismo UNIQUE que ya tiene verification_token.token.
    token     VARCHAR(255) NOT NULL UNIQUE,

    user_id   INTEGER NOT NULL
        REFERENCES public.users (user_id) ON DELETE CASCADE,

    -- Se guarda el vencimiento y no la duracion: asi un cambio del plazo en el
    -- codigo no alarga retroactivamente los enlaces ya enviados, que es
    -- exactamente lo que no debe pasar con una credencial.
    vence_en  TIMESTAMP NOT NULL,

    -- Un enlace sirve UNA vez. Sin esta marca, quien vea el correo mas tarde
    -- -o quien lo tenga reenviado- vuelve a cambiar la contrasena.
    usado     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Para invalidar de un golpe los demas enlaces pendientes de una cuenta cuando
-- uno de ellos se usa (ver RecuperacionDeContrasenaService.restablecer). Sin
-- indice eso recorreria la tabla entera en cada restablecimiento.
CREATE INDEX idx_token_restablecimiento_user ON public.token_restablecimiento (user_id);
