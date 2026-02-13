-- ================================
-- SCHEMA
-- ================================

CREATE SCHEMA IF NOT EXISTS airgap;
SET search_path TO airgap;

-- ================================
-- PACKAGE
-- ================================

CREATE TABLE t_airgap_package (
                                  id VARCHAR(36) PRIMARY KEY,
                                  state VARCHAR(32) NOT NULL,  -- was enum
                                  progressive_number BIGINT NOT NULL UNIQUE,
                                  transaction_start_time TIMESTAMP NOT NULL,
                                  transaction_stop_time TIMESTAMP NOT NULL,
                                  exported_at TIMESTAMP,
                                  package_name VARCHAR(255) NOT NULL,
                                  package_path TEXT NOT NULL,
                                  md5_data_tar VARCHAR(64) NOT NULL,
                                  total_size_bytes BIGINT NOT NULL,
                                  notes TEXT,
                                  created_by VARCHAR(64) NOT NULL
);
-- ================================
-- FILE ITEM
-- ================================

CREATE TABLE t_airgap_file_item (
                                    id VARCHAR(36) PRIMARY KEY,
                                    version BIGINT NOT NULL DEFAULT 0,
                                    relative_path TEXT NOT NULL,
                                    size_bytes BIGINT NOT NULL,
                                    received_time TIMESTAMP NOT NULL,
                                    state VARCHAR(32) NOT NULL,  -- was enum
                                    package_id VARCHAR(36),

                                    CONSTRAINT fk_file_package
                                        FOREIGN KEY (package_id)
                                            REFERENCES t_airgap_package(id)
                                            ON DELETE SET NULL
);

-- ================================
-- INDEXES
-- ================================

CREATE INDEX idx_airgap_file_state
    ON t_airgap_file_item(state);

CREATE INDEX idx_airgap_file_received
    ON t_airgap_file_item(received_time);

CREATE UNIQUE INDEX uk_file_relative_path
    ON t_airgap_file_item(relative_path);

CREATE SEQUENCE airgap.package_progressive_seq
    START 1
INCREMENT 1;


CREATE SEQUENCE IF NOT EXISTS airgap.package_progressive_seq START 1;

ALTER TABLE airgap.t_airgap_package
    ALTER COLUMN progressive_number SET DEFAULT nextval('airgap.package_progressive_seq');


-- ================================
-- DOWNSTREAM TRANSACTIONS
-- ================================

CREATE TABLE t_airgap_transactions (
    id VARCHAR(36) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(32) NOT NULL,
    start_ts TIMESTAMP NOT NULL,
    end_ts TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    package_id VARCHAR(36),
    package_state VARCHAR(32),
    initiated_by VARCHAR(64) NOT NULL,

    CONSTRAINT fk_tx_package
        FOREIGN KEY (package_id)
            REFERENCES airgap.t_airgap_package(id)
            ON DELETE SET NULL
);

CREATE INDEX idx_tx_state
    ON airgap.t_airgap_transactions(state);

CREATE INDEX idx_tx_package_id
    ON airgap.t_airgap_transactions(package_id);

CREATE INDEX idx_tx_package_state
    ON airgap.t_airgap_transactions(package_state);

