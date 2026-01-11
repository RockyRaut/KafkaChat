@echo off
echo ========================================
echo Kafka Chat Server - Quick Start
echo ========================================
echo.

echo Starting Docker services...
docker-compose up -d

echo.
echo Waiting for services to start...
timeout /t 10 /nobreak >nul

echo.
echo ========================================
echo Server Status:
echo ========================================
docker-compose ps

echo.
echo ========================================
echo Your PC's IP Address:
echo ========================================
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set ip=%%a
    set ip=!ip:~1!
    echo http://!ip!:8080
    echo ws://!ip!:8080/ws
    goto :found
)
:found

echo.
echo ========================================
echo Next Steps:
echo ========================================
echo 1. Configure firewall to allow port 8080
echo 2. Open the app on your phones
echo 3. Enter the server URL shown above
echo 4. Start chatting!
echo.
echo To view logs: docker-compose logs -f
echo To stop: docker-compose down
echo.
pause





