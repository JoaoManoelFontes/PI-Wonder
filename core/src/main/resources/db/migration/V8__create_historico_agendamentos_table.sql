CREATE TABLE historico_agendamentos (
    id BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL,
    usuario_keycloak_id UUID NOT NULL,
    status_anterior VARCHAR(20) NOT NULL,
    status_novo VARCHAR(20) NOT NULL,
    alterado_em TIMESTAMP NOT NULL,
    motivo VARCHAR(1000),

    CONSTRAINT fk_historico_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamentos(id),
    CONSTRAINT chk_historico_status_diferentes CHECK (status_anterior <> status_novo),
    CONSTRAINT chk_historico_cancelamento_motivo CHECK (
        status_novo <> 'CANCELADO' OR (motivo IS NOT NULL AND LENGTH(TRIM(motivo)) > 0)
    )
);

CREATE INDEX idx_historico_agendamentos_agendamento ON historico_agendamentos (agendamento_id, id);
