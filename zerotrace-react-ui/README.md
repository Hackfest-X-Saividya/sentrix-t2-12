# ZeroTrace AI React UI

A React + Vite front end for the existing local ZeroTrace AI prototype. It preserves the three current API flows and clearly labels synthetic runtime events and fixture-based remediation.

## Local development

Requirements: Node.js 20+ and npm.

Start the existing Java services in separate terminals:
- Phase 1 scanner: `http://127.0.0.1:8080`
- Phase 2 ZeroWatch: `http://127.0.0.1:8081`
- Phase 3 ZeroRemediate: `http://127.0.0.1:8083`

From this folder:

```cmd
npm install
npm run dev
```

Open `http://127.0.0.1:5173`.

Vite proxies:
- `/api/scan/*` -> Phase 1 `/api/v1/*`
- `/api/watch/*` -> Phase 2 `/api/v1/zerowatch/*`
- `/api/remediate/*` -> Phase 3 `/api/v1/*`

This avoids browser CORS for the React development server. If a service is stopped, the status rail marks it offline.

## Build

```cmd
npm run build
npm run preview
```

The build output is in `dist/`. The Vite development proxy is not automatically present in a production deployment; configure a reverse proxy or add suitable backend routing before serving this build outside local development.

## Honest implementation boundaries

- Source scanning calls the existing Phase 1 API.
- ZeroWatch sends synthetic `ACCESS_DENIED` events to the existing Phase 2 API and displays returned alerts.
- ZeroRemediate uses the existing fixture provider. The proposal is not applied to uploaded source files.
- Local services use in-memory state and can lose data on restart.


## Bug-fix note

The ZeroRemediate demo payload now uses a JavaScript template literal so `codeContext` is sent as the exact two-line SQL fixture, including ordinary Java double quotes and a real newline. This is required by the deterministic fixture provider.


## ZeroWatch ingestion fix

The ZeroWatch event ingestion endpoint is proxied through a small Vite middleware that buffers only `/api/watch/events` requests (maximum 16 KiB) and forwards them to `127.0.0.1:8081` with an explicit `Content-Length`. This supports the backend's bounded-body requirement and avoids chunked/unknown-length requests being rejected. Other ZeroWatch GET requests continue through the standard Vite proxy. The demo uses canonical UUID event IDs and timestamps in the recent past. It creates synthetic access-denied events only; it does not instrument a live application.

The proxy middleware is for local development only. Do not use the Vite development server as a production API gateway.
