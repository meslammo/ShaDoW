# MOD-08 — Gateway foundation

The SHADOW gateway is now represented as a deployable FastAPI service with bearer-token authentication, health endpoint, runtime request endpoint, Dockerfile and environment template.

Production deployment still requires an actual HTTPS host and server-side provider credentials. The Android release will not hard-code secrets or a LAN address.
