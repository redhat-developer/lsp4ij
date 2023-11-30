#!/bin/bash

# Path to your gradle.properties file
GRADLE_PROPERTIES_FILE="gradle.properties"

# Read the current version from gradle.properties, ignoring spaces around the equals sign
CURRENT_VERSION=$(grep -E "pluginVersion\s*=\s*" "$GRADLE_PROPERTIES_FILE" | sed -E 's/.*=\s*([^[:space:]]*).*/\1/')

# Extract the version part before any hyphen
BASE_VERSION=$(echo "$CURRENT_VERSION" | awk -F'-' '{print $1}')

# Replace any suffix following a hyphen with a timestamp in the format YYYYMMDD-HHmmSS
TIMESTAMP=$(date +'%Y%m%d-%H%M%S')
NEW_VERSION="${BASE_VERSION}-$TIMESTAMP"

# Use awk to update the gradle.properties file, allowing spaces around the equals sign
awk -v new_version="$NEW_VERSION" '/pluginVersion\s*=\s*/{sub(/=.*/, "=" new_version)}1' "$GRADLE_PROPERTIES_FILE" > tmpfile && mv tmpfile "$GRADLE_PROPERTIES_FILE"

echo $NEW_VERSION
