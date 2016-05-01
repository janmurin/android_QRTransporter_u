#!/bin/bash
ndk-build
lib="libcolorQRreader.so"
dirPath="/home/jmurin/Desktop/bakalarka/app/QRTransporter"
cp -vf $dirPath/libs/arm64-v8a/$lib $dirPath/app/src/main/jniLibs/arm64-v8a
cp -vf $dirPath/libs/armeabi/$lib $dirPath/app/src/main/jniLibs/armeabi
cp -vf $dirPath/libs/armeabi-v7a/$lib $dirPath/app/src/main/jniLibs/armeabi-v7a
cp -vf $dirPath/libs/mips/$lib $dirPath/app/src/main/jniLibs/mips
cp -vf $dirPath/libs/mips64/$lib $dirPath/app/src/main/jniLibs/mips64
cp -vf $dirPath/libs/x86/$lib $dirPath/app/src/main/jniLibs/x86
cp -vf $dirPath/libs/x86_64/$lib $dirPath/app/src/main/jniLibs/x86_64