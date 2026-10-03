CREATE TABLE servicos (
    id BIGSERIAL PRIMARY KEY,
    prestador_id BIGINT NOT NULL,
    categoria_id BIGINT,
    nome VARCHAR(150) NOT NULL,
    preco DECIMAL(10,2) NOT NULL,
    duracao_min INTEGER NOT NULL,
    foto_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT fk_servicos_prestador FOREIGN KEY (prestador_id) REFERENCES prestadores(id),
    CONSTRAINT fk_servicos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT chk_servicos_duracao_min CHECK (duracao_min > 0),
    CONSTRAINT chk_servicos_preco CHECK (preco >= 0)
);

CREATE TABLE horarios_atendimento (
    id BIGSERIAL PRIMARY KEY,
    prestador_id BIGINT NOT NULL,
    dia_semana INTEGER NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    CONSTRAINT fk_horarios_atendimento_prestador FOREIGN KEY (prestador_id) REFERENCES prestadores(id),
    CONSTRAINT chk_horarios_atendimento_dia_semana CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT chk_horarios_atendimento_periodo CHECK (hora_inicio < hora_fim)
);
