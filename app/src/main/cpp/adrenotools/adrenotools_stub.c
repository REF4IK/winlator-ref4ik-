#include <android/log.h>
#define LOG_TAG "adrenotools_stub"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Stub implementations for adrenotools functions
// These are only used when the real adrenotools library is not available

void *adrenotools_open_libvulkan(void *dpy, void *surface, int *feature_flags,
                                  const char *driver_path, const char *driver_lib_name,
                                  const char *tmp_dir, const char *hook_dir,
                                  const char *custom_dir, const char *name_prefix) {
    LOGW("adrenotools_stub: adrenotools_open_libvulkan called - returning NULL");
    return NULL;
}

int adrenotools_get_driver_version(const char *driver_path) {
    LOGW("adrenotools_stub: adrenotools_get_driver_version called - returning 0");
    return 0;
}
