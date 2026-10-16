INSERT INTO perfis_usuarios (id, keycloak_id, numero_telefone)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000002',
    '84999990001'
);

INSERT INTO agendamentos (cliente_id, prestador_id, servico_id, inicio, fim, status)
SELECT
    '10000000-0000-0000-0000-000000000002',
    prestador.id,
    servico.id,
    periodo.inicio,
    periodo.fim,
    periodo.status
FROM prestadores prestador
JOIN servicos servico
    ON servico.prestador_id = prestador.id
   AND servico.nome = 'Corte de cabelo'
CROSS JOIN (
    VALUES
        (TIMESTAMP '2030-01-07 10:00:00', TIMESTAMP '2030-01-07 10:45:00', 'CONFIRMADO'),
        (TIMESTAMP '2030-01-07 11:00:00', TIMESTAMP '2030-01-07 11:45:00', 'CANCELADO')
) AS periodo(inicio, fim, status)
WHERE prestador.perfil_usuario_id = '10000000-0000-0000-0000-000000000001';
