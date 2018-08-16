@echo off
if "%~x1" equ "" goto end
if "%~x1" equ ".java" goto begin
goto end

:begin
java -cp .;lib\*; ga.uuid.app.%~n1
echo  press any key to exit.
pause > nul 2 > nul&exit

:end
:: echo please drag *.java to the CMD icon~ :)
call :begin Test