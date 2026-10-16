INSERT INTO categorias (nome, descricao, status)
VALUES
    ('Cabelo', 'Cortes, penteados, tranças e outros cuidados com o cabelo.', 'ATIVA'),
    ('Barba', 'Corte, desenho e cuidados com a barba.', 'ATIVA'),
    ('Unhas', 'Manicure, pedicure e alongamento de unhas.', 'ATIVA'),
    ('Sobrancelhas', 'Design e cuidados com as sobrancelhas.', 'ATIVA'),
    ('Maquiagem', 'Maquiagem para eventos e outras ocasiões.', 'ATIVA');

INSERT INTO perfis_usuarios (id, keycloak_id, numero_telefone)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '84999990000'
);

INSERT INTO prestadores (
    perfil_usuario_id,
    nome_estab,
    documento,
    endereco,
    numero,
    bairro,
    cidade,
    estado,
    complemento,
    status,
    enviado_em,
    aprovado_em,
    aprovado_por,
    ativo
)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'Studio Wonder',
    '00000000000191',
    'Rua das Flores',
    '100',
    'Centro',
    'Natal',
    'RN',
    'Sala 1',
    'ATIVO',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'migration-v5',
    TRUE
);

INSERT INTO servicos (prestador_id, categoria_id, nome, preco, duracao_min, status)
VALUES
    (
        (SELECT id FROM prestadores WHERE perfil_usuario_id = '10000000-0000-0000-0000-000000000001'),
        (SELECT id FROM categorias WHERE nome = 'Cabelo'),
        'Corte de cabelo',
        50.00,
        45,
        'ATIVO'
    ),
    (
        (SELECT id FROM prestadores WHERE perfil_usuario_id = '10000000-0000-0000-0000-000000000001'),
        (SELECT id FROM categorias WHERE nome = 'Sobrancelhas'),
        'Design de sobrancelhas',
        40.00,
        30,
        'ATIVO'
    ),
    (
        (SELECT id FROM prestadores WHERE perfil_usuario_id = '10000000-0000-0000-0000-000000000001'),
        (SELECT id FROM categorias WHERE nome = 'Unhas'),
        'Manicure',
        35.00,
        60,
        'ATIVO'
    );

INSERT INTO horarios_atendimento (prestador_id, dia_semana, hora_inicio, hora_fim)
SELECT prestador.id, horario.dia_semana, horario.hora_inicio, horario.hora_fim
FROM prestadores prestador
CROSS JOIN (
    VALUES
        (1, TIME '09:00', TIME '18:00'),
        (2, TIME '09:00', TIME '18:00'),
        (3, TIME '09:00', TIME '18:00'),
        (4, TIME '09:00', TIME '18:00'),
        (5, TIME '09:00', TIME '18:00'),
        (6, TIME '09:00', TIME '13:00')
) AS horario(dia_semana, hora_inicio, hora_fim)
WHERE prestador.perfil_usuario_id = '10000000-0000-0000-0000-000000000001';
