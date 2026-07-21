#pragma once

#include "core/instance.hpp"

#include <vulkan/vulkan_core.h>

#include <cstdint>
#include <memory>

namespace LSFG::Core {

    class Image;

    ///
    /// C++ wrapper class for a Vulkan device.
    ///
    /// This class manages the lifetime of a Vulkan device.
    ///
    class Device {
    public:
        ///
        /// Create the device.
        ///
        /// @param instance Vulkan instance
        /// @param deviceUUID The UUID of the Vulkan device to use.
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Device(const Instance& instance, uint64_t deviceUUID);

        ///
        /// Wrap an existing VkDevice (single-device mode, no ownership).
        /// The device will NOT be destroyed when this object is freed.
        ///
        /// @param existingDevice The Vulkan device to wrap.
        /// @param physDev The physical device associated with this device.
        /// @param computeFamilyIdx Queue family index for compute.
        /// @param computeQueue Queue handle for compute.
        /// @param nullDescriptorSupported Whether null descriptors are supported.
        ///
        Device(VkDevice existingDevice, VkPhysicalDevice physDev,
               uint32_t computeFamilyIdx, VkQueue computeQueue,
               bool nullDescriptorSupported = false);

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->device; }
        /// Get the physical device associated with this logical device.
        [[nodiscard]] VkPhysicalDevice getPhysicalDevice() const { return this->physicalDevice; }
        /// Get the compute queue family index.
        [[nodiscard]] uint32_t getComputeFamilyIdx() const { return this->computeFamilyIdx; }
        /// Get the compute queue.
        [[nodiscard]] VkQueue getComputeQueue() const { return this->computeQueue; }
        /// Whether the device supports null image descriptors.
        [[nodiscard]] bool supportsNullDescriptor() const { return this->nullDescriptorSupported; }
        /// Valid sampled image used for optional bindings when nullDescriptor is absent.
        [[nodiscard]] const Image& getFallbackDescriptorImage() const;

        // Trivially copyable, moveable and destructible
        Device(const Core::Device&) noexcept = default;
        Device& operator=(const Core::Device&) noexcept = default;
        Device(Device&&) noexcept = default;
        Device& operator=(Device&&) noexcept = default;
        ~Device() = default;
    private:
        std::shared_ptr<VkDevice> device;
        VkPhysicalDevice physicalDevice{};

        uint32_t computeFamilyIdx{0};

        VkQueue computeQueue{};
        bool nullDescriptorSupported{false};
        bool ownsHandle_ = true;
        std::shared_ptr<Image> fallbackDescriptorImage;
    };

}
