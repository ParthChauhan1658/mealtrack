CREATE TABLE centre (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(120) NOT NULL,
    type           VARCHAR(20)  NOT NULL CHECK (type IN ('SCHOOL', 'ANGANWADI')),
    district       VARCHAR(60)  NOT NULL,
    block          VARCHAR(60)  NOT NULL,
    expected_meals INT          NOT NULL CHECK (expected_meals > 0),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_centre_district ON centre (district);

CREATE TABLE meal_report (
    id           BIGSERIAL PRIMARY KEY,
    centre_id    BIGINT       NOT NULL REFERENCES centre (id),
    report_date  DATE         NOT NULL,
    meals_served INT          NOT NULL CHECK (meals_served >= 0),
    source       VARCHAR(20)  NOT NULL CHECK (source IN ('API', 'MESSAGE', 'SEED')),
    raw_message  TEXT,
    parse_method VARCHAR(20)  NOT NULL DEFAULT 'NONE' CHECK (parse_method IN ('LLM', 'RULES', 'NONE')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_centre_date UNIQUE (centre_id, report_date)
);
CREATE INDEX idx_report_date ON meal_report (report_date);

CREATE TABLE alert (
    id          BIGSERIAL PRIMARY KEY,
    centre_id   BIGINT       NOT NULL REFERENCES centre (id),
    report_date DATE         NOT NULL,
    type        VARCHAR(30)  NOT NULL CHECK (type IN
                ('MISSING_REPORT', 'SUDDEN_DROP', 'SPIKE', 'EXCEEDS_CAPACITY', 'ZERO_MEALS', 'PARSE_UNCERTAIN')),
    severity    VARCHAR(10)  NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    message     TEXT         NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT uq_alert_centre_date_type UNIQUE (centre_id, report_date, type)
);
CREATE INDEX idx_alert_status ON alert (status);
