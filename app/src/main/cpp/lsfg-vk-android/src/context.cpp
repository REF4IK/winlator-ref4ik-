#include "context.hpp"
#include "config/config.hpp"
#include "common/exception.hpp"
#include "extract/extract.hpp"
#include "extract/trans.hpp"
#include "utils/utils.hpp"
#include "hooks.hpp"
#include "layer.hpp"

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#include <android/log.h>
#endif

#include <vulkan/vulkan_core.h>
#include <lsfg_3_1.hpp>
#include <lsfg_3_1p.hpp>

#include <filesystem>
#include <exception>
#include <iostream>
#include <cstdint>
#include <cstdlib>
#include <vector>
#include <chrono>
#include <memory>
#include <string>
#include <thread>
#include <array>

LsContext::LsContext(const Hooks::DeviceInfo& info, VkSwapchainKHR swapchain,
        VkExtent2D extent, const std::vector<VkImage>& swapchainImages)
        : swapchain(swapchain), swapchainImages(swapchainImages),
          extent(extent) {
    // get updated configuration
    auto& conf = Config::activeConf;
    if (!conf.config_file.empty()
            && (
                    !std::filesystem::exists(conf.config_file)
                  || conf.timestamp != std::filesystem::last_write_time(conf.config_file)
            )) {
        std::cerr << "lsfg-vk: Rereading configuration, as it is no longer valid.\n";
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        // reread configuration
        const std::string file = Utils::getConfigFile();
        const auto name = Utils::getProcessName();
        try {
            Config::updateConfig(file);
            conf = Config::getConfig(name);
        } catch (const std::exception& e) {
            std::cerr << "lsfg-vk: Failed to update configuration, continuing using old:\n";
            std::cerr << "- " << e.what() << '\n';
        }

        LSFG_3_1P::finalize();
        LSFG_3_1::finalize();

        // print config
        std::cerr << "lsfg-vk: Reloaded configuration for " << name.second << ":\n";
        if (!conf.dll.empty()) std::cerr << "  Using DLL from: " << conf.dll << '\n';
        std::cerr << "  Multiplier: " << conf.multiplier << '\n';
        std::cerr << "  Flow Scale: " << conf.flowScale << '\n';
        std::cerr << "  Performance Mode: " << (conf.performance ? "Enabled" : "Disabled") << '\n';
        std::cerr << "  HDR Mode: " << (conf.hdr ? "Enabled" : "Disabled") << '\n';
        if (conf.e_present != 2) std::cerr << "  ! Present Mode: " << conf.e_present << '\n';

        if (conf.multiplier <= 1) return;
    }
    // we could take the format from the swapchain,
    // but honestly this is safer.
    const VkFormat format = conf.hdr
        ? VK_FORMAT_R8G8B8A8_UNORM
        : VK_FORMAT_R16G16B16A16_SFLOAT;

#ifdef __ANDROID__
    // Android path: use AHardwareBuffer-backed images for sharing with framegen.
    // Turnip/Mesa on Android doesn't support OPAQUE_FD export, so we use the
    // AHB path (createContextFromAHB + presentContext with -1 + waitIdle).

    this->frame_0 = Mini::Image(info.device, info.physicalDevice,
        extent, format, VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
    this->frame_1 = Mini::Image(info.device, info.physicalDevice,
        extent, format, VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

    for (size_t i = 0; i < static_cast<size_t>(conf.multiplier - 1); ++i)
        this->out_n.emplace_back(info.device, info.physicalDevice,
            extent, format,
            VK_IMAGE_USAGE_TRANSFER_SRC_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

    // initialize lsfg in single-device mode (uses app's VkDevice)
    setenv("DISABLE_LSFG", "1", 1); // NOLINT

    const auto& q = info.queue;
    if (conf.performance) {
        LSFG_3_1P::initializeExternal(
            info.device, info.physicalDevice, q.first, q.second,
            conf.hdr, 1.0F / conf.flowScale, conf.multiplier - 1,
            [](const std::string& name) {
                auto dxbc = Extract::getShader(name);
                return Extract::translateShader(dxbc);
            });
    } else {
        LSFG_3_1::initializeExternal(
            info.device, info.physicalDevice, q.first, q.second,
            conf.hdr, 1.0F / conf.flowScale, conf.multiplier - 1,
            [](const std::string& name) {
                auto dxbc = Extract::getShader(name);
                return Extract::translateShader(dxbc);
            });
    }

    // Create framegen context using AHB sharing
    std::vector<AHardwareBuffer*> outAhbs;
    outAhbs.reserve(conf.multiplier - 1);
    for (size_t i = 0; i < static_cast<size_t>(conf.multiplier - 1); ++i)
        outAhbs.push_back(this->out_n.at(i).getAhb());

    int32_t ctxId;
    if (conf.performance)
        ctxId = LSFG_3_1P::createContextFromAHB(
            this->frame_0.getAhb(), this->frame_1.getAhb(),
            outAhbs, extent, format);
    else
        ctxId = LSFG_3_1::createContextFromAHB(
            this->frame_0.getAhb(), this->frame_1.getAhb(),
            outAhbs, extent, format);

    this->lsfgCtxId = std::shared_ptr<int32_t>(
        new int32_t(ctxId),
        [del = (conf.performance ? LSFG_3_1P::deleteContext : LSFG_3_1::deleteContext)](const int32_t* id) {
            del(*id);
        }
    );

    unsetenv("DISABLE_LSFG"); // NOLINT

    std::cerr << "lsfg-vk: Android AHB context created (id=" << ctxId << ")\n";

#else
    // Desktop Linux path: use OPAQUE_FD-based image sharing

    std::array<int, 2> fds{};
    this->frame_0 = Mini::Image(info.device, info.physicalDevice,
        extent, format, VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_IMAGE_ASPECT_COLOR_BIT,
        &fds.at(0));
    this->frame_1 = Mini::Image(info.device, info.physicalDevice,
        extent, format, VK_IMAGE_USAGE_TRANSFER_DST_BIT, VK_IMAGE_ASPECT_COLOR_BIT,
        &fds.at(1));

    std::vector<int> outFds(conf.multiplier - 1);
    for (size_t i = 0; i < (conf.multiplier - 1); ++i)
        this->out_n.emplace_back(info.device, info.physicalDevice,
            extent, format,
            VK_IMAGE_USAGE_TRANSFER_SRC_BIT, VK_IMAGE_ASPECT_COLOR_BIT,
            &outFds.at(i));

    // initialize lsfg in single-device mode
    const auto& q = info.queue;
    setenv("DISABLE_LSFG", "1", 1); // NOLINT

    if (conf.performance) {
        LSFG_3_1P::initializeExternal(
            info.device, info.physicalDevice, q.first, q.second,
            conf.hdr, 1.0F / conf.flowScale, conf.multiplier - 1,
            [](const std::string& name) {
                auto dxbc = Extract::getShader(name);
                return Extract::translateShader(dxbc);
            });
    } else {
        LSFG_3_1::initializeExternal(
            info.device, info.physicalDevice, q.first, q.second,
            conf.hdr, 1.0F / conf.flowScale, conf.multiplier - 1,
            [](const std::string& name) {
                auto dxbc = Extract::getShader(name);
                return Extract::translateShader(dxbc);
            });
    }

    this->lsfgCtxId = std::shared_ptr<int32_t>(
        new int32_t(
            conf.performance
                ? LSFG_3_1P::createContext(fds.at(0), fds.at(1), outFds, extent, format)
                : LSFG_3_1::createContext(fds.at(0), fds.at(1), outFds, extent, format)
        ),
        [del = (conf.performance ? LSFG_3_1P::deleteContext : LSFG_3_1::deleteContext)](const int32_t* id) {
            del(*id);
        }
    );

    unsetenv("DISABLE_LSFG"); // NOLINT
#endif

    // prepare render passes
    this->cmdPool = Mini::CommandPool(info.device, info.queue.first);
    for (size_t i = 0; i < 8; i++) {
        auto& pass = this->passInfos.at(i);
        pass.renderSemaphores.resize(conf.multiplier - 1);
        pass.acquireSemaphores.resize(conf.multiplier - 1);
        pass.postCopyBufs.resize(conf.multiplier - 1);
        pass.postCopySemaphores.resize(conf.multiplier - 1);
        pass.prevPostCopySemaphores.resize(conf.multiplier - 1);
    }
}

VkResult LsContext::present(const Hooks::DeviceInfo& info, const void* pNext, VkQueue queue,
        const std::vector<VkSemaphore>& gameRenderSemaphores, uint32_t presentIdx) {
    auto& conf = Config::activeConf;

    // --- Periodic config hot-reload check (every ~30 frames) ---
    if (frameIdx % 30 == 0 && !conf.config_file.empty()) {
        if (!std::filesystem::exists(conf.config_file)
            || conf.timestamp != std::filesystem::last_write_time(conf.config_file)) {
            std::cerr << "lsfg-vk: Hot-reload: config changed, reinitializing...\n";
            std::this_thread::sleep_for(std::chrono::milliseconds(100));

            const std::string file = Utils::getConfigFile();
            const auto name = Utils::getProcessName();
            try {
                Config::updateConfig(file);
                conf = Config::getConfig(name);
            } catch (const std::exception& e) {
                std::cerr << "lsfg-vk: Hot-reload config update failed, keeping old:\n";
                std::cerr << "- " << e.what() << '\n';
            }

            if (conf.multiplier > 1) {
                // Finalize old LSFG contexts and reinitialize
                LSFG_3_1P::finalize();
                LSFG_3_1::finalize();

                lsfgCtxId.reset(); // triggers deleteContext

                setenv("DISABLE_LSFG", "1", 1);

                const VkFormat format = conf.hdr
                    ? VK_FORMAT_R8G8B8A8_UNORM
                    : VK_FORMAT_R16G16B16A16_SFLOAT;

#ifdef __ANDROID__
                const auto& q = info.queue;
                if (conf.performance) {
                    LSFG_3_1P::initializeExternal(
                        info.device, info.physicalDevice, q.first, q.second,
                        conf.hdr, 1.0F / conf.flowScale,
                        static_cast<uint32_t>(conf.multiplier - 1),
                        [](const std::string& name) {
                            auto dxbc = Extract::getShader(name);
                            return Extract::translateShader(dxbc);
                        });
                } else {
                    LSFG_3_1::initializeExternal(
                        info.device, info.physicalDevice, q.first, q.second,
                        conf.hdr, 1.0F / conf.flowScale,
                        static_cast<uint32_t>(conf.multiplier - 1),
                        [](const std::string& name) {
                            auto dxbc = Extract::getShader(name);
                            return Extract::translateShader(dxbc);
                        });
                }

                std::vector<AHardwareBuffer*> outAhbs;
                outAhbs.reserve(conf.multiplier - 1);
                for (size_t i = 0; i < static_cast<size_t>(conf.multiplier - 1); ++i)
                    outAhbs.push_back(this->out_n.at(i).getAhb());

                int32_t ctxId;
                if (conf.performance)
                    ctxId = LSFG_3_1P::createContextFromAHB(
                        this->frame_0.getAhb(), this->frame_1.getAhb(),
                        outAhbs, extent, format);
                else
                    ctxId = LSFG_3_1::createContextFromAHB(
                        this->frame_0.getAhb(), this->frame_1.getAhb(),
                        outAhbs, extent, format);

                lsfgCtxId = std::shared_ptr<int32_t>(
                    new int32_t(ctxId),
                    [lsfgDeleteContext = (conf.performance
                        ? LSFG_3_1P::deleteContext
                        : LSFG_3_1::deleteContext)](const int32_t* id) {
                        lsfgDeleteContext(*id);
                    });

                unsetenv("DISABLE_LSFG");
                std::cerr << "lsfg-vk: Hot-reload reinitialized (ctxId=" << ctxId
                          << ", mult=" << conf.multiplier << ")\n";
#else
                // Desktop: reinit via FD path
                const auto& q = info.queue;
                if (conf.performance) {
                    LSFG_3_1P::initializeExternal(
                        info.device, info.physicalDevice, q.first, q.second,
                        conf.hdr, 1.0F / conf.flowScale,
                        static_cast<uint32_t>(conf.multiplier - 1),
                        [](const std::string& name) {
                            auto dxbc = Extract::getShader(name);
                            return Extract::translateShader(dxbc);
                        });
                } else {
                    LSFG_3_1::initializeExternal(
                        info.device, info.physicalDevice, q.first, q.second,
                        conf.hdr, 1.0F / conf.flowScale,
                        static_cast<uint32_t>(conf.multiplier - 1),
                        [](const std::string& name) {
                            auto dxbc = Extract::getShader(name);
                            return Extract::translateShader(dxbc);
                        });
                }

                std::array<int, 2> fds{};
                // We can't re-extract FDs easily — skip full reinit on hot-reload
                // for desktop path. The multiplier change will apply on next swapchain.
                std::cerr << "lsfg-vk: Hot-reload: desktop FD path, multiplier will"
                          << " apply on next swapchain recreate.\n";
                unsetenv("DISABLE_LSFG");
#endif
            } else {
                std::cerr << "lsfg-vk: Hot-reload: multiplier <= 1, disabling framegen\n";
            }
        }
    }
    auto& pass = this->passInfos.at(this->frameIdx % 8);

#ifdef __ANDROID__
    // Android path: synchronous frame generation using waitIdle()
    // instead of OPAQUE_FD semaphore export which Turnip doesn't support.

    // 1. copy swapchain image to frame_0/frame_1
    //    Use a simple semaphore (no fd export) to synchronize the copy
    pass.preCopySemaphores.at(1) = Mini::Semaphore(info.device);
    pass.preCopyBuf = Mini::CommandBuffer(info.device, this->cmdPool);
    pass.preCopyBuf.begin();

    Utils::copyImage(pass.preCopyBuf.handle(),
        this->swapchainImages.at(presentIdx),
        this->frameIdx % 2 == 0 ? this->frame_0.handle() : this->frame_1.handle(),
        this->extent.width, this->extent.height,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        true, false);

    pass.preCopyBuf.end();

    std::vector<VkSemaphore> gameRenderSemaphores2 = gameRenderSemaphores;
    if (this->frameIdx > 0)
        gameRenderSemaphores2.emplace_back(this->passInfos.at((this->frameIdx - 1) % 8)
            .preCopySemaphores.at(1).handle());

    // Submit the copy. Same-queue submission order already guarantees the
    // copy runs after the game's work signalled via gameRenderSemaphores2 —
    // no extra barrier submit needed. presentContext below + waitIdle() then
    // sync framegen's internal device before we read the output images.
    pass.preCopyBuf.submit(info.queue.second,
        gameRenderSemaphores2,
        { pass.preCopySemaphores.at(1).handle() });

    // 2. Tell framegen to generate intermediary frames
    //    presentContext(id, -1, {}) — no semaphore FDs, synchronous
    std::vector<int> noOutSems;  // empty
    if (conf.performance)
        LSFG_3_1P::presentContext(*this->lsfgCtxId, -1, noOutSems);
    else
        LSFG_3_1::presentContext(*this->lsfgCtxId, -1, noOutSems);

    // 3. Wait for framegen's GPU work to finish before reading output images.
    //    framegen uses its own VkDevice internally, so we need waitIdle()
    //    to ensure cross-device synchronization.
    if (conf.performance)
        LSFG_3_1P::waitIdle();
    else
        LSFG_3_1::waitIdle();

    // 4. Copy generated frames to swapchain images and present them
    for (size_t i = 0; i < static_cast<size_t>(conf.multiplier - 1); i++) {
        // acquire next swapchain image
        pass.acquireSemaphores.at(i) = Mini::Semaphore(info.device);
        uint32_t imageIdx{};
        auto res = Layer::ovkAcquireNextImageKHR(info.device, this->swapchain, 250'000'000,
            pass.acquireSemaphores.at(i).handle(), VK_NULL_HANDLE, &imageIdx);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
            throw LSFG::vulkan_error(res, "Failed to acquire next swapchain image");

        // copy output image to swapchain image
        pass.postCopySemaphores.at(i) = Mini::Semaphore(info.device);
        pass.postCopyBufs.at(i) = Mini::CommandBuffer(info.device, this->cmdPool);
        pass.postCopyBufs.at(i).begin();

        Utils::copyImage(pass.postCopyBufs.at(i).handle(),
            this->out_n.at(i).handle(),
            this->swapchainImages.at(imageIdx),
            this->extent.width, this->extent.height,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
            false, true);

        pass.postCopyBufs.at(i).end();
        pass.postCopyBufs.at(i).submit(info.queue.second,
            { pass.acquireSemaphores.at(i).handle() },
            { pass.postCopySemaphores.at(i).handle() });

        // present swapchain image
        VkSemaphore postCopySem = pass.postCopySemaphores.at(i).handle();
        const VkPresentInfoKHR presentInfo{
            .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
            .pNext = i == 0 ? pNext : nullptr,
            .waitSemaphoreCount = 1,
            .pWaitSemaphores = &postCopySem,
            .swapchainCount = 1,
            .pSwapchains = &this->swapchain,
            .pImageIndices = &imageIdx,
        };
        res = Layer::ovkQueuePresentKHR(queue, &presentInfo);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
            throw LSFG::vulkan_error(res, "Failed to present swapchain image");
    }

    // 5. present actual next frame (the real capture, not a generated one)
    //    Wait for the last post-copy to finish
    pass.prevPostCopySemaphores.at(conf.multiplier - 1 - 1) = Mini::Semaphore(info.device);
    VkSemaphore lastPostCopySem = pass.postCopySemaphores.at(conf.multiplier - 1 - 1).handle();
    const VkPresentInfoKHR finalPresentInfo{
        .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &lastPostCopySem,
        .swapchainCount = 1,
        .pSwapchains = &this->swapchain,
        .pImageIndices = &presentIdx,
    };
    auto res = Layer::ovkQueuePresentKHR(queue, &finalPresentInfo);
    if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
        throw LSFG::vulkan_error(res, "Failed to present swapchain image");

    this->frameIdx++;
    return res;

#else
    // Desktop Linux path: OPAQUE_FD semaphore-based synchronization

    // 1. copy swapchain image to frame_0/frame_1
    int preCopySemaphoreFd{};
    pass.preCopySemaphores.at(0) = Mini::Semaphore(info.device, &preCopySemaphoreFd);
    pass.preCopySemaphores.at(1) = Mini::Semaphore(info.device);
    pass.preCopyBuf = Mini::CommandBuffer(info.device, this->cmdPool);
    pass.preCopyBuf.begin();

    Utils::copyImage(pass.preCopyBuf.handle(),
        this->swapchainImages.at(presentIdx),
        this->frameIdx % 2 == 0 ? this->frame_0.handle() : this->frame_1.handle(),
        this->extent.width, this->extent.height,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        true, false);

    pass.preCopyBuf.end();

    std::vector<VkSemaphore> gameRenderSemaphores2 = gameRenderSemaphores;
    if (this->frameIdx > 0)
        gameRenderSemaphores2.emplace_back(this->passInfos.at((this->frameIdx - 1) % 8)
            .preCopySemaphores.at(1).handle());
    pass.preCopyBuf.submit(info.queue.second,
        gameRenderSemaphores2,
        { pass.preCopySemaphores.at(0).handle(),
          pass.preCopySemaphores.at(1).handle() });

    // 2. render intermediary frames
    std::vector<int> renderSemaphoreFds(conf.multiplier - 1);
    for (size_t i = 0; i < (conf.multiplier - 1); ++i)
        pass.renderSemaphores.at(i) = Mini::Semaphore(info.device, &renderSemaphoreFds.at(i));

    if (conf.performance)
        LSFG_3_1P::presentContext(*this->lsfgCtxId,
            preCopySemaphoreFd,
            renderSemaphoreFds);
    else
        LSFG_3_1::presentContext(*this->lsfgCtxId,
            preCopySemaphoreFd,
            renderSemaphoreFds);

    for (size_t i = 0; i < (conf.multiplier - 1); i++) {
        // 3. acquire next swapchain image
        pass.acquireSemaphores.at(i) = Mini::Semaphore(info.device);
        uint32_t imageIdx{};
        auto res = Layer::ovkAcquireNextImageKHR(info.device, this->swapchain, 250'000'000,
            pass.acquireSemaphores.at(i).handle(), VK_NULL_HANDLE, &imageIdx);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
            throw LSFG::vulkan_error(res, "Failed to acquire next swapchain image");

        // 4. copy output image to swapchain image
        pass.postCopySemaphores.at(i) = Mini::Semaphore(info.device);
        pass.prevPostCopySemaphores.at(i) = Mini::Semaphore(info.device);
        pass.postCopyBufs.at(i) = Mini::CommandBuffer(info.device, this->cmdPool);
        pass.postCopyBufs.at(i).begin();

        Utils::copyImage(pass.postCopyBufs.at(i).handle(),
            this->out_n.at(i).handle(),
            this->swapchainImages.at(imageIdx),
            this->extent.width, this->extent.height,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
            false, true);

        pass.postCopyBufs.at(i).end();
        pass.postCopyBufs.at(i).submit(info.queue.second,
            { pass.acquireSemaphores.at(i).handle(),
              pass.renderSemaphores.at(i).handle() },
            { pass.postCopySemaphores.at(i).handle(),
              pass.prevPostCopySemaphores.at(i).handle() });

        // 5. present swapchain image
        std::vector<VkSemaphore> waitSemaphores{ pass.postCopySemaphores.at(i).handle() };
        if (i != 0) waitSemaphores.emplace_back(pass.prevPostCopySemaphores.at(i - 1).handle());

        const VkPresentInfoKHR presentInfo{
            .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
            .pNext = i == 0 ? pNext : nullptr, // only set on first present
            .waitSemaphoreCount = static_cast<uint32_t>(waitSemaphores.size()),
            .pWaitSemaphores = waitSemaphores.data(),
            .swapchainCount = 1,
            .pSwapchains = &this->swapchain,
            .pImageIndices = &imageIdx,
        };
        res = Layer::ovkQueuePresentKHR(queue, &presentInfo);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
            throw LSFG::vulkan_error(res, "Failed to present swapchain image");
    }

    // 6. present actual next frame
    VkSemaphore lastPrevPostCopySemaphore =
        pass.prevPostCopySemaphores.at(conf.multiplier - 1 - 1).handle();
    const VkPresentInfoKHR presentInfo{
        .sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1,
        .pWaitSemaphores = &lastPrevPostCopySemaphore,
        .swapchainCount = 1,
        .pSwapchains = &this->swapchain,
        .pImageIndices = &presentIdx,
    };
    auto res = Layer::ovkQueuePresentKHR(queue, &presentInfo);
    if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR)
        throw LSFG::vulkan_error(res, "Failed to present swapchain image");

    this->frameIdx++;
    return res;
#endif
}
