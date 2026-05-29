# Troubleshooting Report

### System Specifications
**Operating System:** Windows 11 (local dev machine) + Linux VM (CS506 host)  
**CPU:** x86_64 (desktop + VM)  
**Container Runtime:** Docker + Docker Compose  
**Backend:** Spring Boot 4.0.3, Java 21, Gradle  
**Frontend:** React 19 + Vite 8  
**Database:** MySQL 8.0 (Docker container)  
**Network Setup:** Local browser/frontend + SSH tunnel to VM backend (`localhost:8080` forwarded to VM)

### Problem Description
The project had multiple integration failures across frontend, backend, and deployment environments:

1. Frontend initially showed a blank page due to a `sockjs-client` browser global issue (`global is not defined`).
2. Frontend/backend websocket mismatch: frontend used raw WebSocket while backend exposed SockJS/STOMP endpoints.
3. CORS errors blocked API requests (`/api/register`, `/api/preferences/*`) from local frontend to backend.
4. Repeated `ERR_CONNECTION_REFUSED` errors occurred when local frontend called `localhost:8080` without an active SSH tunnel.
5. Database initialization appeared incomplete; schema existed but expected user data/tables were missing until manual verification/load.
6. Login failures (`401`) occurred after registration due to auth flow inconsistencies and edge-case credential handling.
7. New custom tuning endpoints returned `404` even though code was written, due to deployment from the wrong backend folder on VM (duplicate backend directories).

Overall root cause: not one bug, but a chain of environment, protocol, deployment-path, and API integration issues.

### Problem Resolution
Resolution required stepwise isolation of each layer:

1. **Frontend runtime fix**
   - Added Vite define mapping for browser compatibility:
     - `global: 'globalThis'`
   - Eliminated blank-page crash from `sockjs-client`.

2. **WebSocket protocol alignment**
   - Replaced raw WebSocket frontend client with SockJS + STOMP.
   - Subscribed to backend topic endpoint (`/topic/tuning`) to match Spring WebSocket config.

3. **CORS and local dev routing**
   - Added backend CORS handling and then reinforced with local Vite proxy strategy.
   - Configured Vite dev server proxy for `/api` and `/ws` to local backend tunnel endpoint.
   - This removed browser cross-origin friction in local development.

4. **SSH tunnel/network debugging**
   - Confirmed backend health on VM with `curl http://localhost:8080/health`.
   - Confirmed local machine could only access backend if tunnel was open.
   - Established operational model:
     - frontend runs locally (`5173`)
     - tunnel forwards local `8080` to VM backend `8080`
     - DB remains on VM container (`3306`)

5. **Database verification and initialization**
   - Verified MySQL container health and database presence.
   - Confirmed `guitar_tuner` schema and table creation via direct container exec.
   - Manually validated tables and user data flow (`users`, `songs`, `tuning_profiles`, `string_configs`).

6. **Auth flow fixes**
   - Improved login/register behavior and error visibility.
   - Normalized credential handling (trim/validation) to avoid false login mismatches.

7. **Custom tuning feature groundwork**
   - Implemented backend tuning catalog APIs and DTOs.
   - Added dedicated frontend Custom Tuning tab/page with constrained note selection.
   - Added valid-note support and DB-backed tuning creation flow.
   - Final blocking issue (`404 /api/tunings/*`) traced to deployment path mismatch:
     - VM had two backend folders; Docker was building the wrong one.
   - Root deployment fix:
     - ensure compose build context points to the intended backend directory
     - rebuild backend image with no cache
     - verify classes exist in running `app.jar` before retesting API routes

### Sources
- Spring Boot documentation (Web MVC, CORS, WebSocket/STOMP)
- Vite documentation (dev server proxy, define config)
- Docker and Docker Compose documentation (build context, no-cache rebuilds)
- MySQL Docker image docs (initialization scripts and container exec usage)