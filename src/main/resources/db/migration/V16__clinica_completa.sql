-- ----------------------------------------------------------------------------
-- Una clinica es una direccion a la que alguien tiene que llegar
--
-- HU-26 (DRS-28). La tabla guardaba nombre y coordenadas, y con eso no se puede
-- hacer lo que la historia pide: "incorporarla a la red nacional y que aparezca
-- en el mapa para los pacientes". Un paciente que busca donde atenderse
-- necesita el municipio, la direccion, el telefono y el horario; un punto en un
-- mapa sin nada de eso no le sirve.
--
-- ── Las coordenadas se acotan a El Salvador ─────────────────────────────────
-- El DTO validaba -90..90 y -180..180, que es "cualquier punto del planeta". La
-- propia historia explica por que no basta:
--
--   "evita el error clasico de invertir latitud y longitud, que coloca la
--    clinica en medio del oceano Indico sin que nadie lo note hasta la
--    demostracion"
--
-- Con los rangos invertidos -latitud 13.7, longitud -89.2 puestos al reves-
-- sale (-89.2, 13.7): una latitud imposible. Acotar a 13.0..14.5 y
-- -90.2..-87.6 convierte ese error en un rechazo inmediato en vez de un punto
-- silenciosamente equivocado.
--
-- El CHECK va en la base y no solo en el DTO a proposito: la validacion del DTO
-- protege a quien entra por la API, el CHECK protege tambien a quien entra por
-- una migracion, un script de carga o psql.
-- ----------------------------------------------------------------------------

ALTER TABLE public.clinicas
    ADD COLUMN departamento VARCHAR(40),
    ADD COLUMN municipio    VARCHAR(60),
    ADD COLUMN direccion    VARCHAR(200),
    ADD COLUMN telefono     VARCHAR(20),
    ADD COLUMN horario      VARCHAR(120),
    ADD COLUMN estado       VARCHAR(10) NOT NULL DEFAULT 'ACTIVA';

-- Las columnas nuevas nacen NULL porque ya hay clinicas registradas y no se
-- puede inventar su direccion. Quedan obligatorias en el DTO de alta -- de ahi
-- en adelante nadie registra una sin ellas -- y las tres existentes se
-- completan a mano. Ponerlas NOT NULL aqui obligaria a rellenarlas con algo
-- falso para que la migracion pasara.

ALTER TABLE public.clinicas
    ADD CONSTRAINT clinicas_estado_check
        CHECK (estado IN ('ACTIVA', 'INACTIVA'));

-- Rango del territorio salvadoreno. Se admite NULL: una clinica se da de alta
-- con su nombre y su direccion mucho antes de que alguien salga a tomarle el
-- GPS, y exigir la coordenada de entrada solo conseguiria que alguien escriba
-- una cualquiera.
ALTER TABLE public.clinicas
    ADD CONSTRAINT clinicas_latitud_el_salvador
        CHECK (latitud IS NULL OR latitud BETWEEN 13.0 AND 14.5),
    ADD CONSTRAINT clinicas_longitud_el_salvador
        CHECK (longitud IS NULL OR longitud BETWEEN -90.2 AND -87.6);

-- ── Dos clinicas con el mismo nombre en el mismo municipio son la misma ─────
-- El indice es sobre los valores normalizados -- sin tildes y en minusculas --
-- porque "Clinica San Jose" y "Clínica San José" son el mismo sitio escrito por
-- dos personas distintas, y un UNIQUE literal las dejaria convivir. Reutiliza
-- sin_tildes() de V13.
--
-- Solo aplica cuando hay municipio: mientras las clinicas existentes no lo
-- tengan, no se puede decidir si chocan, y bloquearlas seria inventarse un
-- conflicto.
CREATE UNIQUE INDEX ux_clinicas_nombre_municipio
    ON public.clinicas (LOWER(public.sin_tildes(name)), LOWER(public.sin_tildes(municipio)))
    WHERE municipio IS NOT NULL;

CREATE INDEX ix_clinicas_estado ON public.clinicas (estado);

COMMENT ON COLUMN public.clinicas.estado IS
    'ACTIVA o INACTIVA. Una clinica inactiva deja de ofrecerse para atender, '
    'pero sus consultas y su personal asignado siguen existiendo.';
COMMENT ON CONSTRAINT clinicas_latitud_el_salvador ON public.clinicas IS
    'El Salvador va de 13.0 a 14.5 de latitud. Fuera de ahi casi siempre '
    'significa que se invirtieron latitud y longitud al capturarlas.';
