# SHADOW v0.39 — Mobile Client Contract

Android/iOS/mobile clients communicate **only** with the authenticated Shadow Gateway.

## Transport

- `GET /health`
- `POST /v1/request`
- Header: `Authorization: Bearer <token>` when configured
- Request: `{ "id": "...", "action": "run_goal", "payload": {"goal": "..."}, "context": {} }`
- Response: `{ "id": "...", "ok": true, "data": {...} }`

## Security boundary

The mobile client is a transport/UI boundary. It never receives tool credentials and
never executes Shadow tools directly. All tool execution stays inside the core:

`Mobile → Gateway → Runtime → Agent/Brain → ToolCatalog → SecurityPolicy → Approval → SecureToolExecutor`

The mobile client does not contain API keys. Gateway authentication is supplied at
runtime through configuration/environment and is never persisted by this package.

## Local testing

From the project root:

```bash
python -m shadow.clients.mobile --health --url http://127.0.0.1:8787
python -m shadow.clients.mobile "Hello SHADOW" --url http://127.0.0.1:8787
```

For a protected gateway, provide `SHADOW_GATEWAY_TOKEN` or `--token`.

Voice capture/playback remains a platform concern; the existing provider-neutral
voice pipeline stays in `shadow.perception.voice` and is not duplicated here.
