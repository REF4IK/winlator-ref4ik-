# Third-party licenses

## Native LSFG frame generation (host-side)

The host-side Lossless Scaling frame-generation engine
(`app/src/main/cpp/winlator/lsfg/`, `app/src/main/cpp/thirdparty/dxbc/`)
is ported from Bannerlator's `lsfg-native` compositor work, which itself
derives from WinNative's LSFG port (credited to Camille LaVey / the Eden
Emulator Project) following upstream lsfg-vk (PancakeTAS).

- Engine files (`lsfg_*.cpp/.hpp`): **GPL-3.0-or-later** (see SPDX headers in
  each file; originals: Eden Emulator Project, WinNative).
- `thirdparty/dxbc` (DXVK's DXBC->SPIR-V translator): **zlib/libpng license**
  (see `thirdparty/dxbc/LICENSE.md`; upstream: doitsujin/dxvk).
- The 25 compute shaders are NOT redistributed: they are extracted on device
  from the user's own paid copy of Lossless Scaling (`Lossless.dll`, Steam
  app 993090), which is mmap'd read-only as data and never executed.

## Lossless Scaling

Frame-generation technology by THS / Lossless Scaling
(https://store.steampowered.com/app/993090/Lossless_Scaling/).
A user-supplied `Lossless.dll` is required; nothing from it is bundled.
