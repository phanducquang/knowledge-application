# Infrastructure

Local and future deployment/runtime configuration for Docker and Nginx. Application business logic does not belong here.

`docker/compose.yml` provides local-development PostgreSQL plus S3-compatible MinIO. A one-shot `minio-init` service creates the `knowledge-images` bucket and explicitly keeps anonymous access disabled.

```bash
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml ps
docker compose -f infra/docker/compose.yml down
```

The compose credentials are development-only defaults and must not be reused as production credentials.

MinIO's S3 endpoint is `http://localhost:9000`; its administration console is `http://localhost:9001`. Application settings are listed in `../apps/api/.env.example`. The MinIO data volume is persistent, so `docker compose down` does not delete uploads. Production may use Cloudflare R2 through the same S3 client configuration; the bucket must remain private.
