#include <jni.h>

extern "C"
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_core_PatchElf_createElfObject(JNIEnv *env, jobject thiz, jstring path) {
    // TODO: implement createElfObject()
    return 0;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_destroyElfObject(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement destroyElfObject()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_isChanged(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement isChanged()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getInterpreter(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement getInterpreter()
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_setInterpreter(JNIEnv *env, jobject thiz, jlong object_ptr,
                                               jstring interpreter) {
    // TODO: implement setInterpreter()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getOsAbi(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement getOsAbi()
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_replaceOsAbi(JNIEnv *env, jobject thiz, jlong object_ptr,
                                             jstring os_abi) {
    // TODO: implement replaceOsAbi()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getSoName(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement getSoName()
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_replaceSoName(JNIEnv *env, jobject thiz, jlong object_ptr,
                                              jstring so_name) {
    // TODO: implement replaceSoName()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_PatchElf_getRPath(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement getRPath()
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_addRPath(JNIEnv *env, jobject thiz, jlong object_ptr,
                                         jstring rpath) {
    // TODO: implement addRPath()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_removeRPath(JNIEnv *env, jobject thiz, jlong object_ptr,
                                            jstring rpath) {
    // TODO: implement removeRPath()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_PatchElf_getNeeded(JNIEnv *env, jobject thiz, jlong object_ptr) {
    // TODO: implement getNeeded()
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_addNeeded(JNIEnv *env, jobject thiz, jlong object_ptr,
                                          jstring needed) {
    // TODO: implement addNeeded()
    return JNI_FALSE;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_removeNeeded(JNIEnv *env, jobject thiz, jlong object_ptr,
                                             jstring needed) {
    // TODO: implement removeNeeded()
    return JNI_FALSE;
}