@echo off
rem Double-click to open HtzCosmetics Studio (desktop app to manage, test and publish cosmetics).
title HtzCosmetics Studio
cd /d "%~dp0"
echo Abriendo HtzCosmetics Studio... (la primera vez tarda un poco)
call "%~dp0gradlew.bat" --console=plain -q htzStudio
if errorlevel 1 (
  echo.
  echo No se pudo abrir HtzCosmetics Studio. Revisa el mensaje de arriba.
  pause
)
