-- ----------------------------------------------------------------------------
-- HU-15 · Expediente clinico consolidado (DRS-87): en que sede se tomo cada
-- toma de signos vitales.
--
-- El criterio 3 pide que cada evento de la linea de tiempo diga en que clinica
-- y que profesional lo registro. La consulta ya guarda su sede (V3) y la receta
-- hereda la de su consulta, pero la toma de constantes no la guardaba: solo
-- sabia QUIEN la tomo, no DONDE. Y la enfermera no alcanza para deducirlo,
-- porque trabaja en varias sedes (clinica_personal) y hoy puede estar en otra.
--
-- ── Opcional, como en la consulta ─────────────────────────────────────────
-- Null cuando no se sabe. Una enfermera sin sede activa en la sesion registra
-- igual, por lo mismo que un medico sin sucursales atiende igual
-- (ConsultaRequestDto.clinicaId).
--
-- ── Que se rellena de lo ya registrado ────────────────────────────────────
-- Solo las tomas ligadas a una consulta con sede: ahi la sede es un hecho, la
-- de la consulta que la toma precede. Las demas quedan en null y la linea de
-- tiempo lo dice ("sede no registrada"). Tomar la unica sede actual de la
-- enfermera seria adivinar: el hueco honesto gana al dato inventado.
--
-- Numero de version: V36 es de HU-34, que se trabaja en paralelo. Flyway
-- aplica fuera de orden (FlywayBootstrap), asi que da igual cual llegue antes.
-- ----------------------------------------------------------------------------

ALTER TABLE public.signos_vitales
    ADD COLUMN clinica_id INTEGER REFERENCES public.clinicas (clinica_id);

UPDATE public.signos_vitales s
   SET clinica_id = c.clinica_id
  FROM public.consultas c
 WHERE s.consulta_id = c.consulta_id
   AND c.clinica_id IS NOT NULL;
