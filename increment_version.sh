#!/bin/bash

# Path to your gradle.properties file
GRADLE_PROPERTIES_FILE="gradle.properties"

# Read the current version from gradle.properties
CURRENT_VERSION=$(grep "pluginVersion=" "$GRADLE_PROPERTIES_FILE" | cut -d'=' -f2)

# Extract version parts
IFS="-" read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
IFS="." read -ra VERSION_NUM <<< "${VERSION_PARTS[0]}"

# Increment the last digit
((VERSION_NUM[2]++))

# Assemble the new version
NEW_VERSION="${VERSION_NUM[0]}.${VERSION_NUM[1]}.${VERSION_NUM[2]}-SNAPSHOT"

# Use awk to update the gradle.properties file
awk -v new_version="$NEW_VERSION" '/pluginVersion=/{sub(/=.*/, "=" new_version)}1' "$GRADLE_PROPERTIES_FILE" > tmpfile && mv tmpfile "$GRADLE_PROPERTIES_FILE"

echo $NEW_VERSION
