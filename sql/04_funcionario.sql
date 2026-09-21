CREATE TABLE FUNCIONARIO (
    CPF             VARCHAR(11)   NOT NULL,
    Matricula       VARCHAR(10)   NOT NULL,
    Cargo           VARCHAR(40)   NOT NULL,
    Salario         NUMERIC(10,2) NOT NULL,
    CPF_Supervisor  VARCHAR(11),
    CONSTRAINT pk_funcionario PRIMARY KEY (CPF),
    CONSTRAINT uq_funcionario_matricula UNIQUE (Matricula),
    CONSTRAINT fk_funcionario_pessoa FOREIGN KEY (CPF) REFERENCES PESSOA (CPF),
    -- ON DELETE SET NULL: se o supervisor sai do bar, os supervisionados ficam sem supervisor
    CONSTRAINT fk_funcionario_supervisor FOREIGN KEY (CPF_Supervisor)
        REFERENCES FUNCIONARIO (CPF)
        ON DELETE SET NULL,
    CONSTRAINT ck_funcionario_salario CHECK (Salario > 0)
);

-- Os 3 gerentes (sem supervisor) vem primeiro; os demais sao supervisionados por eles
INSERT INTO FUNCIONARIO (CPF, Matricula, Cargo, Salario, CPF_Supervisor) VALUES
('20000000001', 'M0001', 'Gerente', 6500.00, NULL),
('20000000005', 'M0005', 'Gerente', 6200.00, NULL),
('20000000006', 'M0006', 'Gerente', 6000.00, NULL),
('20000000002', 'M0002', 'Bartender', 2800.00, '20000000001'),
('20000000003', 'M0003', 'Garconete', 2200.00, '20000000001'),
('20000000004', 'M0004', 'Garcom', 2200.00, '20000000001'),
('20000000007', 'M0007', 'Bartender', 2900.00, '20000000001'),
('20000000008', 'M0008', 'Bartender', 2800.00, '20000000005'),
('20000000009', 'M0009', 'Bartender', 3000.00, '20000000006'),
('20000000010', 'M0010', 'Garconete', 2200.00, '20000000001'),
('20000000011', 'M0011', 'Garcom', 2200.00, '20000000001'),
('20000000012', 'M0012', 'Garconete', 2200.00, '20000000005'),
('20000000013', 'M0013', 'Garcom', 2200.00, '20000000005'),
('20000000014', 'M0014', 'Garconete', 2300.00, '20000000006'),
('20000000015', 'M0015', 'Garcom', 2300.00, '20000000006'),
('20000000016', 'M0016', 'Garconete', 2200.00, '20000000001'),
('20000000017', 'M0017', 'Garcom', 2200.00, '20000000005'),
('20000000018', 'M0018', 'Barback', 1800.00, '20000000001'),
('20000000019', 'M0019', 'Garcom', 2200.00, '20000000006'),
('20000000020', 'M0020', 'Cozinheiro', 3300.00, '20000000005'),
('20000000021', 'M0021', 'Cozinheiro', 3200.00, '20000000005'),
('20000000022', 'M0022', 'Auxiliar de Cozinha', 1900.00, '20000000005'),
('20000000023', 'M0023', 'Cozinheiro', 3200.00, '20000000005'),
('20000000024', 'M0024', 'Auxiliar de Cozinha', 1900.00, '20000000005'),
('20000000025', 'M0025', 'Auxiliar de Cozinha', 1900.00, '20000000005'),
('20000000026', 'M0026', 'Caixa', 2300.00, '20000000006'),
('20000000027', 'M0027', 'Seguranca', 2500.00, '20000000006'),
('20000000028', 'M0028', 'Caixa', 2300.00, '20000000006'),
('20000000029', 'M0029', 'Seguranca', 2500.00, '20000000006'),
('20000000030', 'M0030', 'Caixa', 2300.00, '20000000001'),
('20000000031', 'M0031', 'Seguranca', 2500.00, '20000000001'),
('20000000032', 'M0032', 'DJ', 2600.00, '20000000006'),
('20000000033', 'M0033', 'DJ', 2600.00, '20000000006'),
('20000000034', 'M0034', 'Barback', 1800.00, '20000000005'),
('20000000035', 'M0035', 'Barback', 1800.00, '20000000006');
