@echo off
title Music Lesson Booking
cd /d "%~dp0"

echo Compiling the lesson booking service...
call .\mvnw.cmd clean package
if errorlevel 1 (
    echo Build failed, see the errors above.
    pause
    exit /b 1
)

echo Starting the lesson booking service on http://localhost:8080
start "" /min cmd /c "timeout /t 8 /nobreak >nul & start http://localhost:8080/ & start http://localhost:8080/tutor.html"
java -jar target\lesson-booking-1.0.0.jar
pause
