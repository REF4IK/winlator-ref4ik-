#pragma once

#ifdef __cplusplus
extern "C" {
#endif

void *adrenotools_open_libvulkan(void *dpy, void *surface, int *feature_flags,
                                  const char *driver_path, const char *driver_lib_name,
                                  const char *tmp_dir, const char *hook_dir,
                                  const char *custom_dir, const char *name_prefix);

int adrenotools_get_driver_version(const char *driver_path);

#ifdef __cplusplus
}
#endif
