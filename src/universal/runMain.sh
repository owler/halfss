#!/bin/sh

java -server -verbose:gc -Xms512m -Xmx2000m -cp "conf:lib/*" -Dplay.http.secret.key=abcdefghijk play.core.server.ProdServerStart


