# Infrastructure

Local and future deployment/runtime configuration for Docker and Nginx. Application business logic does not belong here.

`docker/compose.yml` currently provides only the local-development PostgreSQL service:

```bash
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml ps
docker compose -f infra/docker/compose.yml down
```

The compose credentials are development-only defaults and must not be reused as production credentials.
