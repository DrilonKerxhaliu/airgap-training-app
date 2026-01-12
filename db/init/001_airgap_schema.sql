-- ================================
-- SCHEMA
-- ================================

CREATE SCHEMA IF NOT EXISTS airgap;
SET search_path TO airgap;

-- ================================
-- ENUMS
-- ================================

CREATE TYPE file_item_state AS ENUM (
    'INSERT',
    'NEW',
    'ACTIVE',
    'PACKED',
    'EXTRACTED',
    'ROUTED',
    'REJECTED'
);

CREATE TYPE package_state AS ENUM (
    'NEW',
    'CREATED',
    'EXPORTED',
    'IMPORTED',
    'INJECTED',
    'FAILED',
    'REJECTED',
    'DELETED'
);

-- ================================
-- PACKAGE
-- ================================

CREATE TABLE t_airgap_package (
                                  id VARCHAR(36) PRIMARY KEY,
                                  version BIGINT NOT NULL,
                                  state package_state NOT NULL,
                                  progressive_number BIGINT NOT NULL UNIQUE,
                                  transaction_start_time TIMESTAMP NOT NULL,
                                  transaction_stop_time TIMESTAMP NOT NULL,
                                  package_name VARCHAR(255) NOT NULL,
                                  package_path TEXT NOT NULL,
                                  md5_data_tar VARCHAR(64) NOT NULL,
                                  total_size_bytes BIGINT NOT NULL,
                                  notes TEXT
);

-- ================================
-- FILE ITEM
-- ================================

CREATE TABLE t_airgap_file_item (
                                    id VARCHAR(36) PRIMARY KEY,
                                    version BIGINT NOT NULL,
                                    relative_path TEXT NOT NULL,
                                    size_bytes BIGINT NOT NULL,
                                    received_time TIMESTAMP NOT NULL,
                                    state file_item_state NOT NULL,
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
