-- ----------------------------------------------------------------------------
-- HU-03 · Restablecer la contrasena cierra las sesiones abiertas (DRS-8,
-- criterio 4).
--
-- El JWT no se puede revocar: vale hasta su "exp" y nada en la base lo
-- conoce. Para poder cerrar las sesiones de una cuenta se guarda aqui un
-- contador; cada token lo lleva como claim y el filtro lo compara con el de la
-- base en cada peticion. Restablecer la contrasena lo incrementa, y desde ese
-- momento todo token emitido antes (de acceso o de refresco) trae un valor
-- viejo y deja de servir.
--
-- DEFAULT 0 para las cuentas que ya existen: un token emitido antes de esta
-- migracion no trae el claim, se lee como 0 y sigue valiendo hasta que la
-- cuenta restablezca su contrasena; nadie queda fuera por desplegar.
-- ----------------------------------------------------------------------------
ALTER TABLE public.users
    ADD COLUMN version_de_sesion INTEGER NOT NULL DEFAULT 0;
