
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
- Java 11
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
