#!/bin/sh

LIB_DIR="lib"

echo $LIB_DIR
for jar in $LIB_DIR/*.jar;
do
    CP="$CP:$jar"
done

java -server -verbose:gc -Xms512m -Xmx3200m -cp $CP -Dplay.http.secret.key=abcdefghijk play.core.server.ProdServerStart


