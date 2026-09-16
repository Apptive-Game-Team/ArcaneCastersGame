CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100),
    name VARCHAR(50) NOT NULL,
    password_hash CHAR(60) NOT NULL,
    selected_deck_id BIGINT,
    mmr SMALLINT NOT NULL DEFAULT 1000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(255) NOT NULL DEFAULT 'Online',
    novice_progress REAL NOT NULL DEFAULT 0.5
);

-- A card is a magic now: magics is the card catalogue, and cards / user_cards / magic_cards
-- are gone. element replaces the card combination as where a magic's element comes from.
CREATE TABLE magics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    element VARCHAR(10) NOT NULL DEFAULT 'None'
        CHECK (element IN ('Fire', 'Water', 'Lightning', 'Rock', 'Nature', 'Wind', 'None')),
    access_type VARCHAR(10) NOT NULL DEFAULT 'DEFAULT'
);

CREATE TABLE user_magics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    magic_id BIGINT NOT NULL REFERENCES magics(id),
    count INT NOT NULL DEFAULT 3,
    UNIQUE (user_id, magic_id)
);

CREATE TABLE decks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(31),
    user_id BIGINT NOT NULL REFERENCES users(id)
);

ALTER TABLE users
ADD FOREIGN KEY (selected_deck_id) REFERENCES decks(id);

-- deck_cards keeps its name and points at magics instead of cards.
CREATE TABLE deck_cards (
    id BIGSERIAL PRIMARY KEY,
    deck_id BIGINT NOT NULL REFERENCES decks(id),
    magic_id BIGINT NOT NULL REFERENCES magics(id),
    count INT NOT NULL DEFAULT 1,
    UNIQUE (magic_id, deck_id)
);

ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

CREATE TABLE game_objects (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(31) UNIQUE
);

CREATE TABLE parameters(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(31) UNIQUE
);

CREATE TABLE parameter_values(
    id BIGSERIAL PRIMARY KEY,
    parameter_id BIGINT,
    game_object_id BIGINT,
    value DOUBLE PRECISION,
    CONSTRAINT fk_game_object
        FOREIGN KEY (game_object_id)
        REFERENCES game_objects(id),
    CONSTRAINT fk_parameter
        FOREIGN KEY (parameter_id)
        REFERENCES parameters(id),
    CONSTRAINT uq_parameter_game_object
        UNIQUE (parameter_id, game_object_id)
);

ALTER TABLE user_magics
    DROP CONSTRAINT user_magics_user_id_fkey,
    ADD CONSTRAINT user_magics_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE decks
    DROP CONSTRAINT decks_user_id_fkey,
    ADD CONSTRAINT decks_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE deck_cards
    DROP CONSTRAINT deck_cards_deck_id_fkey,
    ADD CONSTRAINT deck_cards_deck_id_fkey
        FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE;

-- Statistic tables
CREATE TABLE statistic_games (
    id BIGSERIAL PRIMARY KEY,
    outcome VARCHAR(16) NOT NULL DEFAULT 'WIN' CHECK (outcome IN ('WIN', 'DRAW', 'ABANDONED')),
    win_user_id BIGINT,
    loss_user_id BIGINT,
    duration BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    game_type VARCHAR(255) NOT NULL DEFAULT 'PVP',
    server_version VARCHAR(64) NOT NULL,
    event_schema_version INT NOT NULL CHECK (event_schema_version > 0)
);

CREATE TABLE statistic_game_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    left_user_id BIGINT NOT NULL,
    right_user_id BIGINT NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    server_domain VARCHAR(255) NOT NULL,
    server_port INT NOT NULL,
    server_instance_id VARCHAR(64) NOT NULL,
    server_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'DRAW', 'ABANDONED')),
    end_reason VARCHAR(32),
    end_detail TEXT,
    statistic_game_id BIGINT REFERENCES statistic_games(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE statistic_game_decks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    statistic_game_id BIGINT NOT NULL REFERENCES statistic_games(id) ON DELETE CASCADE,
    magic_id BIGINT NOT NULL REFERENCES magics(id),
    count INT
);

CREATE INDEX idx_statistic_game_deck_user_id_statistic_game_id
    ON statistic_game_decks(user_id, statistic_game_id);

CREATE TABLE statistic_game_magics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    statistic_game_id BIGINT REFERENCES statistic_games(id) ON DELETE CASCADE,
    magic_id BIGINT NOT NULL REFERENCES magics(id),
    count INT
);

CREATE INDEX idx_statistic_game_magic_user_id_statistic_game_id
    ON statistic_game_magics(user_id, statistic_game_id);

-- Copied from the production definition, not inferred from the INSERT in
-- StatisticRepository: database/migration/V000_20260406__init_tables.sql line 508, as
-- amended by V066_20260824__widen_statistic_update_time_intervals.sql. V000 declared the
-- interval columns INTEGER, which caps them at ~2.147 seconds; V066 widens them to
-- BIGINT. This file must not run ahead of that migration -- see WordOnlineDatabase#75.
-- name is varchar(31) and mean_interval_ns is double precision in V000; the earlier
-- inferred definition guessed VARCHAR(255) and REAL.
CREATE TABLE statistic_update_time (
    id BIGSERIAL PRIMARY KEY,
    statistic_game_id BIGINT REFERENCES statistic_games(id) ON DELETE CASCADE,
    name VARCHAR(31),
    min_interval_ns BIGINT,
    max_interval_ns BIGINT,
    mean_interval_ns DOUBLE PRECISION
);

CREATE INDEX idx_statistic_update_time_statistic_game_id
    ON statistic_update_time(statistic_game_id);

CREATE TABLE bot_personas (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE CHECK (user_id < 0),
    name VARCHAR(50) NOT NULL,
    tier VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    thinking_time_ms INT NOT NULL DEFAULT 250,
    reaction_interval_frames INT NOT NULL DEFAULT 8,
    counter_aggression DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    hospitality BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(31) UNIQUE NOT NULL
);

CREATE TABLE game_object_tags (
    game_object_id BIGINT REFERENCES game_objects(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES tags(id) ON DELETE CASCADE,
    CONSTRAINT uq_game_object_id_tag_id UNIQUE (game_object_id, tag_id)
);

CREATE TABLE magic_tags (
    magic_id BIGINT NOT NULL REFERENCES magics(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (magic_id, tag_id)
);

CREATE TABLE tag_counter_rules (
    id BIGSERIAL PRIMARY KEY,
    attacker_tag_id BIGINT NOT NULL REFERENCES tags(id),
    target_tag_id BIGINT NOT NULL REFERENCES tags(id),
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    UNIQUE(attacker_tag_id, target_tag_id)
);
