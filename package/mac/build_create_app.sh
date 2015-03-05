#!/bin/bash

cd ../../
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/1.jar

# edit url
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=0.1.1-SNAPSHOT \
    -Bmac.CFBundleIdentifier=io.bitsquare \
    -Bmac.CFBundleName=Bitsquare \
    -Bicon=BitsquareLICENSE.icns \
    -Bruntime="$JAVA_HOME/../../" \
    -native dmg \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles gui/updatefx/builds/processed/1.jar \
    -appclass io.bitsquare.app.gui.BitsquareAppMain \
    -outfile Bitsquare
    
cd package/mac