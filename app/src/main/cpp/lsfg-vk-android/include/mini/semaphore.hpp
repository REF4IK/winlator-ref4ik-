#pragma once

#include <vulkan/vulkan_core.h>

#include <memory>

namespace Mini {

    ///
    /// C++ wrapper class for a Vulkan semaphore.
    ///
    /// This class manages the lifetime of a Vulkan semaphore.
    ///
    class Semaphore {
    public:
        Semaphore() noexcept = default;

        ///
        /// Create the semaphore.
        ///
        /// @param device Vulkan device
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Semaphore(VkDevice device);

        ///
        /// Import a semaphore.
        ///
        /// @param device Vulkan device
        /// @param fd File descriptor to import the semaphore from.
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Semaphore(VkDevice device, int* fd);

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->semaphore; }

        // Trivially copyable, moveable and destructible
        Semaphore(const Semaphore&) noexcept = default;
        Semaphore& operator=(const Semaphore&) noexcept = default;
        Semaphore(Semaphore&&) noexcept = default;
        Semaphore& operator=(Semaphore&&) noexcept = default;
        ~Semaphore() = default;
    private:
        std::shared_ptr<VkSemaphore> semaphore;
    };

}
