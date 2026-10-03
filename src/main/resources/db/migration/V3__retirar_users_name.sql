-- La entidad User ya no mapea `name` (la identidad de una persona vive en
-- persona.nombres/apellidos desde V2); esta columna quedaba huerfana a
-- proposito hasta este commit, que es el que actualiza la entidad. Ver el
-- comentario al final de V2__modelo_clinico.sql.
ALTER TABLE public.users DROP COLUMN name;
