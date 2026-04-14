@echo off
title Smart Inventory System

:: Try Maven first (if installed)
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [System] Found Maven. Running with 'mvn exec:java'...
    mvn clean compile exec:java
    goto end
)

:: Fallback: use compile.ps1 which handles paths with spaces correctly
echo [System] Maven not found. Using PowerShell compile script...
powershell -ExecutionPolicy Bypass -File "%~dp0compile.ps1"

:end
