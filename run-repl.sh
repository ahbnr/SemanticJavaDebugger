#!/bin/bash

set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
WORKING_DIR=$(pwd)


cd "$SCRIPT_DIR"
./gradlew -q classes
# Based on https://stackoverflow.com/a/45858448
while read line; do
    if [[ $line =~ ^[[:blank:]]*([^[:blank:]]+)[[:blank:]]*=(.*)$ ]]; then
        var="${BASH_REMATCH[1]}"
        val="${BASH_REMATCH[2]}"

        declare $var="$val"
     fi
done < <( ./gradlew runInfo )
cd "$WORKING_DIR"

# shellcheck disable=SC2068
java -cp "$CLASSPATH" --add-opens jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED $MAINCLASS $@
