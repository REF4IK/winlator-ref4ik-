# lsfg-vk

> [!NOTE]
> This repository is a **branch of [`lsfg-vk`](https://github.com/PancakeTAS/lsfg-vk) 1.0.0**
> with Android-specific patches added on top. Together with the
> [`LSFG-Android`](../LSFG-Android/) app one directory above, this branch
> enables native frame generation on Android, no root required. The Linux
> build path is unchanged from upstream: every Android-only change is guarded
> by `#ifdef __ANDROID__`, and Linux builds produce a layer binary
> indistinguishable from upstream 1.0.0.

Lossless Scaling is a Windows-exclusive app with the goal of bringing frame
generation (among other features) to every single game or app.

lsfg-vk brings this frame generation to Linux users by acting as a Vulkan
layer in between your game and your graphics card.

> [!TIP]
> **lsfg-vk 1.0.0 is itself a pre-release** of the upstream project. Issues
> found in the upstream code path should be reported to the upstream project;
> issues specific to the Android port belong in the [`LSFG-Android`](../LSFG-Android/)
> repository.

## Installation (Linux)

lsfg-vk can run on a variety of Linux distributions:

- Click [here](https://github.com/PancakeTAS/lsfg-vk/releases) to download
  lsfg-vk for your distribution.
- Follow [this guide](https://github.com/PancakeTAS/lsfg-vk/wiki/Installation-Guide)
  if you need any more help.

Once installed, open up the lsfg-vk Configuration Window which should
hopefully appear in your application menu.

Please see the [Wiki](https://github.com/PancakeTAS/lsfg-vk/wiki) for more
information and join the [Discord](https://discord.gg/losslessscaling) for
help (needs Steam verification).

## Native Android

The Android-specific patches in this branch are consumed by the
[`LSFG-Android`](../LSFG-Android/) app, which runs frame generation on a
MediaProjection capture and composites the result in a system overlay over
the target game.

Why a separate API surface was needed: Adreno and Mali drivers refuse
`vkGetMemoryFdKHR` on memory imported from an `AHardwareBuffer`. The
upstream FD-based image-sharing path therefore can't be reused on Android.
The Android additions replace it with an AHB-native path: callers hand
framegen `AHardwareBuffer*` directly, framegen imports them via
`VK_ANDROID_external_memory_android_hardware_buffer` into its own device,
and a new `waitIdle` entry exposes `vkDeviceWaitIdle` so a host Vulkan
session sharing the same AHBs can synchronize without a cross-device
semaphore (which Vulkan does not define).

The Android build does **not** run from this directory. The consumer is the
Android Studio project at [`../LSFG-Android/`](../LSFG-Android/), which
pulls `framegen/` in via CMake `add_subdirectory()` from the relative path
`../../../../../lsfg-vk-android` in its JNI `CMakeLists.txt`. See
[`../LSFG-Android/README.md`](../LSFG-Android/README.md) for the build flow.

## Differences from upstream `lsfg-vk` 1.0.0

No upstream files were removed and no new top-level files were added — the
Android branch is a set of additive `#ifdef __ANDROID__` patches inside the
existing tree. Per-file summary:

| File | Change |
|---|---|
| `framegen/public/lsfg_3_1.hpp`, `framegen/public/lsfg_3_1p.hpp` | Added forward decl `struct AHardwareBuffer;` and the public Android entry points `createContextFromAHB(...)` and `waitIdle()`. |
| `framegen/v3.1_src/lsfg.cpp`, `framegen/v3.1p_src/lsfg.cpp` | Implementation of `createContextFromAHB` and `waitIdle`. |
| `framegen/v3.1_include/v3_1/context.hpp`, `framegen/v3.1p_include/v3_1p/context.hpp` | Added `Context` constructor overload taking `AHardwareBuffer*` inputs and outputs. |
| `framegen/v3.1_src/context.cpp`, `framegen/v3.1p_src/context.cpp` | Implementation of the AHB constructor — same shader chain as the FD constructor, but inputs and outputs are seeded from caller-supplied AHBs. |
| `framegen/v3.1_include/v3_1/shaders/generate.hpp`, `framegen/v3.1p_include/v3_1p/shaders/generate.hpp` | Added `Generate(...)` overload taking pre-allocated `Core::Image` outputs (the AHB path needs them); added a `getOutImages()` accessor so the host can do queue-family acquire/release on output AHBs. |
| `framegen/v3.1_src/shaders/generate.cpp`, `framegen/v3.1p_src/shaders/generate.cpp` | Implementation of the new `Generate` constructor. |
| `framegen/include/core/image.hpp` | Forward decl `struct AHardwareBuffer;`, new `Image` constructor that imports an AHB via `VK_ANDROID_external_memory_android_hardware_buffer` + `VkImportAndroidHardwareBufferInfoANDROID` with a dedicated allocation, and an `externalShared` flag. |
| `framegen/src/core/image.cpp` | Implementation of the AHB-backed `Image` constructor. |
| `framegen/src/core/device.cpp` | Android variant of the device-extension list. Replaces `VK_KHR_external_memory_fd` + `VK_KHR_external_semaphore_fd` with `VK_ANDROID_external_memory_android_hardware_buffer`, `VK_KHR_external_memory`, `VK_KHR_sampler_ycbcr_conversion`, `VK_KHR_dedicated_allocation`, `VK_KHR_get_memory_requirements2`, `VK_KHR_bind_memory2`, `VK_KHR_maintenance1`. `VK_EXT_robustness2` stays mandatory on both targets. |
| Top-level `CMakeLists.txt` | Minor build glue so the Android Studio project can pull `framegen/` in via `add_subdirectory()`. |
| `thirdparty/` | Unchanged. Same `volk`, `pe-parse`, `dxbc`, `toml11` as upstream 1.0.0. The Android Studio project consumes `volk`, `pe-parse`, and `dxbc` directly from this branch. |

### Public Android API at a glance

| Symbol | Where | What it adds |
|---|---|---|
| `LSFG_3_1::createContextFromAHB` / `LSFG_3_1P::createContextFromAHB` | `framegen/public/lsfg_3_1{,p}.hpp`, `framegen/v3.1{,p}_src/lsfg.cpp` | Public Android API: takes `AHardwareBuffer*` instead of opaque file descriptors. |
| `LSFG_3_1::waitIdle` / `LSFG_3_1P::waitIdle` | `framegen/public/lsfg_3_1{,p}.hpp`, `framegen/v3.1{,p}_src/lsfg.cpp` | Exposes `vkDeviceWaitIdle` on framegen's internal device so a host Vulkan session sharing AHBs can synchronize without a cross-device semaphore. |
| `Image::Image(..., AHardwareBuffer*)` | `framegen/include/core/image.hpp`, `framegen/src/core/image.cpp` | Imports an AHB into a `VkImage` via `VK_ANDROID_external_memory_android_hardware_buffer` + `VkImportAndroidHardwareBufferInfoANDROID`, with dedicated allocation. |
| `Context::Context(..., AHardwareBuffer*, ...)` | `framegen/v3.1{,p}_include/v3_1{,p}/context.hpp`, `framegen/v3.1{,p}_src/context.cpp` | Mirror of the FD constructor, building the same shader chain but seeding inputs/outputs from caller-provided AHBs. |
| `Generate(..., std::vector<Core::Image> outImgs)` | `framegen/v3.1{,p}_include/v3_1{,p}/shaders/generate.hpp`, `framegen/v3.1{,p}_src/shaders/generate.cpp` | Variant that accepts pre-built output images instead of allocating from FDs. Used by the AHB context. |
| Device extension list, Android variant | `framegen/src/core/device.cpp` | Replaces the FD extension chain with the AHB extension chain. `VK_EXT_robustness2` stays required. |

### Compatibility note

These patches **only** execute when the caller uses the new `*FromAHB` entry
points. The FD-based public API is untouched, so Linux builds of
`lsfg-vk-android` produce a layer binary indistinguishable from upstream
`lsfg-vk` 1.0.0. The Linux build commands documented upstream work on this
branch unchanged.

## Credits

- **[PancakeTAS](https://github.com/PancakeTAS)** — author of the original
  [`lsfg-vk`](https://github.com/PancakeTAS/lsfg-vk) Linux port, which is the
  foundation this branch builds on.
- **THS / Lossless Scaling** — creator of the official Windows app
  [Lossless Scaling](https://store.steampowered.com/app/993090/Lossless_Scaling/),
  whose frame-generation technology makes all of this possible.
- **[FrankBarretta](https://github.com/FrankBarretta)** — port from Linux to
  Android (this branch and the consumer app
  [`LSFG-Android`](../LSFG-Android/)).
