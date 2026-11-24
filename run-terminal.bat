@echo off
REM BorrowBuddy - terminal version, works with NO internet.
REM Every run recompiles the code first, so after you edit any .java file just run this again.
REM   run-terminal.bat           -> Supabase if DB_URL is set and reachable, otherwise the local database
REM   run-terminal.bat --offline -> always the local database in the data folder
cd /d "%~dp0"
echo Compiling...
call mvn -o -q package -DskipTests
if errorlevel 1 (
    echo.
    echo BUILD FAILED - read the [ERROR] lines above: they show the file and line number to fix.
    exit /b 1
)
java -jar target\borrowbuddy-1.0.0.jar %*
