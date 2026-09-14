-- ----------------------------------------------------------------------------
-- signos_vitales
--
-- La toma de constantes que precede a la consulta: el triage. Enfermeria la
-- registra, el medico la lee antes de diagnosticar. Esa division no es un
-- capricho de permisos sino la forma en que funciona una consulta: quien pesa,
-- mide y toma la presion no es quien diagnostica.
--
-- ── Por que columnas numericas y no texto ──────────────────────────────────
-- La maqueta del frontend guardaba "120/80 mmHg", "36.8°C", "72 kg": cadenas
-- con la unidad pegada. Es el mismo error que V6 evito en
-- prescripcion_medicamentos, y por el mismo motivo: con texto libre no se
-- puede responder "que pacientes tienen la sistolica sobre 140", que es
-- justamente lo que se le pide a un expediente electronico. La unidad es fija
-- y conocida por columna (kg, cm, grados Celsius, mmHg, lpm, rpm, %), asi que
-- no hay nada que guardar de ella.
--
-- La presion se parte en dos columnas por lo mismo. "120/80" es un par de
-- numeros que la costumbre escribe con una barra, no un dato unico.
--
-- ── Por que casi todo admite NULL ──────────────────────────────────────────
-- Una toma real es parcial muy a menudo: se toma la presion y el pulso en un
-- control rapido, y no se pesa ni se mide. Exigir las ocho obligaba a
-- inventar las que faltan, y un signo vital inventado en un expediente es
-- peor que uno ausente -mismo principio que el frontend ya aplica al mostrar
-- el hueco honesto en vez de datos de relleno.
--
-- Los rangos (CHECK) no son validacion de formato sino de plausibilidad
-- fisiologica: no existe un ser humano a 90 grados ni con una saturacion de
-- 150%. Un dedo que resbala en el teclado no puede quedar guardado como
-- historia clinica. Los limites son anchos a proposito -cubren desde un
-- neonato hasta un caso critico-, porque el objetivo es atajar el error de
-- captura, no opinar sobre el paciente.
-- ----------------------------------------------------------------------------
CREATE TABLE public.signos_vitales (
    signos_vitales_id   BIGSERIAL PRIMARY KEY,

    paciente_id         BIGINT NOT NULL
        REFERENCES public.pacientes (persona_id) ON DELETE CASCADE,

    -- Quien tomo la constante. Apunta a `enfermeras` y no a `persona` porque
    -- una toma sin responsable identificable no es un registro clinico: es un
    -- numero suelto. Sale del token (ver EnfermeraAutenticado), nunca del
    -- cuerpo de la peticion, igual que consultas.medico_id.
    --
    -- Sin ON DELETE CASCADE, y es deliberado: dar de baja a una enfermera no
    -- puede borrar el historial de constantes que tomo. RESTRICT obliga a
    -- resolverlo a mano si algun dia se intenta.
    enfermera_id        BIGINT NOT NULL
        REFERENCES public.enfermeras (persona_id) ON DELETE RESTRICT,

    -- La consulta a la que precede, cuando ya existe. Admite NULL porque el
    -- orden real es el inverso: enfermeria toma las constantes ANTES de que
    -- el medico abra la consulta, asi que en el momento del INSERT casi nunca
    -- hay una consulta a la que apuntar.
    consulta_id         BIGINT
        REFERENCES public.consultas (consulta_id) ON DELETE SET NULL,

    tomado_en           TIMESTAMP NOT NULL,

    peso_kg             NUMERIC(5,2) CHECK (peso_kg > 0 AND peso_kg <= 500),
    estatura_cm         NUMERIC(5,2) CHECK (estatura_cm > 0 AND estatura_cm <= 300),
    temperatura_c       NUMERIC(4,1) CHECK (temperatura_c >= 25 AND temperatura_c <= 45),

    presion_sistolica   SMALLINT CHECK (presion_sistolica  BETWEEN 40 AND 300),
    presion_diastolica  SMALLINT CHECK (presion_diastolica BETWEEN 20 AND 200),

    pulso_lpm           SMALLINT CHECK (pulso_lpm BETWEEN 20 AND 300),
    frecuencia_resp_rpm SMALLINT CHECK (frecuencia_resp_rpm BETWEEN 4 AND 90),
    saturacion_pct      SMALLINT CHECK (saturacion_pct BETWEEN 50 AND 100),

    observaciones       TEXT
);

-- La consulta que manda es "dame las ultimas constantes de este paciente":
-- es la que el medico dispara al abrir el expediente. El indice va por
-- paciente y fecha descendente para que esa lectura no recorra la tabla.
CREATE INDEX idx_signos_vitales_paciente_fecha
    ON public.signos_vitales (paciente_id, tomado_en DESC);

CREATE INDEX idx_signos_vitales_consulta
    ON public.signos_vitales (consulta_id);
