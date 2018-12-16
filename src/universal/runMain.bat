@echo off
echo Main started

call setEnv.bat

java -server -verbose:gc -Xms512m -Xmx1512m -cp "%CLASSPATH%" -Dplay.http.secret.key=abcdefghijk play.core.server.ProdServerStart
exit