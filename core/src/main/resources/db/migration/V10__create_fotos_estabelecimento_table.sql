CREATE TABLE fotos_estabelecimento (
    id BIGSERIAL PRIMARY KEY,
    prestador_id BIGINT NOT NULL REFERENCES prestadores(id) ON DELETE CASCADE,
    nome_objeto VARCHAR(500) NOT NULL UNIQUE,
    foto_url VARCHAR(500) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fotos_estabelecimento_prestador ON fotos_estabelecimento (prestador_id, id);
