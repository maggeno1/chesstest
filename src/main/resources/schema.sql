-- src/main/resources/schema.sql
CREATE TABLE IF NOT EXISTS teilnehmer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    verein VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    altersklasse VARCHAR(50) NOT NULL,
    startgebuehr_bezahlt BOOLEAN DEFAULT FALSE,
    startgebuehr_muss_nicht_zahlen BOOLEAN DEFAULT FALSE,
    anwesenheits_status VARCHAR(50) DEFAULT 'nicht anwesend',
    kommentar TEXT
);

CREATE TABLE IF NOT EXISTS registration_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    is_used INTEGER NOT NULL
);

-- Optional: Index für schnelle Token-Suche
CREATE UNIQUE INDEX IF NOT EXISTS idx_token_token ON registration_tokens (token);