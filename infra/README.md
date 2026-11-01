# Infrastructure

Local and future deployment/runtime configuration for Docker and Nginx. Application business logic does not belong here.

## Ubuntu / PM2 deployment

For the existing Ubuntu + PostgreSQL + RustFS setup, use the
[step-by-step PM2 runbook](pm2/README.md). It includes API/Web launchers, a PM2
ecosystem declaration and production environment templates. Nginx, HTTPS and
domain configuration are managed separately by the operator. The files have
been checked locally; they have not been applied to the VPS.

[Deployment scripts](pm2/SCRIPTS.md) automate first deployment and updates from
`main`, with build checks, atomic current switching, API/Web health gates, a
deployment lock and explicit recovery/rollback. Existing PostgreSQL/RustFS
services are not restarted, backed up or restored by these scripts.

## Local development services

`docker/compose.yml` provides local-development PostgreSQL plus S3-compatible MinIO. A one-shot `minio-init` service creates the `knowledge-images` bucket and explicitly keeps anonymous access disabled.

```bash
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml ps
docker compose -f infra/docker/compose.yml down
```

The compose credentials are development-only defaults and must not be reused as production credentials.

PostgreSQL stays on major 17, using the project-maintained `pgvector/pgvector:0.8.6-pg17-bookworm` image (AMD64/ARM64). Flyway V8 creates the `vector` extension automatically with the extension-capable local role. Existing PostgreSQL 17 data volumes remain usable; do not run `down -v` to upgrade. Back up first and review collation changes when moving Alpine data to Debian. Production needs pgvector installed plus suitable migration-role privileges, not a superuser runtime API role. See [`../docs/SEMANTIC_RETRIEVAL.md`](../docs/SEMANTIC_RETRIEVAL.md).

Embedding generation is backend-only and disabled by default; this infrastructure change does not add a semantic search UI/API or replace FTS. MinIO Compose configuration and the public MinIO test mirror/pre-pull strategy are unchanged.

MinIO's S3 endpoint is `http://localhost:9000`; its administration console is `http://localhost:9001`. Application settings are listed in `../apps/api/.env.example`. The MinIO data volume is persistent, so `docker compose down` does not delete uploads. Production may use Cloudflare R2 through the same S3 client configuration; the bucket must remain private.
