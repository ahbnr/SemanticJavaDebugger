#!/bin/bash
set -e

WORKING_DIR=$(pwd)
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

SJDB="$SCRIPT_DIR/../../sjdb"

JAVA_VER=$(java -version 2>&1 | sed -n ';s/.* version "\(.*\)\.\(.*\)\..*".*/\1\2/p;')
if [ "$JAVA_VER" -lt 11 ]
then
  echo "You need at least Java 11"
  exit
fi

cd "$SCRIPT_DIR"
./gradlew -q classes

"$SJDB" BTreeIteration.sjdb
cd "$WORKING_DIR"