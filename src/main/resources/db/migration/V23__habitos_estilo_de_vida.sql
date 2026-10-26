
-- Tabla de tipos de hábitos
CREATE TABLE tipos_habito (
    id INTEGER PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL
);

-- Tabla de hábitos de estilo de vida
CREATE TABLE habitos_estilo_de_vida (
    id BIGINT PRIMARY KEY,
    descripcion VARCHAR(255) NOT NULL,
    nivel VARCHAR(10) NOT NULL,
    paciente_id BIGINT NOT NULL,
    tipo_habito_id INTEGER NOT NULL,

    CONSTRAINT fk_habito_paciente
        FOREIGN KEY (paciente_id)
        REFERENCES pacientes (persona_id),

    CONSTRAINT fk_habito_tipo
        FOREIGN KEY (tipo_habito_id)
        REFERENCES tipos_habito (id),

    CONSTRAINT chk_habito_nivel
        CHECK (nivel IN ('BAJO', 'MODERADO', 'ALTO'))
);

-- Secuencias compatibles con GenerationType.SEQUENCE
CREATE SEQUENCE tipos_habito_seq
    START WITH 1
    INCREMENT BY 50;

CREATE SEQUENCE habitos_estilo_de_vida_seq
    START WITH 1
    INCREMENT BY 50;

-- Índices para las relaciones
CREATE INDEX idx_habito_paciente
    ON habitos_estilo_de_vida (paciente_id);

CREATE INDEX idx_habito_tipo
    ON habitos_estilo_de_vida (tipo_habito_id);
