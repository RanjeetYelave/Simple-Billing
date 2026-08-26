# Run Billsoft in a browser

Billsoft no longer needs Electron. Start the Spring Boot application, then open
the local address shown in the terminal (normally http://127.0.0.1:8080) in any
modern browser.

## macOS and Linux

From the `billsoft` directory, run:

```sh
./run-billsoft.sh
```

## Windows

Double-click `run-billsoft.bat`, or run it from Command Prompt.

## Requirements for this source checkout

- Java 21 installed and available as `java`
- Internet access only when Maven needs to download build dependencies

The future consumer installer will bundle Java, register startup at login/boot,
and open the browser automatically. Those installation responsibilities are not
part of the application server itself.
