@echo on
echo Main started

java -server -verbose:gc -Xms512m -Xmx1512m -cp "conf;lib\*" -Dplay.http.secret.key=abcdefghijk play.core.server.ProdServerStart
exit