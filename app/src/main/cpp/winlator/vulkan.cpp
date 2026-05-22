#include <vulkan/vulkan.h>
#include <iostream>
#include <map>
#include <vector>

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <unistd.h>
#include <fstream>
#include <cstdlib>

static void *vulkan_handle = NULL;
static PFN_vkGetInstanceProcAddr gip = NULL;
static bool turbo_mode_active = false;

__attribute__((constructor))
void start() {
    if (!vulkan_handle) {
        vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
    }
}

VkInstance create_instance() {
    VkResult result;
    VkInstance instance;
    VkInstanceCreateInfo create_info = {};

    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(vulkan_handle, "vkCreateInstance");

    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pNext = NULL;
    create_info.flags = 0;
    create_info.pApplicationInfo = NULL;
    create_info.enabledLayerCount = 0;
    create_info.enabledExtensionCount = 0;

    result = createInstance(&create_info, NULL, &instance);

    if (result != VK_SUCCESS)
        __android_log_print(ANDROID_LOG_DEBUG, "GPUInformation", "Failed to create instance: %d", result);

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
        __android_log_print(ANDROID_LOG_DEBUG, "GPUInformation", "Failed to enumerate devices: %d", result);

    return physical_devices;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVersion(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceProperties props = {};
    char *driverVersion;
    VkInstance instance;

    instance = create_instance();
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

    return (env->NewStringUTF(driverVersion));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVendorID(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceProperties props = {};
    uint32_t vendorID;
    VkInstance instance;

    instance = create_instance();
    if (!instance) return 0;

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
    char *renderer;
    VkInstance instance;

    instance = create_instance();
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    for (const auto &pdevice: get_physical_devices(instance)) {
        getPhysicalDeviceProperties(pdevice, &props);
        asprintf(&renderer, "%s", props.deviceName);
    }

    destroyInstance(instance, NULL);

    return (env->NewStringUTF(renderer));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_core_GPUInformation_getMemorySize(JNIEnv *env, jclass obj) {
    VkPhysicalDeviceMemoryProperties props = {};
    long memorySize;
    VkInstance instance;

    instance = create_instance();
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
    jobjectArray extensions;
    VkInstance instance;
    uint32_t extensionCount;
    std::vector<VkExtensionProperties> extensionProperties;

    instance = create_instance();

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

    return extensions;
}

// Adreno GPU Turbo Mode управление
// Основано на libadrenotools и Eden реализации

bool is_adreno_gpu() {
    VkPhysicalDeviceProperties props = {};
    VkInstance instance = create_instance();
    
    if (!instance) return false;
    
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
    // Попытка прямой записи (работает только с root)
    std::ofstream file(path);
    if (file.is_open()) {
        file << value;
        file.close();
        if (!file.fail()) {
            __android_log_print(ANDROID_LOG_DEBUG, "AdrenoTurbo", "Successfully wrote '%s' to %s", value, path);
            return true;
        }
    }
    
    // Попытка через shell с su (требует root)
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

bool set_adreno_turbo_mode(bool enable) {
    if (!is_adreno_gpu()) {
        __android_log_print(ANDROID_LOG_DEBUG, "AdrenoTurbo", "Not Adreno GPU, skipping");
        return false;
    }
    
    __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo", "%s Adreno Turbo Mode", 
                        enable ? "Enabling" : "Disabling");
    
    bool success = false;
    int success_count = 0;
    
    if (enable) {
        // Метод 1: Установить governor в performance
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "performance")) {
            success_count++;
        }
        
        // Метод 2: Установить на максимальный power level (0 = максимум)
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0")) {
            success_count++;
        }
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "0")) {
            success_count++;
        }
        
        // Метод 3: Принудительно включить клоки (может не работать без root)
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "1");
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "1");
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "1");
        
        // Метод 4: Отключить idle timer (GPU не будет засыпать)
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "10000")) {
            success_count++;
        }
        
        // Дополнительные пути для разных устройств
        write_to_sysfs("/sys/devices/platform/kgsl-3d0.0/devfreq/kgsl-3d0.0/governor", "performance");
        write_to_sysfs("/sys/devices/soc/soc:qcom,kgsl-3d0/devfreq/kgsl-3d0/governor", "performance");
        
    } else {
        // Вернуть к нормальному режиму
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "msm-adreno-tz")) {
            success_count++;
        }
        
        // Восстановить автоматическое управление
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "0");
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "0");
        write_to_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "0");
        
        // Восстановить idle timer
        if (write_to_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "80")) {
            success_count++;
        }
        
        // Дополнительные пути
        write_to_sysfs("/sys/devices/platform/kgsl-3d0.0/devfreq/kgsl-3d0.0/governor", "msm-adreno-tz");
        write_to_sysfs("/sys/devices/soc/soc:qcom,kgsl-3d0/devfreq/kgsl-3d0/governor", "msm-adreno-tz");
    }
    
    // Если хотя бы одна операция удалась - считаем успехом
    if (success_count > 0) {
        turbo_mode_active = enable;
        __android_log_print(ANDROID_LOG_INFO, "AdrenoTurbo", 
                          "Turbo mode %s (%d operations succeeded)", 
                          enable ? "enabled" : "disabled", success_count);
        return true;
    }
    
    __android_log_print(ANDROID_LOG_WARN, "AdrenoTurbo", 
                       "Failed to change turbo mode (no root access or unsupported device)");
    return false;
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