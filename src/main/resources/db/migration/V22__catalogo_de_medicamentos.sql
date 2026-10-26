-- ============================================================================
-- V22 -- Catalogo de medicamentos (HU-23, DRS-92)
--
-- Hasta aqui el medicamento de una receta era texto libre
-- (prescripcion_medicamentos.medicamento). Eso deja escribir "Amoxisilina",
-- "amoxicilina 500" y "Amoxil" para la misma cosa, y hace imposible la
-- pregunta que viene en HU-24: "este paciente es alergico a la penicilina,
-- ¿lo que le estan recetando la contiene?". Para responderla hace falta que
-- cada renglon de la receta apunte a un medicamento conocido y que ese
-- medicamento diga, como dato aparte, cual es su principio activo.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- La clave con la que se comparan dos textos del catalogo
--
-- HU-23 criterio 2: un medicamento con el mismo nombre comercial,
-- presentacion y concentracion que otro se rechaza. "Mismo" tiene que
-- ignorar lo que un administrador teclea distinto sin querer decir otra cosa:
-- mayusculas, tildes y espacios. "Tableta" y "tableta", "Acetaminofén" y
-- "Acetaminofen", "500 mg" y "500mg" son el mismo producto.
--
-- Se quitan TODOS los espacios y no solo los de los extremos porque en la
-- concentracion es donde mas se escriben distinto ("500 mg" / "500mg") y
-- es justamente uno de los tres campos de la clave. En un nombre, quitar los
-- espacios interiores no junta dos productos distintos en la practica.
--
-- Es una funcion y no la expresion repetida porque la usan dos sitios que
-- tienen que coincidir al caracter: el indice unico de abajo y la consulta
-- con la que el servicio busca el duplicado para nombrarlo en el 409
-- (MedicamentoRepository.buscarDuplicado). Si divergieran, el servicio diria
-- "no hay duplicado" y la base lo rechazaria igual, con un mensaje generico.
--
-- IMMUTABLE porque se usa en un indice; lo es de verdad porque sin_tildes ya
-- fija su diccionario (ver V13) y lower/regexp_replace no dependen de nada.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.clave_de_catalogo(texto text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT LOWER(public.sin_tildes(regexp_replace(texto, '\s+', '', 'g')))
$$;

COMMENT ON FUNCTION public.clave_de_catalogo(text) IS
    'Texto sin mayusculas, tildes ni espacios. Clave de unicidad del catalogo '
    'de medicamentos (HU-23 criterio 2).';


-- ----------------------------------------------------------------------------
-- medicamentos
--
-- Los cinco datos son NOT NULL (HU-23 criterio 1) y ademas no pueden ser una
-- cadena en blanco: NOT NULL deja pasar '   ', que para una receta es lo
-- mismo que no decir nada. El DTO ya lo valida; el CHECK esta para las cargas
-- a mano, que tambien tocan esta tabla.
--
-- principio_activo es una columna propia, no parte del nombre, porque es lo
-- que HU-24 va a cruzar contra las alergias del paciente. Un producto con
-- dos principios activos (amoxicilina + acido clavulanico) los lleva en la
-- misma columna separados por " + ": partirlos en una tabla aparte es una
-- decision que le corresponde a HU-24, cuando se sepa como compara.
--
-- `activo` y no un DELETE: un medicamento que ya se receto no puede
-- desaparecer, porque las recetas emitidas lo referencian (criterio 4: "sigue
-- visible en las recetas emitidas antes"). Desactivarlo solo lo saca de la
-- lista de lo que se puede recetar de aqui en adelante.
-- ----------------------------------------------------------------------------
CREATE TABLE public.medicamentos (
    medicamento_id   BIGSERIAL PRIMARY KEY,

    nombre_generico  VARCHAR(80)  NOT NULL,
    nombre_comercial VARCHAR(80)  NOT NULL,
    principio_activo VARCHAR(150) NOT NULL,
    presentacion     VARCHAR(50)  NOT NULL,
    concentracion    VARCHAR(40)  NOT NULL,

    activo           BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT medicamentos_campos_no_vacios CHECK (
        btrim(nombre_generico)  <> '' AND
        btrim(nombre_comercial) <> '' AND
        btrim(principio_activo) <> '' AND
        btrim(presentacion)     <> '' AND
        btrim(concentracion)    <> ''
    )
);

-- La regla del criterio 2, dicha en la base. Cubre tambien a los inactivos a
-- proposito: si se da de alta otra vez un producto que se desactivo, lo
-- correcto es reactivarlo, no tener dos filas del mismo producto -- una con
-- su historial de recetas y otra sin el --.
CREATE UNIQUE INDEX ux_medicamentos_producto
    ON public.medicamentos (
        public.clave_de_catalogo(nombre_comercial),
        public.clave_de_catalogo(presentacion),
        public.clave_de_catalogo(concentracion)
    );

-- La busqueda del autocompletado compara contra estas expresiones (ver
-- MedicamentoRepository.buscar). Con 25 filas no hacen falta; se dejan porque
-- un catalogo real de farmacia pasa de mil, y el autocompletado consulta en
-- cada tecla.
CREATE INDEX ix_medicamentos_generico_sin_tildes
    ON public.medicamentos (LOWER(public.sin_tildes(nombre_generico)));

CREATE INDEX ix_medicamentos_principio_activo_sin_tildes
    ON public.medicamentos (LOWER(public.sin_tildes(principio_activo)));


-- ----------------------------------------------------------------------------
-- El renglon de la receta apunta al catalogo
--
-- medicamento_id es NULABLE porque las recetas emitidas antes de esta
-- migracion no tienen a que apuntar: eran texto libre y no hay forma honesta
-- de adivinar a que fila del catalogo se referia "amoxicilina 500". Se
-- prefiere el hueco a la mentira, igual que con consultas.clinica_id (V6).
-- Que una receta NUEVA lo traiga siempre lo exige PrescripcionService.
--
-- La columna `medicamento` se queda y se sigue llenando: es la foto del
-- nombre en el momento de recetar. Si mañana se corrige el catalogo, la receta
-- que ya se entrego tiene que seguir diciendo lo que decia el papel.
--
-- Sin ON DELETE: un medicamento del catalogo no se borra (ver `activo`), y si
-- alguien lo intentara a mano, que la base se lo impida mientras haya recetas
-- que lo usen.
-- ----------------------------------------------------------------------------
ALTER TABLE public.prescripcion_medicamentos
    ADD COLUMN medicamento_id BIGINT NULL
        REFERENCES public.medicamentos (medicamento_id);

CREATE INDEX idx_prescripcion_medicamentos_medicamento
    ON public.prescripcion_medicamentos (medicamento_id);

-- La foto del nombre ahora junta generico, concentracion, comercial y
-- presentacion ("Amoxicilina 500 mg (Amoxil), capsula"), que con los topes de
-- arriba puede pasar de los 160 de V6. Ampliar un VARCHAR no reescribe la
-- tabla ni toca las filas existentes.
ALTER TABLE public.prescripcion_medicamentos
    ALTER COLUMN medicamento TYPE VARCHAR(300);


-- ----------------------------------------------------------------------------
-- Catalogo inicial
--
-- Medicamentos de uso comun en la consulta general en El Salvador, con
-- marcas que se encuentran en farmacias del pais. Es lo minimo para que la
-- demo pueda emitir recetas sin que el administrador tenga que cargar el
-- catalogo primero; no pretende ser el cuadro basico del MINSAL.
-- ----------------------------------------------------------------------------
INSERT INTO public.medicamentos
    (nombre_generico, nombre_comercial, principio_activo, presentacion, concentracion)
VALUES
    ('Acetaminofén',               'Panadol',      'Paracetamol',                          'Tableta',              '500 mg'),
    ('Acetaminofén',               'Tempra',       'Paracetamol',                          'Jarabe',               '160 mg/5 mL'),
    ('Ibuprofeno',                 'Advil',        'Ibuprofeno',                           'Tableta',              '400 mg'),
    ('Naproxeno',                  'Flanax',       'Naproxeno sódico',                     'Tableta',              '550 mg'),
    ('Diclofenaco',                'Voltarén',     'Diclofenaco sódico',                   'Tableta',              '50 mg'),
    ('Amoxicilina',                'Amoxil',       'Amoxicilina',                          'Cápsula',              '500 mg'),
    ('Amoxicilina',                'Amoxil',       'Amoxicilina',                          'Suspensión oral',      '250 mg/5 mL'),
    ('Amoxicilina + Ác. clavulánico', 'Augmentin', 'Amoxicilina + Ácido clavulánico',      'Tableta',              '875 mg/125 mg'),
    ('Azitromicina',               'Zithromax',    'Azitromicina',                         'Tableta',              '500 mg'),
    ('Ciprofloxacina',             'Ciproxina',    'Ciprofloxacina',                       'Tableta',              '500 mg'),
    ('Trimetoprim + Sulfametoxazol', 'Bactrim',    'Trimetoprim + Sulfametoxazol',         'Tableta',              '160 mg/800 mg'),
    ('Metronidazol',               'Flagyl',       'Metronidazol',                         'Tableta',              '500 mg'),
    ('Loratadina',                 'Clarityne',    'Loratadina',                           'Tableta',              '10 mg'),
    ('Cetirizina',                 'Zyrtec',       'Cetirizina',                           'Tableta',              '10 mg'),
    ('Omeprazol',                  'Losec',        'Omeprazol',                            'Cápsula',              '20 mg'),
    ('Ranitidina',                 'Zantac',       'Ranitidina',                           'Tableta',              '150 mg'),
    ('Metformina',                 'Glucophage',   'Metformina clorhidrato',               'Tableta',              '850 mg'),
    ('Glibenclamida',              'Daonil',       'Glibenclamida',                        'Tableta',              '5 mg'),
    ('Losartán',                   'Cozaar',       'Losartán potásico',                    'Tableta',              '50 mg'),
    ('Enalapril',                  'Renitec',      'Enalapril maleato',                    'Tableta',              '10 mg'),
    ('Amlodipino',                 'Norvasc',      'Amlodipino',                           'Tableta',              '5 mg'),
    ('Atorvastatina',              'Lipitor',      'Atorvastatina cálcica',                'Tableta',              '20 mg'),
    ('Salbutamol',                 'Ventolin',     'Salbutamol',                           'Inhalador',            '100 mcg/dosis'),
    ('Prednisona',                 'Meticorten',   'Prednisona',                           'Tableta',              '5 mg'),
    ('Sales de rehidratación oral', 'Suero Oral',  'Glucosa + Cloruro de sodio + Cloruro de potasio + Citrato de sodio', 'Sobre en polvo', '20.5 g');
