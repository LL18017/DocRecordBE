-- user_type queda reemplazada por el modelo de roles/especializaciones: el
-- perfil de una persona se sabe por su rol (role/user_roles) y por tener
-- fila en medicos/enfermeras/pacientes, no por una columna aparte que ahora
-- duplicaba esa informacion.
ALTER TABLE public.users DROP COLUMN user_type_id;
DROP TABLE public.user_type;
