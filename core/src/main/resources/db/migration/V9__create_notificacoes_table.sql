CREATE TABLE notificacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_keycloak_id UUID NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    mensagem TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    chave_evento VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notificacoes_status CHECK (status IN ('PENDENTE', 'LIDA')),
    CONSTRAINT uk_notificacoes_evento_usuario_tipo
        UNIQUE (chave_evento, usuario_keycloak_id, tipo)
);

CREATE INDEX idx_notificacoes_usuario_status_criado
    ON notificacoes (usuario_keycloak_id, status, criado_em DESC);
