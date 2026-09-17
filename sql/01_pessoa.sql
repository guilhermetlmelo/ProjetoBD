CREATE TABLE PESSOA (
    CPF VARCHAR(11) NOT NULL,
    Nome VARCHAR(80) NOT NULL,
    Rua VARCHAR(80),
    Numero VARCHAR(10),
    Bairro VARCHAR(50),
    CEP VARCHAR(9),
    CONSTRAINT pk_pessoa PRIMARY KEY (CPF)
);

INSERT INTO PESSOA (CPF, Nome, Rua, Numero, Bairro, CEP) VALUES
('10000000001', 'Ana Beatriz Ramos',    'Rua da Aurora',         '210',  'Boa Vista',   '50050-000'),
('10000000002', 'Carlos Eduardo Lima',  'Av. Conde da Boa Vista','815',  'Boa Vista',   '50060-004'),
('10000000003', 'Debora Nunes Alves',   'Rua do Bom Jesus',      '45',   'Recife',      '50030-170'),
('10000000004', 'Eduardo Farias Melo',  'Av. Boa Viagem',        '1200', 'Boa Viagem',  '51011-000'),
('10000000005', 'Fernanda Souza Rocha', 'Rua Padre Carapuceiro', '733',  'Boa Viagem',  '51020-280'),
('10000000006', 'Gabriel Torres Pinto', 'Rua Sete de Setembro',  '98',   'Santo Amaro', '50100-020'),
('20000000001', 'Helena Martins Sa',    'Rua do Hospicio',       '320',  'Boa Vista',   '50050-050'),
('20000000002', 'Igor Cavalcanti Lins', 'Rua Real da Torre',     '577',  'Torre',       '50710-000'),
('20000000003', 'Juliana Barros Neves', 'Av. Caxanga',           '1044', 'Madalena',    '50720-001'),
('20000000004', 'Lucas Andrade Vieira', 'Rua Benfica',           '160',  'Madalena',    '50720-001');
