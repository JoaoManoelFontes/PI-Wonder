CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    foto_url VARCHAR(500),

    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_categorias_nome_lower ON categorias (LOWER(nome));
CREATE TABLE perfis_usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id UUID NOT NULL UNIQUE,
    foto_url VARCHAR(500),
    numero_telefone VARCHAR(20),

    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE prestadores (
    id BIGSERIAL PRIMARY KEY,
    perfil_usuario_id UUID NOT NULL UNIQUE,
    nome_estab VARCHAR(255) NOT NULL,
    documento VARCHAR(20),
    endereco VARCHAR(255),
    numero VARCHAR(30),
    bairro VARCHAR(120),
    cidade VARCHAR(120),
    estado VARCHAR(2),
    complemento VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    enviado_em TIMESTAMP,
    aprovado_em TIMESTAMP,
    aprovado_por VARCHAR(50),
    motivo_rejeicao TEXT,
    foto_url VARCHAR(500),

    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_prestadores_perfil_usuario FOREIGN KEY (perfil_usuario_id) REFERENCES perfis_usuarios(id)
);
