#!/usr/bin/env bash
# ============================================================================
# run-scenarios.sh — Generate test data and exercise every transfer path
# ============================================================================
set -euo pipefail

echo "═══════════════════════════════════════════════════════════"
echo "  File Shuttle — Integration Test Scenarios"
echo "═══════════════════════════════════════════════════════════"

# ── Generate test files ───────────────────────────────────────
echo ""; echo "▶ Generating test files..."
mkdir -p test-data
dd if=/dev/urandom of=test-data/test-file.bin bs=1M count=1  2>/dev/null
dd if=/dev/urandom of=test-data/large-file.bin bs=1M count=50 2>/dev/null
echo '{"name":"shuttle-test","ts":"2025-01-01T00:00:00Z"}' > test-data/test.json
echo "  Created: test-file.bin (1MB), large-file.bin (50MB), test.json"

# ── Build ─────────────────────────────────────────────────────
echo ""; echo "▶ Building containers..."
docker compose build --quiet

# ── Scenario 1: Local → S3 ───────────────────────────────────
echo ""; echo "══ Scenario 1: LOCAL → S3 (1 MB) ══"
docker compose run --rm file-shuttle \
  -f test-file.bin -it local -i /data/local/test-file.bin \
  -ot s3 -o s3://test-bucket/scenario1/test-file.bin \
  --s3-endpoint http://minio:9000

# ── Scenario 2: S3 → NFS ─────────────────────────────────────
echo ""; echo "══ Scenario 2: S3 → NFS (1 MB) ══"
docker compose run --rm file-shuttle \
  -f test-file.bin -it s3 -i s3://test-bucket/scenario1/test-file.bin \
  -ot nfs -o /mnt/nfs-share/scenario2/test-file.bin \
  --s3-endpoint http://minio:9000

# ── Scenario 3: Local → URL (HTTP PUT) ───────────────────────
echo ""; echo "══ Scenario 3: LOCAL → URL (PUT, 1 MB) ══"
docker compose run --rm file-shuttle \
  -f test.json -it local -i /data/local/test.json \
  -ot url -o http://upload-server:8080/test.json \
  --http-method PUT

# ── Scenario 4: S3 → URL (HTTP POST) ─────────────────────────
echo ""; echo "══ Scenario 4: S3 → URL (POST) ══"
docker compose run --rm file-shuttle \
  -f test-file.bin -it s3 -i s3://test-bucket/scenario1/test-file.bin \
  -ot url -o http://upload-server:8080/from-s3.bin \
  --s3-endpoint http://minio:9000 --http-method POST

# ── Scenario 5: URL → S3 ─────────────────────────────────────
echo ""; echo "══ Scenario 5: URL → S3 ══"
docker compose run --rm file-shuttle \
  -f from-url.bin -it url -i http://upload-server:8080/from-s3.bin \
  -ot s3 -o s3://output-bucket/from-url/data.bin \
  --s3-endpoint http://minio:9000

# ── Scenario 6: Local → S3 (50 MB throughput) ────────────────
echo ""; echo "══ Scenario 6: LOCAL → S3 (50 MB, 128KB buffer, 16MB parts) ══"
docker compose run --rm file-shuttle \
  -f large-file.bin -it local -i /data/local/large-file.bin \
  -ot s3 -o s3://test-bucket/perf/large-file.bin \
  --s3-endpoint http://minio:9000 -bs 131072 -ms 16777216

# ── Scenario 7: NFS → S3 ─────────────────────────────────────
echo ""; echo "══ Scenario 7: NFS → S3 ══"
docker compose run --rm file-shuttle \
  -f test-file.bin -it nfs -i /mnt/nfs-share/scenario2/test-file.bin \
  -ot s3 -o s3://output-bucket/from-nfs/test-file.bin \
  --s3-endpoint http://minio:9000

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  All 7 scenarios complete!"
echo "═══════════════════════════════════════════════════════════"
echo "  MinIO Console: http://localhost:9001 (minioadmin/minioadmin)"
echo "  Upload Server: curl http://localhost:8080/test.json"
echo ""
# docker compose down -v   # uncomment to tear down
