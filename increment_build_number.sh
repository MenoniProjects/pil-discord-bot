#!/bin/sh

bn=$(head -n 1 .build_number)
bn=$((bn+1))
echo "$bn" > .build_number
echo "build number increased to $bn"
sed -i -e "s/<build.number>.*<\/build.number>/<build.number>b$bn<\/build.number>/g" pom.xml