---
module: platform
feature: file-storage
task: 16
attempt: 2
date: 2026-11-11
---

# Test report: platform — Task 16: file-storage

Environment: Windows 11, Git Bash, JDK = the IntelliJ 2025.1.1.1 JBR, Docker with `-Dapi.version=1.44`, run on the
uncommitted working tree of `feat/KAN-96-platform-file-storage`.

| Run | Command (in `backend/`) | Result |
|-----|-------------------------|--------|
| Targeted IT | `mvn -B -Dapi.version=1.44 -Dit.test=ObjectFileStorageIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false verify` | `ObjectFileStorageIT` 12/12 passed (8.3 s), `spotless:check` clean, BUILD SUCCESS |
| Full suite | `mvn -B -Dapi.version=1.44 verify` | Surefire 1242/1242, Failsafe 433/433, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. The D9 condition leaves the other application contexts starting |
| Traceability | `bun harnessclaude/hooks/sdd.ts trace platform STORY-007` | 15/15 criteria have a test |
| Secret scan | `gitleaks dir backend/src/test/java/vn/tsolve/support/GarageTestBase.java` | **1 leak**: `generic-api-key`, line 28 (`rpc_secret`). The pre-commit hook and the CI `gitleaks` check would fail |
| Dependencies | `mvn dependency:tree` | `s3` and `apache-client` 2.57.12, `httpclient` 4.5.13, and `commons-logging` 1.2 (compile, transitive). No `apache5-client` or `netty-nio-client` |

Coverage: no coverage tool is configured for the backend, so none was measured. Every public method of `ObjectFileStorage` and
every branch of the AC-relevant paths runs in the IT. The non-404 `S3Exception` from HEAD and the `StorageProperties`
validation branches are not run.

## Attempt 2 (2026-11-11)
The reviewer did not run Maven or Docker (barred by the dispatcher while the orchestrator mvn verify ran). Results come from that run, in backend/target/.

| Run | Source | Result |
|-----|--------|--------|
| Unit | surefire-reports StorageConfigTest (11:20) | 7/7 passed |
| IT | failsafe-reports ObjectFileStorageIT (11:26) | 14/14 passed, 12.1 s; Spring ITs ran earlier in the same JVM (Spring log format in the XML) |
| Traceability | sdd.ts trace platform STORY-007 | 15/15 |
| Secret scan | gitleaks dir backend/src --no-banner | no leaks found |
| JFR probe | scratch probes on JBR 21.0.6 and JDK 24 | stdout/stderr writes from any thread record jdk.FileWrite path=null; a logback default-config DEBUG line is 1 violation under the IT filter (review A2-1) |

## Attempt 3 (2026-11-11)
| Run | Command / source | Result |
|-----|------------------|--------|
| IT alone (no Spring context in the JVM) | `mvn -B -Dapi.version=1.44 -Dit.test=ObjectFileStorageIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false verify` | ObjectFileStorageIT 14/14 (16.8 s), spotless clean, BUILD SUCCESS. Logback default format in the XML confirms the isolated scenario |
| Traceability | `sdd.ts trace platform STORY-007` | 15/15 |
| Secret scan | `gitleaks dir backend/src --no-banner` | no leaks found |
| JFR probe | scratchpad Probe.java on JBR 21.0.6 | Files.write, FileOutputStream(File), RandomAccessFile and FileChannel writes report real paths. Only console writes and FileOutputStream(FileDescriptor) report null |
| Full suite | not re-run | orchestrator's run before the fix: 1249 unit and 435 IT, 0 failures |
