
CREATE TABLE PAGAMENTO_PARCIAL (
    ID_Atendimento     INTEGER       NOT NULL,
    Num_Transacao      INTEGER       NOT NULL,
    Valor_Pago         NUMERIC(10,2) NOT NULL,
    Metodo_Pagamento   VARCHAR(10)   NOT NULL,
    Horario_Pagamento  TIMESTAMP     NOT NULL,
    CONSTRAINT pk_pagamento_parcial PRIMARY KEY (ID_Atendimento, Num_Transacao),
    CONSTRAINT fk_pagamento_parcial_atendimento FOREIGN KEY (ID_Atendimento)
        REFERENCES ATENDIMENTO (ID_Atendimento)
        ON DELETE CASCADE,
    CONSTRAINT ck_pagamento_parcial_valor CHECK (Valor_Pago > 0),
    CONSTRAINT ck_pagamento_parcial_metodo CHECK (Metodo_Pagamento IN ('PIX', 'CREDITO', 'DEBITO', 'DINHEIRO'))
);

INSERT INTO PAGAMENTO_PARCIAL (ID_Atendimento, Num_Transacao, Valor_Pago, Metodo_Pagamento, Horario_Pagamento) VALUES
(1,  1,  78.00, 'PIX',      '2026-09-04 21:10:00'),
(2,  1,  80.00, 'CREDITO',  '2026-09-04 22:30:00'),
(2,  2,  80.00, 'DEBITO',   '2026-09-04 22:31:00'),
(2,  3,  76.00, 'PIX',      '2026-09-04 22:32:00'),
(3,  1,  62.00, 'DINHEIRO', '2026-09-05 20:05:00'),
(4,  1,  58.00, 'CREDITO',  '2026-09-05 23:10:00'),
(4,  2,  58.00, 'PIX',      '2026-09-05 23:11:00'),
(5,  1,  40.00, 'PIX',      '2026-09-11 21:00:00'),
(5,  2,  40.00, 'PIX',      '2026-09-11 21:01:00'),
(6,  1,  88.00, 'DEBITO',   '2026-09-11 22:15:00'),
(7,  1,  50.00, 'CREDITO',  '2026-09-12 21:20:00'),
(7,  2,  40.00, 'DINHEIRO', '2026-09-12 21:21:00'),
(8,  1,  36.00, 'PIX',      '2026-09-12 23:30:00'),
(9,  1, 106.00, 'CREDITO',  '2026-09-18 22:00:00'),
(10, 1,  53.00, 'DEBITO',   '2026-09-18 22:40:00'),
(11, 1,  60.00, 'PIX',      '2026-09-19 20:30:00');






