-- ----------------------------------------------------------------------------
-- El peso y la talla se acotan a lo que un ser humano puede medir
--
-- HU-17 (DRS-88), criterio 3: "dado un peso fuera del rango de 0.5 a 400 kg o
-- una talla fuera de 0.3 a 2.5 m, cuando intento guardar, entonces el sistema
-- lo rechaza con un mensaje claro".
--
-- V9 ya acotaba, pero mas flojo: peso > 0 y <= 500, estatura > 0 y <= 300 cm.
-- Con esos limites, 0.05 kg y 2.9 m pasaban sin protestar. No son valores que
-- alguien teclee a proposito: son la coma corrida de sitio -- 7.25 en vez de
-- 72.5 -- y ese error, si entra, arrastra despues un IMC absurdo y una
-- clasificacion nutricional falsa en el expediente.
--
-- Los limites del criterio son deliberadamente amplios en el extremo bajo: 0.5
-- kg y 0.3 m parecen imposibles hasta que se recuerda que un prematuro extremo
-- pesa menos de un kilo y mide alrededor de 30 cm. Un rango "razonable para un
-- adulto" dejaria fuera a los pacientes mas fragiles del sistema.
--
-- La estatura se sigue guardando en CENTIMETROS, como desde V9; el criterio la
-- enuncia en metros y son los mismos limites: 0.3 m = 30 cm, 2.5 m = 250 cm.
-- Cambiar la unidad de la columna obligaria a convertir los datos existentes y
-- a tocar el frontend, sin ganar nada -- quien captura escribe centimetros.
-- ----------------------------------------------------------------------------

-- Se comprueba que nada de lo guardado quede fuera antes de apretar el CHECK.
-- Si hubiera alguna fila fuera de rango, la migracion falla aqui y deja claro
-- que hay un dato que revisar a mano -- que es lo correcto: decidir si 0.05 kg
-- era un recien nacido o una coma mal puesta no es algo que pueda hacer una
-- migracion.
ALTER TABLE public.signos_vitales DROP CONSTRAINT IF EXISTS signos_vitales_peso_kg_check;
ALTER TABLE public.signos_vitales DROP CONSTRAINT IF EXISTS signos_vitales_estatura_cm_check;

ALTER TABLE public.signos_vitales
    ADD CONSTRAINT signos_vitales_peso_kg_check
        CHECK (peso_kg IS NULL OR (peso_kg >= 0.5 AND peso_kg <= 400));

ALTER TABLE public.signos_vitales
    ADD CONSTRAINT signos_vitales_estatura_cm_check
        CHECK (estatura_cm IS NULL OR (estatura_cm >= 30 AND estatura_cm <= 250));

COMMENT ON CONSTRAINT signos_vitales_peso_kg_check ON public.signos_vitales IS
    'HU-17 criterio 3. El limite inferior de 0.5 kg no es un descuido: un '
    'prematuro extremo pesa menos de un kilo.';
COMMENT ON CONSTRAINT signos_vitales_estatura_cm_check ON public.signos_vitales IS
    'HU-17 criterio 3, expresado en centimetros: 0.3 m = 30 cm, 2.5 m = 250 cm.';
