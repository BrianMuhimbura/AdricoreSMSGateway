# AdricoreSmsGatewayInterface

Angular 21 standalone application with PrimeNG UI components and a simple client-side authentication flow.

## Features Added
- PrimeNG + PrimeFlex + PrimeIcons UI stack (Lara Light Blue theme).
- Simple AuthService (in-memory) with login/logout.
- Protected home area with sidebar (PanelMenu) and header showing logged in user.
- Routed feature placeholders: Dashboard, Partners, Transactions, Messages.
- Logout button returns to login screen.

## Prerequisites
- Node.js 20+ recommended (project currently using Node 22).
- npm (comes with Node).

## Install
```bash
npm install --legacy-peer-deps
```
Note: PrimeNG 17 expects Angular <= 18; using `--legacy-peer-deps` allows install with Angular 21 for UI prototyping. For production, consider upgrading to matching PrimeNG version once available.

## Run Dev Server
```bash
npm start
```
Navigate to: http://localhost:4200

## Authentication
Use any non-empty username & password. Empty credentials are rejected.
- Login route: `/login`
- After login redirected to `/home`
- Logout returns to `/login`

## Routes
| Path | Description |
|------|-------------|
| /login | Public login screen |
| /home | Protected layout (defaults to Dashboard) |
| /home/partners | Partners placeholder |
| /home/transactions | Transactions placeholder |
| /home/messages | Messages placeholder |

## Build
```bash
npm run build
```
Artifacts: `dist/AdricoreSmsGatewayInterface/`

## Unit Tests
Basic AuthService test:
```bash
npm test
```

## Project Structure (Key Files)
- `src/app/auth.service.ts` – login/logout state with signals.
- `src/app/auth.guard.ts` – protects `home` routes.
- `src/app/login.component.ts` – login form.
- `src/app/home-layout.component.ts` – shell with sidebar & header.
- `src/app/app.routes.ts` – route configuration.

## Customization
- Replace in-memory auth with API integration inside `AuthService.login()`.
- Add role-based items in `HomeLayoutComponent.items`.
- Add real dashboard widgets (charts, stats) inside `DashboardComponent`.

## Styling
Global styles import PrimeNG theme and Tailwind for utility classes in `src/styles.css`.

## Next Steps
- Add persistent auth (e.g., localStorage / JWT).
- Add HTTP interceptors for API calls.
- Add real data tables (DataTable, Chart components from PrimeNG).

## License
Internal project – license not specified.
