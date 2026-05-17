#include "fpslimiter.h"
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#define LOG_TAG "FPSHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Оригинальные функции OpenGL
typedef void (*glXSwapBuffers_t)(void* dpy, void* drawable);
typedef int (*eglSwapBuffers_t)(void* dpy, void* surface);

static glXSwapBuffers_t real_glXSwapBuffers = NULL;
static eglSwapBuffers_t real_eglSwapBuffers = NULL;

static int initialized = 0;


/**
 * Инициализация при загрузке библиотеки
 */
__attribute__((constructor))
static void init_fps_hook() {
    if (initialized)
        return;
    
    LOGI("FPS Hook library loaded");
    
    // Читаем FPS лимит из переменной окружения
    const char* fps_limit_str = getenv("FPS_LIMIT");
    int fps_limit = 0;
    
    if (fps_limit_str != NULL) {
        fps_limit = atoi(fps_limit_str);
    }
    
    if (fps_limit > 0) {
        LOGI("Initializing FPS limiter: %d FPS", fps_limit);
        fps_limiter_init(fps_limit);
    } else {
        LOGI("FPS limiting disabled (FPS_LIMIT not set or 0)");
    }
    
    initialized = 1;
}

/**
 * Hook для glXSwapBuffers (OpenGL X11)
 */
extern "C" void glXSwapBuffers(void* dpy, void* drawable) {
    if (!real_glXSwapBuffers) {
        real_glXSwapBuffers = (glXSwapBuffers_t)dlsym(RTLD_NEXT, "glXSwapBuffers");
        if (!real_glXSwapBuffers) {
            LOGE("Failed to find real glXSwapBuffers");
            return;
        }
        LOGI("glXSwapBuffers hooked successfully");
    }
    
    // Применяем FPS limiting
    fps_limiter_wait();
    
    // Вызываем оригинальную функцию
    real_glXSwapBuffers(dpy, drawable);
}

/**
 * Hook для eglSwapBuffers (OpenGL ES)
 */
extern "C" int eglSwapBuffers(void* dpy, void* surface) {
    if (!real_eglSwapBuffers) {
        real_eglSwapBuffers = (eglSwapBuffers_t)dlsym(RTLD_NEXT, "eglSwapBuffers");
        if (!real_eglSwapBuffers) {
            LOGE("Failed to find real eglSwapBuffers");
            return 0;
        }
        LOGI("eglSwapBuffers hooked successfully");
    }
    
    // Применяем FPS limiting
    fps_limiter_wait();
    
    // Вызываем оригинальную функцию
    real_eglSwapBuffers(dpy, surface);
    
    return 1; // EGL_TRUE
}

// VULKAN HOOKING ВРЕМЕННО ОТКЛЮЧЕН
// Перехват через LD_PRELOAD/dlsym не работает с Wine/DXVK
// Работают только OpenGL хуки

