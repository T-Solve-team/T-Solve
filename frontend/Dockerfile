# syntax=docker/dockerfile:1
# T-Solve web app image: Node builds the static files, Caddy serves them (guideline 11 §6).
#   docker build -t tsolve-frontend .
#   docker run --rm -p 8081:8080 tsolve-frontend      → http://localhost:8081
# Base image versions are pinned; Dependabot (DevOps) proposes the updates.

# ---- 1. Build the static files ----------------------------------------------------------------
# The files are the same for every CPU type, so this stage always runs on the build machine's own.
FROM --platform=$BUILDPLATFORM node:24.22.1-alpine3.24 AS build
WORKDIR /app

# Dependencies first: this layer is reused until package.json or package-lock.json change.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# No build args: the app calls /api/v1/… on the origin it is served from (routed by the environment's main
# Caddy), so one image serves every Workspace site.
RUN npm run build

# ---- 2. Serve them ----------------------------------------------------------------------------
FROM caddy:2.11.7-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

# Not root: 65534 is Alpine's "nobody". Port 8080 needs no extra rights, and the image's /config and
# /data folders are writable by every user.
USER 65534:65534
EXPOSE 8080
# wget exits with 1 when /healthz does not answer 200, which Docker reads as "unhealthy".
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD ["wget", "-q", "--spider", "http://127.0.0.1:8080/healthz"]
# CMD comes from the Caddy image: caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
