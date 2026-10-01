
AIRGAP SERVICE – COMPLETE MERGED TECHNICAL & FUNCTIONAL SPECIFICATION

1. PURPOSE OF THE APPLICATION
   Airgap Service (AGS) is a backend service that enables secure exchange of files/messages
   between ONLINE and OFFLINE environments in air‑gap scenarios (no direct network connection).
   It ensures controlled transfer using packaging, encryption, checksums, audit, schedulers
   and REST APIs.

2. OVERALL ARCHITECTURE
   Main nodes involved:
- NODO RM‑D005 (file source / collector)
- NODO D005‑ON‑LINE
- NODO D005‑OFF‑LINE

Files arrive from RM‑D005 via FTP + rsync and are placed on local filesystem.

3. BIDIRECTIONAL FLOW ONLINE ↔ OFFLINE
   Airgap Service is fully symmetric.

Directions:
UPSTREAM  (ONLINE → OFFLINE)
Node: D005‑ON‑LINE
IN:  /COLLECTED_DATA/IN
OUT: /COLLECTED_DATA/OUT

DOWNSTREAM (OFFLINE → ONLINE)
Node: D005‑OFF‑LINE
IN:  /COLLECTED_DATA/IN
OUT: /COLLECTED_DATA/OUT

Both systems can send and receive files using the same logic and codebase,
differing only by configuration.

4. TECHNOLOGY STACK (MANDATORY)
- Java 17
- Spring Boot 2.7.18 (Java 11 compatible)
- Spring Data JPA + Hibernate (NO JdbcTemplate)
- Maven 3.8.8 (Java 11 compatible)
- PostgreSQL 15
- Quartz Scheduler
- Docker / Docker Compose
- Google GSON for JSON
- AES‑256 encryption
- MD5 checksum

5. FILE SYSTEM & CONFIGURATION
   All paths and parameters are defined in application.properties and injected via @Value.

Example: // can be changed I made it just like example:

airgap.collect.in=/COLLECTED_DATA/IN
airgap.collect.out=/COLLECTED_DATA/OUT
airgap.auto.hours=2
airgap.auto.max-gb=10
airgap.cleanup.days=10

Hardcoded paths are not allowed.

6. FILE DISCOVERY & LIFECYCLE
   A watchdog/poller monitors /COLLECTED_DATA/IN.
   Files are considered valid when size is stable.
   Files are persisted in DB.

File states:
- NEW
- ACTIVE
- PACKED

7. PACKAGE LOGIC

7.1 PACKAGE NAME (MANDATORY)
PKG_<TransactionStartTime>_<TransactionStopTime>_<ProgressiveNumber>.tar

7.2 PACKAGE STRUCTURE
PKG_XXXX.tar
├─ manifest.enc
└─ PKG_XXXX-data.tar

7.3 MANIFEST
- JSON format (GSON)
- Encrypted using AES‑256
- Contains:
    - list of files
    - metadata
    - MD5 checksum of data.tar

8. AUTO VS LATEST PACKAGING

8.1 LATEST (MANUAL)
Triggered only by REST API call.
Creates on‑demand package with transactional snapshot and DB locking.

Endpoint:
GET /airgap/{direction}/package/latest

8.2 AUTO (AUTOMATIC)
Executed by Quartz Scheduler.
Triggers:
- every 1 hours
- or when folder reaches 100 MB
  Runs on both ONLINE and OFFLINE.

AUTO and LATEST are completely independent.

9. SCHEDULERS
- File Watcher Scheduler
- Auto Packaging Scheduler (1h / 100 MB)
- Cleanup Scheduler (every 10 days)
- Offline Import Watcher

10. RETENTION / CLEANUP
    Cleanup scheduler runs every 10 days.
    Deletes packages from disk and updates DB status to DELETED.

11. REST API COMMUNICATION – JSON ONLY
    All communication between REST API and UI is JSON only.

Mandatory headers:
Content-Type: application/json
Accept: application/json

Binary, HTML or XML responses are not used.

11.1 STANDARD RESPONSE FORMAT
{
"timestamp": "...",
"status": "SUCCESS | ERROR",
"direction": "UPSTREAM | DOWNSTREAM",
"operation": "LATEST | AUTO | UPLOAD | DOWNLOAD",
"data": {},
"errors": []
}

12. DOWNLOAD & UPLOAD

Download:
UI receives JSON metadata only.
Backend handles copy/export.

Upload:
JSON based.
TAR content sent as BASE64.

13. DATABASE MODEL (JPA)
    Main entities:
- Package
- FileItem
- ImportTransaction
- UserActionAudit
- ConfigParam [ToBe re-checked]

Concurrency:
- @Version optimistic locking
- PESSIMISTIC_WRITE for LATEST operations

14. DEPENDENCY INJECTION & CODING RULES
- Use Spring annotations (@Autowired, @Service, @Repository)
- No manual instantiation
- Code must be simple, readable and commented

15. AUDIT & TRACKING
    Implemented using Spring AOP.
    Intercepts all REST API calls.
    Stored data:
- user
- endpoint
- HTTP method
- direction
- outcome
- timestamp

Payloads are not stored or we can chose to stare it into a json format.

16. OFFLINE IMPORT & INTEGRITY
    Steps:
- parity check (sequence + timestamp)
- decrypt manifest
- verify MD5
- unzip package
- route files to /COLLECTED_DATA/OUT

Statuses:
- IMPORTED
- REJECTED
- FAILED

17. SECURITY
- AES‑256 encryption
- MD5 integrity check
- input validation
- no sensitive payload logging
- minimal privileges

18. Use folder-watcher like first option, add and the PUT upload like a second option that can be used in the feature.

19. FINAL STATUS
    This document represents my view based in the email or on the document provided for the creation of the app [Service Airgap].


-------------------------------------

# Airgap Service

Backend service for controlled file exchange between offline/online zones. It builds encrypted package archives, imports uploaded packages, tracks transactions in PostgreSQL, and exposes JSON APIs plus SSE streams.

## Purpose and app flow (functional explanation)

This app manages **bidirectional transfer** in air-gap scenarios:

- **Downstream**: collect files from local IN folder, build encrypted `.tar` package, and deliver/send package to configured destination (`file://`, `ftp://`, `sftp://`).
- **Upstream**: receive uploaded `.tar` package, validate integrity/sequence, extract and import content, archive transaction metadata.

Main lifecycle is:

1. Files arrive in collection input folder.
2. File discovery marks files as `NEW`.
3. Packaging creates package from `NEW` files (`CREATED` state).
4. Package can be downloaded or sent to remote destination (`SENT` state).
5. Uploaded inbound packages are validated and imported (`IMPORTED`/`ARCHIVED`), or rejected/failed.

## Tech stack

- Java 17
- Spring Boot 2.7.18
- Maven
- PostgreSQL 15
- Quartz Scheduler
- Gson (JSON mapper)
- Docker

## Repository layout

- `src/main/java` – API, services, schedulers, persistence
- `src/main/resources/application.properties` – app configuration
- `db/init/001_airgap_schema.sql` – schema + tables + indexes
- `Dockerfile` – multi-stage build image for the service

## Prerequisites

1. JDK 17 (`mvn` build fails on older Java versions).
2. PostgreSQL running and reachable.
3. A Base64 AES-256 key (`AIRGAP_AES_KEY`).
4. Runtime config JSON available at `airgap.config.path` (default: `/opt/airgap/config/config.json`).

## Required environment variables

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/airgap_db
export SPRING_DATASOURCE_USERNAME=airgap_user
export SPRING_DATASOURCE_PASSWORD=airgap_pass
export AIRGAP_AES_KEY=<BASE64_32_BYTE_KEY>
```

## Runtime config file (`config.json`)

Minimal required structure:

```json
{
  "server": {
    "autoPackaging": {
      "hours": 1,
      "size-check-interval-min": 10
    },
    "downstream": {
      "download_package_path": "file:///tmp/downstream-out"
    },
    "upstream": {
      "upload_package_path": "file:///tmp/upstream-in"
    }
  },
  "ui": {
    "autoMode": true
  }
}
```

## Database initialization

Run `db/init/001_airgap_schema.sql` in PostgreSQL before starting the app.

## Local run: full steps

1. Export required environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/airgap_db
export SPRING_DATASOURCE_USERNAME=airgap_user
export SPRING_DATASOURCE_PASSWORD=airgap_pass
export AIRGAP_AES_KEY=<BASE64_32_BYTE_KEY>
```

2. Create local runtime folders:

```bash
mkdir -p runtime/downstream/collection/in
mkdir -p runtime/upstream/collection/out
mkdir -p runtime/downstream/original-packages
mkdir -p runtime/upstream/archive
mkdir -p runtime/upstream/work
mkdir -p runtime/upstream/package-input
mkdir -p config
```

3. Create `config/config.json` with the JSON structure above.

4. Override path properties for local machine (example):

```bash
export SPRING_APPLICATION_JSON='{
  "airgap": {
    "collect": {
      "in": "'"$(pwd)"'/runtime/downstream/collection/in",
      "out": "'"$(pwd)"'/runtime/upstream/collection/out",
      "rescan-ms": 30000
    },
    "save": {
      "original": {
        "packages": {
          "dir": "'"$(pwd)"'/runtime/downstream/original-packages"
        }
      }
    },
    "archive": {
      "packages": {
        "dir": "'"$(pwd)"'/runtime/upstream/archive"
      }
    },
    "unpack": {
      "work": {
        "dir": "'"$(pwd)"'/runtime/upstream/work"
      }
    },
    "uploaded": {
      "packages": {
        "dir": "'"$(pwd)"'/runtime/upstream/package-input"
      }
    },
    "config": {
      "path": "'"$(pwd)"'/config/config.json"
    }
  }
}'
```

5. Start application:

```bash
mvn spring-boot:run
```

Base URL:

`http://localhost:8081/airgapservice`

## Main API endpoints

All paths below are relative to `/airgapservice`:

- `/airgap/downstream/*` – downstream packaging/download/status/statistics
- `/airgap/upstream/*` – upstream upload/import/history/status
- `/airgap/statistics/*` – package/transaction stats + CSV/XLS exports
- `/airgap/file/stream` – SSE file events
- `/airgap/stream/ui-config` – SSE UI config updates
- `/airgap/automode?enabled=true|false` – toggle UI auto mode

## How to exercise app end-to-end

### A) Downstream packaging flow (create and download package)

1. Put test files in collection IN folder (configured by `airgap.collect.in`).
2. Call latest packaging:

```bash
curl -s "http://localhost:8081/airgapservice/airgap/downstream/package/latest?username=test"
```

3. List packages:

```bash
curl -s "http://localhost:8081/airgapservice/airgap/downstream/package/list"
```

4. Download package by id:

```bash
curl -L "http://localhost:8081/airgapservice/airgap/downstream/package/<PACKAGE_ID>/download?username=test" -o package.tar
```

5. Optional: send packages to downstream destination from `config.json`:

```bash
curl -X POST "http://localhost:8081/airgapservice/airgap/downstream/packages/send?username=test" \
  -H "Content-Type: application/json" \
  -d '{"packageIds":["<PACKAGE_ID>"]}'
```

### B) Upstream import flow (upload and import package)

Option 1: Import existing package file already present in `airgap.uploaded.packages.dir`:

```bash
curl -X POST "http://localhost:8081/airgapservice/airgap/upstream/package/<PACKAGE_NAME>.tar/upload?username=test&contingency=false"
```

Option 2: Drag-drop style multipart upload:

```bash
curl -X POST "http://localhost:8081/airgapservice/airgap/upstream/package/dragdrop?username=test&contingency=false" \
  -F "file=@/absolute/path/to/package.tar"
```

Option 3: Big binary stream upload:

```bash
curl -X POST "http://localhost:8081/airgapservice/airgap/upstream/package/bigstream?username=test&contingency=false" \
  -H "Content-Type: application/octet-stream" \
  -H "X-Filename: package.tar" \
  --data-binary "@/absolute/path/to/package.tar"
```

Then check:

```bash
curl -s "http://localhost:8081/airgapservice/airgap/upstream/package/list"
curl -s "http://localhost:8081/airgapservice/airgap/upstream/status"
```

### C) Status and statistics

```bash
curl -s "http://localhost:8081/airgapservice/airgap/downstream/status"
curl -s "http://localhost:8081/airgapservice/airgap/downstream/statistics"
curl -s "http://localhost:8081/airgapservice/airgap/upstream/status"
curl -s "http://localhost:8081/airgapservice/airgap/upstream/statistics"
```

### D) SSE streams

```bash
curl -N "http://localhost:8081/airgapservice/airgap/file/stream"
curl -N "http://localhost:8081/airgapservice/airgap/stream/ui-config"
```

### E) Toggle auto mode from API

```bash
curl -X POST "http://localhost:8081/airgapservice/airgap/automode?enabled=true"
curl -X POST "http://localhost:8081/airgapservice/airgap/automode?enabled=false"
```

## Scheduled jobs (Quartz)

- Time-based auto packaging (`hours` from `config.json`).
- Size-check auto packaging (`size-check-interval-min` and max size config in properties).
- Archive cleanup (cron from `airgap.cleanup.cron`).

## Notes

- `docker-compose.yml` currently references `./backend/...` paths; align paths with this repository layout before running compose.
- API base path is `/airgapservice` (from `server.servlet.context-path`).
