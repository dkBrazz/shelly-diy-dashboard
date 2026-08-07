# Dashboard frontend

React and TypeScript client for the Shelly DIY Dashboard. See the repository root `README.md` for setup and `AGENTS.md` for contribution rules.

Useful commands from this directory:

```bash
npm ci
npm run dev
npm run check
```

Vite proxies `/api`, `/login`, `/logout`, and `/oauth2` to the backend on port 8080. Production builds are emitted to `../src/main/resources/static`.
