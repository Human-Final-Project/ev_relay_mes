@echo off
setlocal

set "EMBEDDED_DIR=%~dp0"
set "L1_DIR=%EMBEDDED_DIR%mes_L1"
set "L2_DIR=%EMBEDDED_DIR%mes_collector"
set "L1_EXE=%L1_DIR%\l1_simulator.exe"
set "L2_EXE=%L2_DIR%\mes_collector.exe"

rem Prevent duplicate collectors/simulators from making communication alarms and stale sessions.
tasklist /FI "IMAGENAME eq mes_collector.exe" 2>NUL | find /I "mes_collector.exe" >NUL
if not errorlevel 1 (
    echo [ERROR] mes_collector.exe is already running.
    echo         Run stop_all.bat first, then start again.
    pause
    exit /b 1
)
tasklist /FI "IMAGENAME eq l1_simulator.exe" 2>NUL | find /I "l1_simulator.exe" >NUL
if not errorlevel 1 (
    echo [ERROR] l1_simulator.exe is already running.
    echo         Run stop_all.bat first, then start again.
    pause
    exit /b 1
)
set "ALARM_RATE=25"

if not exist "%L2_EXE%" (
    echo [ERROR] L2 executable not found: %L2_EXE%
    pause
    exit /b 1
)
if not exist "%L1_EXE%" (
    echo [ERROR] L1 executable not found: %L1_EXE%
    pause
    exit /b 1
)

echo Starting L2 collector...
start "MES L2 Collector" /D "%L2_DIR%" "%L2_EXE%"
timeout /t 2 /nobreak >nul

echo Starting six L1 simulators with random alarms. Job alarm rate=%ALARM_RATE%%%
start "L1 EQ-WIND-01 - OP20 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-WIND-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%
start "L1 EQ-WELD-01 - OP30 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-WELD-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%
start "L1 EQ-ASSY-01 - OP40_OP50 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-ASSY-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%
start "L1 EQ-SEAL-01 - OP60 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-SEAL-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%
start "L1 EQ-TEST-01 - OP70 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-TEST-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%
start "L1 EQ-PACK-01 - OP80 RANDOM ALARM" /D "%L1_DIR%" "%L1_EXE%" EQ-PACK-01 127.0.0.1 9000 --alarm-random --alarm-rate %ALARM_RATE%

echo.
echo Random-alarm demo started. Each START job independently schedules at most one alarm.
echo WARNING alarms continue production; ERROR alarms pause until MES clear and RESUME.
endlocal
