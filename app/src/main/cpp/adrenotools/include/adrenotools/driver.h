#pragma once

#include <android/native_window.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ADRENOTOOLS_DRIVER_CUSTOM         1
#define ADRENOTOOLS_DRIVER_FILE_REDIRECT  2
#define ADRENOTOOLS_DRIVER_GPU_MAPPING    4

void *adrenotools_open_libvulkan(void *dpy, void *surface, int *feature_flags,
                                  const char *driver_path, const char *driver_lib_name,
                                  const char *tmp_dir, const char *hook_dir,
                                  const char *custom_dir, const char *name_prefix);

int adrenotools_get_driver_version(const char *driver_path);

#ifdef __cplusplus
}
#endif
