#pragma once

#include <vulkan/vulkan_core.h>

#include <cstdint>
#include <memory>

namespace Mini {

    ///
    /// C++ wrapper class for a Vulkan command pool.
    ///
    /// This class manages the lifetime of a Vulkan command pool.
    ///
    class CommandPool {
    public:
        CommandPool() noexcept = default;

        ///
        /// Create the command pool.
        ///
        /// @param device Vulkan device
        /// @param graphicsFamilyIdx Index of the graphics queue family
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        CommandPool(VkDevice device, uint32_t graphicsFamilyIdx);

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->commandPool; }

        /// Trivially copyable, moveable and destructible
        CommandPool(const CommandPool&) noexcept = default;
        CommandPool& operator=(const CommandPool&) noexcept = default;
        CommandPool(CommandPool&&) noexcept = default;
        CommandPool& operator=(CommandPool&&) noexcept = default;
        ~CommandPool() = default;
    private:
        std::shared_ptr<VkCommandPool> commandPool;
    };

}
