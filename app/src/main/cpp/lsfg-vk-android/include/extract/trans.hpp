#pragma once

#include <cstdint>
#include <vector>

namespace Extract {

    ///
    /// Translate DXBC bytecode to SPIR-V bytecode.
    ///
    /// @param bytecode The DXBC bytecode to translate.
    /// @return The translated SPIR-V bytecode.
    ///
    std::vector<uint8_t> translateShader(std::vector<uint8_t> bytecode);

}
