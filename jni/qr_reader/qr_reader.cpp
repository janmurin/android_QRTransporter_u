//
// Created by jmurin on 17.3.2016.
//
#include <jni.h>
#include <string.h>

extern "C"{
JNIEXPORT jstring JNICALL
Java_sk_jmurin_android_qrtransporter_ColorQRAnalyzer_readQR(JNIEnv *env, jobject instance, jlong matAddrRgba) {

    return env->NewStringUTF("hello world");
}
//
//
//
//JNIEXPORT jstring JNICALL
//Java_sk_jmurin_android_opencvapp4_ReadQRActivity_hello(JNIEnv *env, jobject obj){
//    return env->NewStringUTF("Hello from JNI!");
//}

}

