#!/bin/bash

./gradlew -q compileKotlin

# Based on https://stackoverflow.com/a/45858448
while read line; do
    if [[ $line =~ ^[[:blank:]]*([^[:blank:]]+)[[:blank:]]*=(.*)$ ]]; then
        var="${BASH_REMATCH[1]}"
        val="${BASH_REMATCH[2]}"

        declare $var="$val"
     fi
done < <( ./gradlew runInfo )

java -cp "$CLASSPATH" $MAINCLASS
