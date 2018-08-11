@echo off
if "%~x1" equ "" goto end
if "%~x1" equ ".java" goto begin
goto END

:begin
java -cp .;lib\*; ga.uuid.app.%~n1 
pause&exit

:end
echo please drag *.java to the CMD icon~ :)
pause&exit