@echo off
cd /d "%~dp0"
title Grabbit - upload to GitHub
if not exist "app\build.gradle.kts" (
  echo ERROR: put this file INSIDE the grabbit-android folder, next to the "app" folder.
  pause
  exit /b 1
)
set "GIT="
for %%G in ("%ProgramFiles%\Git\cmd\git.exe" "%ProgramFiles(x86)%\Git\cmd\git.exe" "%LOCALAPPDATA%\Programs\Git\cmd\git.exe" "%ProgramW6432%\Git\cmd\git.exe") do if exist %%G set "GIT=%%~G"
if not defined GIT for /f "delims=" %%G in (where git 2^>nul) do if not defined GIT set "GIT=%%G"
if not defined GIT (
  echo Git not found. Opening the download page - install it with all default options, then run this file again.
  start "" https://git-scm.com/download/win
  pause
  exit /b 1
)
echo Using Git: %GIT%
echo Preparing files...
if not exist ".git" "%GIT%" init -q
"%GIT%" checkout -q -B main
"%GIT%" add -A
"%GIT%" -c user.name=O9009 -c user.email=O9009@users.noreply.github.com commit -q -m "Grabbit Android"
"%GIT%" remote remove origin 2>nul
"%GIT%" remote add origin https://github.com/O9009/o9009.git
echo.
echo Uploading to GitHub. If a GitHub sign-in window opens - sign in and approve.
"%GIT%" push -f origin main
if errorlevel 1 (
  echo.
  echo Upload failed - take a screenshot of this window and send it to Claude.
  pause
  exit /b 1
)
echo.
echo DONE! Opening the build page. Wait 5-10 minutes for the green check mark.
start "" https://github.com/O9009/o9009/actions
pause
