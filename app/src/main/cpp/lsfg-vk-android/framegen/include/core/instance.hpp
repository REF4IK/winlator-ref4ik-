#pragma once

#include <vulkan/vulkan_core.h>

#include <memory>

namespace LSFG::Core {

    ///
    /// C++ wrapper class for a Vulkan instance.
    ///
    /// This class manages the lifetime of a Vulkan instance.
    ///
    class Instance {
    public:
        ///
        /// Create the instance.
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Instance();

        ///
        /// Wrap an existing VkInstance (single-device mode).
        /// The instance will NOT be destroyed when this object is freed.
        ///
        /// @param existingInstance The Vulkan instance to wrap.
        ///
        explicit Instance(VkInstance existingInstance);

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return this->instance ? *this->instance : VK_NULL_HANDLE; }

        /// Trivially copyable, moveable and destructible
        Instance(const Instance&) noexcept = default;
        Instance& operator=(const Instance&) noexcept = default;
        Instance(Instance&&) noexcept = default;
        Instance& operator=(Instance&&) noexcept = default;
        ~Instance() = default;
    private:
        std::shared_ptr<VkInstance> instance;
        bool ownsHandle_ = true;
    };

}
