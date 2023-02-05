#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

JAVA_VER=$(java -version 2>&1 | sed -n ';s/.* version "\(.*\)\.\(.*\)\..*".*/\1\2/p;')
if [ "$JAVA_VER" -lt 11 ]
then
  echo "You need at least Java 11"
  exit
fi

./gradlew run --args="--color examples/twothreetree/TwoThreeTree.sjd"
