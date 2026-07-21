#pragma once

#include "mini/commandpool.hpp"

#include <vulkan/vulkan_core.h>

#include <vector>
#include <memory>

namespace Mini {

    /// State of the command buffer.
    enum class CommandBufferState {
        /// Command buffer is not initialized or has been destroyed.
        Invalid,
        /// Command buffer has been created.
        Empty,
        /// Command buffer recording has started.
        Recording,
        /// Command buffer recording has ended.
        Full,
        /// Command buffer has been submitted to a queue.
        Submitted
    };

    ///
    /// C++ wrapper class for a Vulkan command buffer.
    ///
    /// This class manages the lifetime of a Vulkan command buffer.
    ///
    class CommandBuffer {
    public:
        CommandBuffer() noexcept = default;

        ///
        /// Create the command buffer.
        ///
        /// @param device Vulkan device
        /// @param pool Vulkan command pool
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        CommandBuffer(VkDevice device, const CommandPool& pool);

        ///
        /// Begin recording commands in the command buffer.
        ///
        /// @throws std::logic_error if the command buffer is in Empty state
        /// @throws LSFG::vulkan_error if beginning the command buffer fails.
        ///
        void begin();

        ///
        /// End recording commands in the command buffer.
        ///
        /// @throws std::logic_error if the command buffer is not in Recording state
        /// @throws LSFG::vulkan_error if ending the command buffer fails.
        ///
        void end();

        ///
        /// Submit the command buffer to a queue.
        ///
        /// @param queue Vulkan queue to submit to
        /// @param waitSemaphores Semaphores to wait on before executing the command buffer
        /// @param signalSemaphores Semaphores to signal after executing the command buffer
        ///
        /// @throws std::logic_error if the command buffer is not in Full state.
        /// @throws LSFG::vulkan_error if submission fails.
        ///
        void submit(VkQueue queue,
            const std::vector<VkSemaphore>& waitSemaphores = {},
            const std::vector<VkSemaphore>& signalSemaphores = {});

        /// Get the state of the command buffer.
        [[nodiscard]] CommandBufferState getState() const { return *this->state; }
        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->commandBuffer; }

        /// Trivially copyable, moveable and destructible
        CommandBuffer(const CommandBuffer&) noexcept = default;
        CommandBuffer& operator=(const CommandBuffer&) noexcept = default;
        CommandBuffer(CommandBuffer&&) noexcept = default;
        CommandBuffer& operator=(CommandBuffer&&) noexcept = default;
        ~CommandBuffer() = default;
    private:
        std::shared_ptr<CommandBufferState> state;
        std::shared_ptr<VkCommandBuffer> commandBuffer;
    };

}
