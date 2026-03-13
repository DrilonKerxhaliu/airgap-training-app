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
-- UPSTREAM PACKAGE TABLE
-- ================================
create table if not exists airgap.t_airgap_upload_package
(
    id                    varchar(36) primary key,
    package_name          varchar(255) not null,
    sequence_index        bigint       not null,
    original_tar_path     varchar(1024) not null,
    archived_tar_path     varchar(1024),
    work_dir_path         varchar(1024),
    outer_dir_path        varchar(1024),
    data_dir_path         varchar(1024),
    manifest_relative_path varchar(512),
    manifest_md5_data_tar varchar(128),
    status                varchar(32)  not null,
    note                  varchar(2000),
    file_count            integer,
    total_size_bytes      bigint,
    uploaded_by           varchar(64),
    imported_at           timestamp,
    archived_at           timestamp,
    created_at            timestamp not null,
    removed_at            timestamp
    );

create unique index if not exists idx_upload_pkg_name
    on airgap.t_airgap_upload_package(package_name);

create unique index if not exists idx_upload_pkg_seq
    on airgap.t_airgap_upload_package(sequence_index);

create index if not exists idx_upload_pkg_status
    on airgap.t_airgap_upload_package(status);

-- ================================
-- UPSTREAM FILE TABLE
-- ================================
create table if not exists airgap.t_airgap_upload_file
(
    id                    varchar(36) primary key,
    upload_package_id     varchar(36) not null,
    relative_path         varchar(1024) not null,
    extracted_absolute_path varchar(1024),
    delivered_absolute_path varchar(1024),
    size_bytes            bigint not null,
    checksum_md5          varchar(128),
    status                varchar(32) not null,
    note                  varchar(2000),
    created_at            timestamp not null,
    updated_at            timestamp not null,
    constraint fk_upload_file_pkg
    foreign key (upload_package_id)
    references airgap.t_airgap_upload_package(id)
    );

create index if not exists idx_upload_file_pkg
    on airgap.t_airgap_upload_file(upload_package_id);

create index if not exists idx_upload_file_status
    on airgap.t_airgap_upload_file(status);

-- ================================
-- UPSTREAM INDEX TABLE
-- ================================

create table if not exists airgap.t_airgap_upload_sequence
(
    id                  bigint primary key,
    last_sequence_index bigint      not null,
    updated_at          timestamp   not null
);

insert into airgap.t_airgap_upload_sequence (id, last_sequence_index, updated_at)
values (1, 0, now())
    on conflict (id) do nothing;

-- ================================
-- DOWNSTREAM / UPSTREAM TRANSACTIONS
-- ================================

CREATE TABLE airgap.t_airgap_transactions
(
    id VARCHAR(36) PRIMARY KEY,

    state VARCHAR(32) NOT NULL,

    direction VARCHAR(32) NOT NULL,

    start_ts TIMESTAMP NOT NULL,
    end_ts TIMESTAMP,

    note TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    initiated_by VARCHAR(64) NOT NULL,

    package_id VARCHAR(36),
    upload_package_id VARCHAR(36),

    package_state VARCHAR(32),
    upload_package_status VARCHAR(32),

    CONSTRAINT fk_tx_package
        FOREIGN KEY (package_id)
            REFERENCES airgap.t_airgap_package(id)
            ON DELETE SET NULL,

    CONSTRAINT fk_tx_upload_package
        FOREIGN KEY (upload_package_id)
            REFERENCES airgap.t_airgap_upload_package(id)
            ON DELETE SET NULL
);

CREATE INDEX idx_tx_state
    ON airgap.t_airgap_transactions(state);

CREATE INDEX idx_tx_package_id
    ON airgap.t_airgap_transactions(package_id);

CREATE INDEX idx_tx_upload_package_id
    ON airgap.t_airgap_transactions(upload_package_id);

CREATE INDEX idx_tx_direction
    ON airgap.t_airgap_transactions(direction);

