# Deployment

GitHub Actions is split into two workflows:

1. `CI`: test, build `bootJar`, build and push Docker image to GHCR.
2. `CD`: after `CI` succeeds on `main`, connect to the server over SSH and run `docker compose pull` plus `docker compose up -d`.

## Workflow files

- [ci.yml](/C:/payment/api_test/.github/workflows/ci.yml)
- [cd.yml](/C:/payment/api_test/.github/workflows/cd.yml)

## GitHub secrets

Create these secrets in the repository:

- `SERVER_HOST`
- `SERVER_PORT`
- `SERVER_USER`
- `SERVER_SSH_KEY`

`CI` uses the built-in `GITHUB_TOKEN` to push to GHCR.

## Image name

The workflow publishes this image:

```text
ghcr.io/<repository-owner>/payment-api
```

For example, if the owner is `acme`, the deployed image becomes:

```text
ghcr.io/acme/payment-api:latest
```

## Server-side compose example

Keep `compose.yml` and `.env` only on the server.

Example `compose.yml`:

```yaml
services:
  api:
    image: ${IMAGE_NAME}
    container_name: payment-api
    restart: unless-stopped
    ports:
      - "8080:8080"
    env_file:
      - .env
```

Example server `.env`:

```env
IMAGE_NAME=ghcr.io/your-org/payment-api:latest
```

## Server deployment directory

`CD` assumes the server compose project is located here:

```text
~/payment-api
```

If your server path is different, update [cd.yml](/C:/payment/api_test/.github/workflows/cd.yml).

## Notes

- The server must already have Docker Engine and Docker Compose installed.
- The workflows do not create `compose.yml` or `.env` on the server.
- `CI` skips image push on `pull_request` and only publishes on non-PR runs.
