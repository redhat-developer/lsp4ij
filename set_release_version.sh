#!/bin/bash

GRADLE_PROPERTIES_FILE="gradle.properties"

# Read the current version from gradle.properties
CURRENT_VERSION=$(grep "pluginVersion=" "$GRADLE_PROPERTIES_FILE" | cut -d'=' -f2)

# Remove the "-SNAPSHOT" suffix from the current version
NEW_VERSION=${CURRENT_VERSION%-SNAPSHOT}

# Update the gradle.properties file with the new version
awk -v current="$CURRENT_VERSION" -v new="$NEW_VERSION" 'BEGIN {FS=OFS="="} $1 == "pluginVersion" { $2 = new }1' "$GRADLE_PROPERTIES_FILE" > tmpfile && mv tmpfile "$GRADLE_PROPERTIES_FILE"

echo $NEW_VERSION
