# Copilot Instructions for Billsoft UI

## Project Overview
- **Billsoft UI** is a browser-based invoicing frontend, using vanilla JS modules and a single-page app structure.
- The UI interacts with a backend API (default: `http://localhost:8080`) for all data (customers, products, invoices).
- All business logic and state are managed client-side in the `js/` directory.

## Key Components
- `index.html`: Loads the app, includes only a root `<div id="app">` and the main JS module.
- `js/main.js`: App entry point. Initializes UI, loads data, binds event handlers.
- `js/ui.js`: Renders and updates the DOM. All UI logic (forms, tables, event wiring) is here.
- `js/product.js`, `js/customer.js`, `js/invoice.js`: Modules for managing products, customers, and invoices. Each provides `load`, `save`, and `preview` methods.
- `js/api.js`: Handles all HTTP requests to the backend. Update `BASE` if backend URL changes.
- `js/utils.js`: Utility functions for DOM access, formatting, and ID extraction.

## Data Flow
- On load, `main.js` calls `productModule.load()` and `customerModule.load()` to fetch initial data.
- UI updates are triggered by user actions (form input, button clicks) and handled in `ui.js`.
- All create/update actions call backend via `api.js` and update local state on success.

## Developer Workflows
- **No build step**: All code is ES6 modules, loaded directly in the browser.
- **Run locally**: Serve the folder with any static server (e.g. `python3 -m http.server`).
- **Backend required**: The UI expects a backend at `http://localhost:8080` with REST endpoints for `/api/customers`, `/api/products`, `/api/invoices`.
- **Debugging**: Use browser dev tools. All errors are logged to the console.

## Project Conventions
- Use `id` attributes for all major form fields and buttons. Access via `$(id)` from `utils.js`.
- Data objects for products, customers, and invoices follow the backend API schema.
- UI updates are always performed via methods in `ui.js`.
- All network errors should be caught and surfaced to the user (see `main.js` for pattern).

## Examples
- To add a new product: Use `productModule.save()` after filling the product form.
- To render the invoice table: Call `ui.render()` and `ui.populateCustomers()` after data loads.

## External Integrations
- No third-party JS frameworks are used. All logic is custom ES6 modules.
- The only dependency is the backend API.

## File Reference
- See `js/ui.js` for UI patterns and DOM structure.
- See `js/api.js` for backend integration and endpoint structure.
- See `js/main.js` for app initialization and workflow.

---
For any new features, follow the modular structure and update the relevant module and UI logic. Keep all DOM manipulation inside `ui.js`.
