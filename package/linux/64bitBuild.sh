﻿#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit version
version=0.4.9.5

jarFile="/media/sf_vm_shared_ubuntu/Bitsquare-$version.jar"
jdkfixFile="/media/sf_vm_shared_ubuntu/jdkfix-$version.jar"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BjvmOptions=-Xbootclasspath/a:"jdkfix-$version.jar":"../runtime/lib/ext/jfxrt.jar" \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Internet \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles $jarFile:$jdkfixFile \
    -srcfiles package/linux/LICENSE \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare

# uncomment because the build VM does not support alien
#sudo alien -r -c -k gui/deploy/bundles/bitsquare-$version.deb

mv "gui/deploy/bundles/bitsquare-$version.deb" "/media/sf_vm_shared_ubuntu/Bitsquare-64bit-$version.deb"
#mv "bitsquare-$version-1.x86_64.rpm" "/media/sf_vm_shared_ubuntu/Bitsquare-64bit-$version.rpm"
rm -r gui/deploy/

cd package/linux