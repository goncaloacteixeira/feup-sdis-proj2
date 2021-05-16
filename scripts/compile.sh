#! /usr/bin/bash

mkdir build

javac -cp .:./lib/log4j-api-2.14.1.jar messages/*.java peer/*.java client/*.java operations/*.java -d build

cp -r resources/ build/resources
cp log4j2.xml build/