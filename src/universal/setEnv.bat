@echo off

@if "%1"=="setup" goto setup
@for %%i in (lib\*.jar) do @call %0 setup %%i
@goto end
:setup
@SET CLASSPATH=%2;%CLASSPATH%
:end
