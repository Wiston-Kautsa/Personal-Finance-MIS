#!/usr/bin/env sh
set -eu

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install JDK 21 or newer, or set JAVA_HOME." >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven was not found. Install Maven or run from an IDE with Maven support." >&2
  exit 1
fi

mvn javafx:run
