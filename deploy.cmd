@echo off
setlocal

:: -----------------------------------------------------------------------
:: Script: deploy.cmd
:: Location: md-platform/
:: Purpose: Performs Maven build, creates Docker images, and starts services.
:: -----------------------------------------------------------------------

echo.
echo --- 1. Performing CLEAN MAVEN BUILD for all modules ---
echo.

:: Execute the full Maven lifecycle (clean, compile, package).
:: The -DskipTests flag is optional but speeds up deployment.
call mvn clean package -DskipTests

:: Check the exit code of the last command (mvn).
IF ERRORLEVEL 1 (
    echo.
    echo [ERROR] Maven build failed. Aborting deployment.
    echo.
    goto :eof
)

echo.
echo --- 2. Building Images and Starting Infrastructure/Applications ---
echo.

:: The 'docker compose' command requires the -f flag because the YAML file
:: is in the 'infra/' subdirectory, not the current directory.
:: --build: Forces a rebuild of images (needed for fresh JARs).
:: --force-recreate: Ensures new containers are created from the new images.
:: -d: Runs services in detached (background) mode.
call docker compose -f infra/docker-compose.yml up -d --build --force-recreate

:: Check the exit code of the last command (docker compose).
IF ERRORLEVEL 1 (
    echo.
    echo [ERROR] Docker Compose failed to start services.
    echo.
    goto :eof
)

echo.
echo --- DEPLOYMENT COMPLETE ---
echo.

:: Optional: Display the status of running containers and the last few logs.
echo Current running services:
call docker compose -f infra/docker-compose.yml ps

endlocal