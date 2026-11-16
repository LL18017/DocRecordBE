-- HU-32: el motivo de cancelacion de una cita pasa a admitir hasta 500
-- caracteres (minimo 10, que valida el servicio), como las anulaciones.
ALTER TABLE public.citas ALTER COLUMN motivo_cancelacion TYPE VARCHAR(500);

-- HU-16 criterio 4: una toma puede anularla tambien un administrador, que no
-- es enfermera. anulada_por pasa de apuntar a enfermeras a apuntar a persona
-- (la enfermera autora sigue siendo una persona; los ids no cambian).
ALTER TABLE public.signos_vitales DROP CONSTRAINT IF EXISTS signos_vitales_anulada_por_fkey;
ALTER TABLE public.signos_vitales
    ADD CONSTRAINT signos_vitales_anulada_por_fkey
        FOREIGN KEY (anulada_por) REFERENCES public.persona (persona_id) ON DELETE RESTRICT;
