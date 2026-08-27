@echo off
setlocal

echo Stopping EV Relay MES L1/L2 simulator processes...
taskkill /IM l1_simulator.exe /F >nul 2>&1
taskkill /IM mes_collector.exe /F >nul 2>&1

echo L1 simulators and L2 collector have been stopped.
endlocal
