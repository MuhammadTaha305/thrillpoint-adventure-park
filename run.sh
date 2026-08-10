#!/usr/bin/env bash
#
# Build and run ThrillPoint Adventure Park.
#
# Works with either JavaFX setup:
#   * a JDK that bundles JavaFX (Liberica Full, Zulu FX) - no flags needed
#   * a plain JDK plus the OpenJFX SDK - set JAVAFX_HOME to its lib directory,
#     or unzip the SDK into ./javafx-sdk next to this script
#
# Usage:  ./run.sh          build and launch the dashboard
#         ./run.sh demo     build and run the race condition demo only
#         ./run.sh build    build only

set -e
cd "$(dirname "$0")"

FXFLAGS=""
if [ -n "$JAVAFX_HOME" ]; then
    FXFLAGS="--module-path $JAVAFX_HOME --add-modules javafx.controls"
    echo "Using JavaFX from JAVAFX_HOME: $JAVAFX_HOME"
elif [ -d "javafx-sdk/lib" ]; then
    FXFLAGS="--module-path javafx-sdk/lib --add-modules javafx.controls"
    echo "Using JavaFX from ./javafx-sdk/lib"
else
    echo "No JAVAFX_HOME set - assuming your JDK bundles JavaFX."
fi

rm -rf bin
mkdir -p bin

echo "Compiling..."
javac $FXFLAGS -d bin src/*.java
echo "Build OK."

case "$1" in
    build)
        ;;
    demo)
        java $FXFLAGS -cp bin RaceConditionDemo
        ;;
    *)
        java $FXFLAGS -cp bin Main
        ;;
esac
