@echo off
cd /d "%~dp0"
schtasks /Create /TN "AirRemotePcReceiver" /TR "powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"%~dp0pc_receiver.ps1\"" /SC ONLOGON /RL HIGHEST /F
echo Air Remote receiver will now start automatically when you log in to Windows.
echo To run it now: start_pc_receiver.bat
pause