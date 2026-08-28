# Simple-Billing (WAR-based Desktop Application System)

This project has been turned into a reliable, cross-platform desktop application using a JavaFX/WebView desktop shell and a separate launcher-updater runtime wrapper.

## Project Structure

The project is structured as a Maven multi-module project:
*   **`billsoft`**: The core Spring Boot web application containing the UI (HTML/CSS/JS), backend logic, and H2 database configurations. Packaged as a standard `WAR`.
*   **`shell`**: A lightweight JavaFX desktop shell that launches the Spring Boot backend on a free local port, monitors its health at `/api/health`, and renders it within a native `WebView`.
*   **`launcher`**: A tiny console application that serves as the entry point for production deployments, coordinates atomic updates, creates database backups, and handles automatic rollbacks if a version boot fails.

---

## Production Folder Layout

A typical packaged desktop release has the following layout:
```
SimpleBilling/
├── launcher.jar             # Main application entry point (or compiled platform-native binary)
├── shell.jar                # JavaFX desktop UI shell
├── runtime/
│   ├── billsoft.war         # The running version of the Spring Boot application
│   └── billsoft-update.war  # Temp file downloaded when an update is fetched
└── user-data/
    └── database.mv.db       # User SQLite/H2 database and files (outside application folder)
```

---

## Development Workflow

### 1. Build the entire reactor
Run the Maven wrapper from the root directory to clean and package all modules:
```bash
./mvnw clean package -DskipTests
```

### 2. Run the application
To test the complete launcher-updater system locally:
```bash
java -jar launcher/target/launcher-0.0.1-SNAPSHOT.jar
```
The launcher will search for your built modules and automatically spin up the JavaFX Webview shell.

---

## Automatic Update & Rollback Flow

1.  **Check & Download:** While running, the application checks for updates from GitHub Releases via `UpdateService.java`. When an update is detected, it downloads the new WAR to `runtime/billsoft-update.war`, persists version details, and triggers `System.exit(0)`.
2.  **Process Swapping:** The launcher detects the termination of the application and checks for the existence of `billsoft-update.war`.
3.  **Backup:** Launcher creates a database snapshot to `backup/` under the user-data folder.
4.  **Install:** Overwrites `billsoft.war` with the new update file.
5.  **Health Check & Monitoring:** Spawns the JavaFX shell and monitors it for 25 seconds.
6.  **Rollback:** If the new version fails to boot (crashing due to database migration errors, etc.), the launcher stops the process, restores the database backup, rolls back `billsoft.war` to the previous backup, and restarts the old stable version.
