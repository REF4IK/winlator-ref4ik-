#include <vulkan/vulkan.h>
#include <iostream>
#include <map>
#include <vector>
#include <string>

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/resource.h>
#include <sched.h>
#include <fstream>
#include <cstdlib>
#include <cstring>
#include <android/api-level.h>
#include "../adrenotools/include/adrenotools/driver.h"

#define LOG_TAG "GPUInformation"
#define VLOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define VLOG_E(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void *vulkan_handle = NULL;
static PFN_vkGetInstanceProcAddr gip = NULL;
static bool turbo_mode_active = false;

static void preload_first_existing(const char **candidates) {
    for (int i = 0; candidates[i]; i++) {
        if (dlopen(candidates[i], RTLD_GLOBAL | RTLD_NOW))
            return;
    }
}

static void preload_vendor_icd_deps() {
    const char *jpeg_candidates[] = {
        "/system/lib64/libjpeg.so",
        "/system_ext/lib64/libjpeg.so",
        "libjpeg.so",
        NULL,
    };
    preload_first_existing(jpeg_candidates);

    const char *crypto_candidates[] = {
        "libcrypto.so",
        NULL,
    };
    preload_first_existing(crypto_candidates);
}

__attribute__((constructor))
void start() {
    preload_vendor_icd_deps();

    if (!vulkan_handle) {
        vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        if (vulkan_handle)
            gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
    }
}

// ============================================================================
// Динамическая загрузка Vulkan-драйвера (адаптировано из Winlator Ludashi)
// Позволяет переключать драйвер Vulkan в зависимости от выбранного драйвера.
// ============================================================================

static char *get_native_library_dir(JNIEnv *env, jobject context) {
    if (!context) return nullptr;
    jclass cls = env->FindClass("com/winlator/cmod/core/AppUtils");
    if (!cls) return nullptr;
    jmethodID mid = env->GetStaticMethodID(cls, "getNativeLibDir",
        "(Landroid/content/Context;)Ljava/lang/String;");
    if (!mid) return nullptr;
    jstring nativeLibDir = (jstring)env->CallStaticObjectMethod(cls, mid, context);
    if (!nativeLibDir) return nullptr;
    return (char *)env->GetStringUTFChars(nativeLibDir, nullptr);
}

static char *get_driver_path(JNIEnv *env, jobject context, const char *driver_name) {
    if (!context || !driver_name) return nullptr;
    jclass contextWrapperClass = env->FindClass("android/content/ContextWrapper");
    if (!contextWrapperClass) return nullptr;
    jmethodID getFilesDir = env->GetMethodID(contextWrapperClass, "getFilesDir", "()Ljava/io/File;");
    if (!getFilesDir) return nullptr;
    jobject filesDirObj = env->CallObjectMethod(context, getFilesDir);
    if (!filesDirObj) return nullptr;
    jclass fileClass = env->GetObjectClass(filesDirObj);
    jmethodID getAbsolutePath = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    if (!getAbsolutePath) return nullptr;
    jstring absolutePath = (jstring)env->CallObjectMethod(filesDirObj, getAbsolutePath);
    if (!absolutePath) return nullptr;
    const char *abs_path = env->GetStringUTFChars(absolutePath, nullptr);
    char *driver_path = nullptr;
    asprintf(&driver_path, "%s/imagefs/contents/adrenotools/%s/", abs_path, driver_name);
    env->ReleaseStringUTFChars(absolutePath, abs_path);
    return driver_path;
}

static char *get_library_name(JNIEnv *env, jobject context, const char *driver_name) {
    if (!context || !driver_name) return nullptr;
    jclass adrenotoolsManager = env->FindClass("com/winlator/cmod/contents/AdrenotoolsManager");
    if (!adrenotoolsManager) return nullptr;
    jmethodID constructor = env->GetMethodID(adrenotoolsManager, "<init>", "(Landroid/content/Context;)V");
    jmethodID getLibraryName = env->GetMethodID(adrenotoolsManager, "getLibraryName",
        "(Ljava/lang/String;)Ljava/lang/String;");
    if (!constructor || !getLibraryName) return nullptr;
    jobject mgrObj = env->NewObject(adrenotoolsManager, constructor, context);
    if (!mgrObj) return nullptr;
    jstring driverName = env->NewStringUTF(driver_name);
    jstring libraryName = (jstring)env->CallObjectMethod(mgrObj, getLibraryName, driverName);
    if (!libraryName) return nullptr;
    return (char *)env->GetStringUTFChars(libraryName, nullptr);
}

static void *init_vulkan_with_driver(JNIEnv *env, jobject context, const char *driver_name) {
    if (!driver_name || strcmp(driver_name, "System") == 0) {
        // Всегда перезагружаем system libvulkan, чтобы сбросить gip с кастомного драйвера
        vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_LOCAL | RTLD_NOW);
        if (vulkan_handle)
            gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
        return vulkan_handle;
    }

    preload_vendor_icd_deps();

    char *driver_path = get_driver_path(env, context, driver_name);
    if (!driver_path) {
        VLOG_E("init_vulkan_with_driver: failed to get driver_path for %s", driver_name);
        return nullptr;
    }

    if (access(driver_path, F_OK) != 0) {
        VLOG_E("init_vulkan_with_driver: driver_path not accessible: %s", driver_path);
        free(driver_path);
        if (!vulkan_handle) {
            vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_LOCAL | RTLD_NOW);
            if (vulkan_handle)
                gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
        }
        return vulkan_handle;
    }

    char *library_name = get_library_name(env, context, driver_name);
    char *native_library_dir = get_native_library_dir(env, context);
    if (!library_name || !native_library_dir) {
        VLOG_E("init_vulkan_with_driver: missing library_name or native_lib_dir");
        free(driver_path);
        if (library_name) free(library_name);
        if (native_library_dir) free(native_library_dir);
        return nullptr;
    }

    char *tmpdir = nullptr;
    asprintf(&tmpdir, "%stemp", driver_path);
    mkdir(tmpdir, S_IRWXU | S_IRWXG);

    VLOG("init_vulkan_with_driver: driver=%s path=%s lib=%s nativeLibDir=%s tmp=%s",
         driver_name, driver_path, library_name, native_library_dir, tmpdir);

    int featureFlags = ADRENOTOOLS_DRIVER_CUSTOM;
    const char *redirectDir = getenv("ADRENOTOOLS_REDIRECT_DIR");
    if (redirectDir && redirectDir[0] != '\0') {
        featureFlags |= ADRENOTOOLS_DRIVER_FILE_REDIRECT;
    }

    VLOG("init_vulkan_with_driver: calling adrenotools_open_libvulkan(dlopenMode=%d, featureFlags=%d, tmpLibDir=%s, hookLibDir=%s, customDriverDir=%s, customDriverName=%s, fileRedirectDir=%s)",
         RTLD_NOW, featureFlags, tmpdir, native_library_dir, driver_path, library_name,
         (redirectDir && redirectDir[0] != '\0') ? redirectDir : "null");

    void *handle = adrenotools_open_libvulkan(
        RTLD_NOW,
        featureFlags,
        tmpdir,
        native_library_dir,
        driver_path,
        library_name,
        (redirectDir && redirectDir[0] != '\0') ? redirectDir : nullptr,
        nullptr);

    if (!handle) {
        VLOG_E("init_vulkan_with_driver: adrenotools_open_libvulkan failed, fallback to system");
        if (!vulkan_handle) {
            vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_LOCAL | RTLD_NOW);
            if (vulkan_handle)
                gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
        }
        handle = vulkan_handle;
    } else {
        vulkan_handle = handle;
        gip = (PFN_vkGetInstanceProcAddr)dlsym(handle, "vkGetInstanceProcAddr");
        VLOG("init_vulkan_with_driver: SUCCESS handle=%p", handle);
    }

    free(driver_path);
    free(library_name);
    free(native_library_dir);
    free(tmpdir);

    return handle;
}

// ============================================================================
// Создание Vulkan instance — с опциональным драйвером
// ============================================================================

static VkInstance create_instance_common(JNIEnv *env, jobject context, jstring driverName) {
    VkResult result;
    VkInstance instance;
    VkInstanceCreateInfo create_info = {};

    void *local_handle = nullptr;

    if (driverName != nullptr && context != nullptr) {
        const char *driver_name = env->GetStringUTFChars(driverName, nullptr);
        if (driver_name && strlen(driver_name) > 0 && strcmp(driver_name, "System") != 0) {
            local_handle = init_vulkan_with_driver(env, context, driver_name);
        }
        env->ReleaseStringUTFChars(driverName, driver_name);
    }

    // Если не загрузили кастомный — всегда сбрасываем на system
    if (!local_handle) {
        vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        if (vulkan_handle)
            gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
        local_handle = vulkan_handle;
    }

    if (!local_handle || !gip) {
        VLOG_E("create_instance: no vulkan handle available");
        return VK_NULL_HANDLE;
    }

    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(local_handle, "vkCreateInstance");
    if (!createInstance)
        createInstance = (PFN_vkCreateInstance)gip(nullptr, "vkCreateInstance");

    if (!createInstance) {
        VLOG_E("create_instance: vkCreateInstance not found");
        return VK_NULL_HANDLE;
    }

    int apiLevel = android_get_device_api_level();

    VkApplicationInfo app_info = {};
    app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app_info.pApplicationName = "Winlator";
    app_info.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    app_info.pEngineName = "Winlator";
    app_info.engineVersion = VK_MAKE_VERSION(1, 0, 0);

    if (apiLevel > 32) {
        app_info.apiVersion = VK_API_VERSION_1_0;
    } else {
        PFN_vkEnumerateInstanceVersion enumerateInstanceVersion =
            (PFN_vkEnumerateInstanceVersion)dlsym(local_handle, "vkEnumerateInstanceVersion");
        if (enumerateInstanceVersion)
            enumerateInstanceVersion(&app_info.apiVersion);
        else
            app_info.apiVersion = VK_API_VERSION_1_0;
    }

    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pNext = NULL;
    create_info.flags = 0;
    create_info.pApplicationInfo = &app_info;
    create_info.enabledLayerCount = 0;
    create_info.enabledExtensionCount = 0;

    result = createInstance(&create_info, NULL, &instance);

    if (result != VK_SUCCESS) {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to create instance: %d", result);
        return VK_NULL_HANDLE;
    }

    return instance;
}

std::vector<VkPhysicalDevice> get_physical_devices(VkInstance instance) {
    VkResult result = VK_ERROR_UNKNOWN;
    std::vector<VkPhysicalDevice> physical_devices;
    uint32_t deviceCount;

    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)gip(instance, "vkEnumeratePhysicalDevices");

    enumeratePhysicalDevices(instance, &deviceCount, NULL);
    physical_devices.resize(deviceCount);

    if (deviceCount > 0)
        result = enumeratePhysicalDevices(instance, &deviceCount, physical_devices.data());

    if (result != VK_SUCCESS)
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to enumerate devices: %d", result);

    return physical_devices;
}

// ============================================================================
// Native-методы GPUInformation — перегрузки БЕЗ драйвера (обратная совместимость)
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVersion(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceProperties props = {};
    char *driverVersion = nullptr;
    VkInstance instance = create_instance_common(env, nullptr, nullptr);

    if (instance == VK_NULL_HANDLE) return env->NewStringUTF("Unknown");

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t vk_driver_major = VK_VERSION_MAJOR(props.driverVersion);
        uint32_t vk_driver_minor = VK_VERSION_MINOR(props.driverVersion);
        uint32_t vk_driver_patch = VK_VERSION_PATCH(props.driverVersion);
        asprintf(&driverVersion, "%d.%d.%d", vk_driver_major, vk_driver_minor,
                 vk_driver_patch);
    }

    destroyInstance(instance, NULL);

    if (!driverVersion) return env->NewStringUTF("Unknown");
    return env->NewStringUTF(driverVersion);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVendorID(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceProperties props = {};
    uint32_t vendorID = 0;
    VkInstance instance = create_instance_common(env, nullptr, nullptr);
    if (instance == VK_NULL_HANDLE) return 0;

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    auto devices = get_physical_devices(instance);
    if (devices.empty()) {
        destroyInstance(instance, NULL);
        return 0;
    }

    getPhysicalDeviceProperties(devices[0], &props);
    vendorID = props.vendorID;

    destroyInstance(instance, NULL);

    return (jint)vendorID;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getRenderer(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceProperties props = {};
    char *renderer = nullptr;
    VkInstance instance = create_instance_common(env, nullptr, nullptr);
    if (instance == VK_NULL_HANDLE) return env->NewStringUTF("Unknown");

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        asprintf(&renderer, "%s", props.deviceName);
    }

    destroyInstance(instance, NULL);

    if (!renderer) return env->NewStringUTF("Unknown");
    return env->NewStringUTF(renderer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_core_GPUInformation_getMemorySize(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceMemoryProperties props = {};
    long memorySize = 0;
    VkInstance instance = create_instance_common(env, nullptr, nullptr);
    if (instance == VK_NULL_HANDLE) return 0;

    PFN_vkGetPhysicalDeviceMemoryProperties getPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties)gip(instance, "vkGetPhysicalDeviceMemoryProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice : get_physical_devices(instance)) {
        getPhysicalDeviceMemoryProperties(pdevice, &props);
        memorySize = props.memoryHeaps[0].size;
    }

    destroyInstance(instance, NULL);
    return memorySize / 1048576;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_GPUInformation_enumerateExtensions(JNIEnv *env, jclass obj) {
    jobjectArray extensions = nullptr;
    VkInstance instance = create_instance_common(env, nullptr, nullptr);
    if (instance == VK_NULL_HANDLE) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    uint32_t extensionCount = 0;
    std::vector<VkExtensionProperties> extensionProperties;

    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)gip(instance, "vkEnumerateDeviceExtensionProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice : get_physical_devices(instance)) {
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, NULL);
        extensionProperties.resize(extensionCount);
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, extensionProperties.data());
        extensions = (jobjectArray)env->NewObjectArray(extensionCount, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        int index = 0;
        for (const auto &extensionProperty : extensionProperties) {
            env->SetObjectArrayElement(extensions, index, env->NewStringUTF(extensionProperty.extensionName));
            index++;
        }
    }

    destroyInstance(instance, NULL);

    if (!extensions) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }
    return extensions;
}

// ============================================================================
// Native-методы GPUInformation — перегрузки С драйвером (динамические расширения)
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVersionWithDriver(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    char *driverVersion = nullptr;
    VkInstance instance = create_instance_common(env, context, driverName);
    if (instance == VK_NULL_HANDLE) return env->NewStringUTF("Unknown");

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t major = VK_VERSION_MAJOR(props.driverVersion);
        uint32_t minor = VK_VERSION_MINOR(props.driverVersion);
        uint32_t patch = VK_VERSION_PATCH(props.driverVersion);
        asprintf(&driverVersion, "%d.%d.%d", major, minor, patch);
    }

    destroyInstance(instance, NULL);

    if (!driverVersion) return env->NewStringUTF("Unknown");
    return env->NewStringUTF(driverVersion);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVendorIDWithDriver(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    uint32_t vendorID = 0;
    VkInstance instance = create_instance_common(env, context, driverName);
    if (instance == VK_NULL_HANDLE) return 0;

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    auto devices = get_physical_devices(instance);
    if (!devices.empty()) {
        getPhysicalDeviceProperties(devices[0], &props);
        vendorID = props.vendorID;
    }

    destroyInstance(instance, NULL);
    return (jint)vendorID;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getRendererWithDriver(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    char *renderer = nullptr;
    VkInstance instance = create_instance_common(env, context, driverName);
    if (instance == VK_NULL_HANDLE) return env->NewStringUTF("Unknown");

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        asprintf(&renderer, "%s", props.deviceName);
    }

    destroyInstance(instance, NULL);

    if (!renderer) return env->NewStringUTF("Unknown");
    return env->NewStringUTF(renderer);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_GPUInformation_enumerateExtensionsWithDriver(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    jobjectArray extensions = nullptr;
    VkInstance instance = create_instance_common(env, context, driverName);
    if (instance == VK_NULL_HANDLE) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    uint32_t extensionCount = 0;
    std::vector<VkExtensionProperties> extensionProperties;

    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)gip(instance, "vkEnumerateDeviceExtensionProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice : get_physical_devices(instance)) {
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, NULL);
        extensionProperties.resize(extensionCount);
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, extensionProperties.data());
        extensions = (jobjectArray)env->NewObjectArray(extensionCount, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        int index = 0;
        for (const auto &extensionProperty : extensionProperties) {
            env->SetObjectArrayElement(extensions, index, env->NewStringUTF(extensionProperty.extensionName));
            index++;
        }
    }

    destroyInstance(instance, NULL);

    if (!extensions) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }
    return extensions;
}

// ============================================================================
// Adreno GPU Turbo Mode управление
// ============================================================================

bool is_adreno_gpu() {
    VkPhysicalDeviceProperties props = {};
    VkInstance instance = create_instance_common(nullptr, nullptr, nullptr);

    if (instance == VK_NULL_HANDLE) return false;

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties =
        (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    bool is_adreno = false;
    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        std::string deviceName(props.deviceName);
        if (deviceName.find("Adreno") != std::string::npos) {
            is_adreno = true;
            break;
        }
    }

    destroyInstance(instance, NULL);
    return is_adreno;
}

bool write_to_sysfs(const char* path, const char* value) {
    std::ofstream file(path);
    if (file.is_open()) {
        file << value;
        file.close();
        if (!file.fail()) {
            __android_log_print(ANDROID_LOG_DEBUG, "AdrenoTurbo", "Successfully wrote '%s' to %s", value, path);
            return true;
        }
    }

    char command[512];
    snprintf(command, sizeof(command), "su -c 'echo %s > %s'", value, path);

    int result = system(command);
    if (result == 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "AdrenoTurbo", "Successfully wrote '%s' to %s via su", value, path);
        return true;
    }

    __android_log_print(ANDROID_LOG_DEBUG, "AdrenoTurbo", "Failed to write to: %s (no root access)", path);
    return false;
}

// CPU performance tweaks без root — поднимает приоритет, привязка к big-ядрам
static void apply_cpu_performance_tweaks(bool enable) {
    pid_t tid = gettid();

    if (enable) {
        // 1. Максимальный приоритет (-20)
        setpriority(PRIO_PROCESS, tid, -20);

        // 2. Попытка SCHED_FIFO (realtime)
        struct sched_param param = {};
        param.sched_priority = sched_get_priority_max(SCHED_FIFO);
        sched_setscheduler(tid, SCHED_FIFO, &param);

        // 3. Привязка к big-ядрам (4-7 на 8-ядерных типичных SoC)
        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        for (int i = 4; i <= 7; i++) CPU_SET(i, &cpuset);
        sched_setaffinity(tid, sizeof(cpuset), &cpuset);

        __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo",
            "CPU tweaks: priority=-20, SCHED_FIFO, big-cores affinity");
    } else {
        // Сброс приоритета в нормаль
        setpriority(PRIO_PROCESS, tid, 0);

        struct sched_param param = {};
        param.sched_priority = 0;
        sched_setscheduler(tid, SCHED_NORMAL, &param);

        // Разрешаем все ядра
        cpu_set_t cpuset;
        CPU_ZERO(&cpuset);
        for (int i = 0; i < 8; i++) CPU_SET(i, &cpuset);
        sched_setaffinity(tid, sizeof(cpuset), &cpuset);

        __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo",
            "CPU tweaks: reset to normal");
    }
}

bool set_adreno_turbo_mode(bool enable) {
    // GPU: adrenotools — отключает power management на GPU
    __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo", "%s Adreno Turbo Mode via adrenotools",
                        enable ? "Enabling" : "Disabling");
    adrenotools_set_turbo(enable);

    // CPU: приоритет, affinity, планировщик — без root
    apply_cpu_performance_tweaks(enable);

    turbo_mode_active = enable;
    __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo",
                       "Turbo mode %s via adrenotools", enable ? "enabled" : "disabled");
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_GPUInformation_setTurboMode(JNIEnv *env, jclass obj, jboolean enable) {
    return set_adreno_turbo_mode(enable);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_GPUInformation_isTurboModeActive(JNIEnv *env, jclass obj) {
    return turbo_mode_active;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_GPUInformation_isAdrenoGPU(JNIEnv *env, jclass obj) {
    return is_adreno_gpu();
}