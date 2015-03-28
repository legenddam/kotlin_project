#!/bin/bash

cd ../../

set -e

# Extract the version number. buildVersion is used for ever increasing integer at UpdateFX. fullVersion contains major and minor version + buildVersion 
buildVersion=$( sed -n 's/^.*final int BUILD_VERSION = //p' core/src/main/java/io/bitsquare/app/UpdateProcess.java )
# remove trailing;
buildVersion="${buildVersion:0:${#buildVersion}-1}"
echo buildVersion = $buildVersion

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp core/target/shaded.jar core/updatefx/builds/$buildVersion.jar
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ core/updatefx

cd package/mac