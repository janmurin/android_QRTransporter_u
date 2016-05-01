#include <jni.h>

JNIEXPORT jstring JNICALL Java_sk_jmurin_android_qrtransporter_decoding_ColorQRAnalyzer_getMessage
        (JNIEnv *env, jobject thisObj) {
    if(getCislo()==23){
        return (*env)->NewStringUTF(env, "Hello from native code! cislo je 23");
    }else{
        return (*env)->NewStringUTF(env, "Hello from native code!");
    }

}

int getCislo(){
    return 23;
}