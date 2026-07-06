#include <jni.h>
#include <android/native_window_jni.h>
#include "VulkanRendererContext.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeInit(
    JNIEnv* env, jobject, jobject surface, jint w, jint h,
    jstring jDriverPath, jstring jLibraryName, jstring jNativeLibDir)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (!win) return 0;
    // Рендеринг всегда через системный Vulkan loader — adrenotools
    // используется ТОЛЬКО для GPUInformation (расширения, версия, вендор)
    try { return reinterpret_cast<jlong>(new VulkanRendererContext(win, w, h, nullptr)); }
    catch (...) {
        ANativeWindow_release(win);
        return 0;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeResize(JNIEnv*, jobject, jlong h, jint w, jint ht) {
    auto* r=reinterpret_cast<VulkanRendererContext*>(h); if (r) r->onSurfaceResized(w,ht);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete reinterpret_cast<VulkanRendererContext*>(h);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeUpdateWindowContent(
    JNIEnv* env, jobject, jlong handle, jlong id, jobject buf, jshort w, jshort h, jshort stride, jint x, jint y)
{
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r||!buf) return;
    void* px=env->GetDirectBufferAddress(buf);
    if (px && env->GetDirectBufferCapacity(buf)>=(jlong)w*h*4)
        r->updateWindowContent(id,px,w,h,stride,x,y);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeUpdateWindowContentAHB(
    JNIEnv*, jobject, jlong handle, jlong id, jlong ahbPtr, jshort w, jshort h, jint x, jint y)
{
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle);
    if (r&&ahbPtr) r->updateWindowContentAHB(id,reinterpret_cast<AHardwareBuffer*>(ahbPtr),w,h,x,y);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetTransform(
    JNIEnv*, jobject, jlong handle, jfloat ox, jfloat oy, jfloat sx, jfloat sy)
{
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle); if (r) r->setTransform(ox,oy,sx,sy);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetPointerPos(JNIEnv*, jobject, jlong handle, jshort x, jshort y) {
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle); if (r) r->updatePointerPosition(x,y);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetCursorVisible(JNIEnv*, jobject, jlong handle, jboolean v) {
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle); if (r) r->setCursorVisible(v);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeUpdateCursorImage(
    JNIEnv* env, jobject, jlong handle, jobject buf, jshort w, jshort h, jshort hotX, jshort hotY)
{
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r||!buf) return;
    void* px=env->GetDirectBufferAddress(buf);
    if (px && env->GetDirectBufferCapacity(buf)>=(jlong)w*h*4)
        r->updateCursorImage(px,w,h,hotX,hotY);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetRenderList(
    JNIEnv* env, jobject, jlong handle, jlongArray jids, jintArray jxs, jintArray jys, jint count)
{
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r||count<=0) return;

    jlong* ids=(jlong*)env->GetPrimitiveArrayCritical(jids,nullptr);
    jint*  xs =(jint*) env->GetPrimitiveArrayCritical(jxs, nullptr);
    jint*  ys =(jint*) env->GetPrimitiveArrayCritical(jys, nullptr);
    r->setRenderList(reinterpret_cast<const int64_t*>(ids),xs,ys,count);
    env->ReleasePrimitiveArrayCritical(jys, ys,  JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(jxs, xs,  JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(jids,ids, JNI_ABORT);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeRemoveWindow(JNIEnv*, jobject, jlong handle, jlong id) {
    auto* r=reinterpret_cast<VulkanRendererContext*>(handle); if (r) r->removeWindow(id);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeInitScanout(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->initScanout();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeDestroyScanout(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->destroyScanout();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetScanoutDisabled(JNIEnv*, jobject, jlong handle, jboolean disabled) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) {
        r->scanoutDisabled.store(disabled == JNI_TRUE);
        if (disabled && r->scanoutActive.load()) {
            r->destroyScanout();
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeScanoutSetBuffer(
    JNIEnv*, jobject, jlong handle, jlong ahbPtr, jint x, jint y, jint w, jint h, jint fenceFd)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r && ahbPtr) r->scanoutSetBuffer(reinterpret_cast<AHardwareBuffer*>(ahbPtr), x, y, w, h, (int)fenceFd);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeScanoutSetCursorImage(
    JNIEnv* env, jobject, jlong handle, jobject buf, jshort w, jshort h, jshort stride)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r || !buf) return;
    void* px = env->GetDirectBufferAddress(buf);
    if (px && env->GetDirectBufferCapacity(buf) >= (jlong)w*h*4)
        r->scanoutSetCursorImage(px, w, h, stride);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeScanoutSetCursorPos(
    JNIEnv*, jobject, jlong handle, jshort x, jshort y, jshort hotX, jshort hotY)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->scanoutSetCursorPos(x, y, hotX, hotY);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeIsScanoutActive(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    return r ? (jboolean)r->scanoutActive.load() : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeScanoutSetDst(
    JNIEnv*, jobject, jlong handle, jint x, jint y, jint w, jint h)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->scanoutSetDst(x, y, w, h);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetScanoutWindow(
    JNIEnv* env, jobject, jlong handle, jobject gameSurface, jobject cursorSurface)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r) return;
    ANativeWindow* gw = ANativeWindow_fromSurface(env, gameSurface);
    ANativeWindow* cw = ANativeWindow_fromSurface(env, cursorSurface);
    if (!gw || !cw) {
        if (gw) ANativeWindow_release(gw);
        if (cw) ANativeWindow_release(cw);
        r->initScanout();
        return;
    }
    r->initScanoutFromWindows(gw, cw);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetVerboseLog(JNIEnv*, jobject, jlong handle, jboolean v) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->setVerboseLog((bool)v);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeDumpRendererInfo(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->dumpRendererInfo();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeIsGameFrameDelivered(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    return r ? (jboolean)r->gameFrameDelivered.load() : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetFilterMode(JNIEnv*, jobject, jlong handle, jint mode) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->setFilterMode((int)mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetSwapRB(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->setSwapRB(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetPresentMode(JNIEnv*, jobject, jlong handle, jint mode) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->setPresentMode((VkPresentModeKHR)mode);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeGetSupportedPresentModes(JNIEnv* env, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r) return env->NewIntArray(0);
    auto modes = r->getSupportedPresentModes();
    jintArray arr = env->NewIntArray((jsize)modes.size());
    if (!modes.empty()) env->SetIntArrayRegion(arr,0,(jsize)modes.size(),modes.data());
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeDetachSurface(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->detachSurface();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeReattachSurface(JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r || !surface) return JNI_FALSE;
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (!win) return JNI_FALSE;
    bool ok = r->reattachSurface(win);
    if (ok && r->scanoutActive.load()) {
        r->destroyScanout();
    }
    return (jboolean)ok;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeSetEffects(
    JNIEnv* env, jobject, jlong handle, jintArray jtypes, jfloatArray jparams, jint count)
{
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (!r || count <= 0) { if (r) r->clearEffects(); return; }

    jint* types = (jint*)env->GetPrimitiveArrayCritical(jtypes, nullptr);
    jfloat* params = (jfloat*)env->GetPrimitiveArrayCritical(jparams, nullptr);

    std::vector<EffectEntry> entries(count);
    for (int i = 0; i < count; i++) {
        entries[i].type = (EffectType)types[i];
        memcpy(entries[i].params, params + i * 8, sizeof(float) * 8);
    }

    env->ReleasePrimitiveArrayCritical(jparams, params, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(jtypes, types, JNI_ABORT);

    r->setEffects(entries.data(), count);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeClearEffects(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    if (r) r->clearEffects();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_VulkanRenderer_nativeHasEffects(JNIEnv*, jobject, jlong handle) {
    auto* r = reinterpret_cast<VulkanRendererContext*>(handle);
    return r ? (jboolean)r->hasEffects() : JNI_FALSE;
}
