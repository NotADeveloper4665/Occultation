#pragma once

#include <android/NeuralNetworksTypes.h>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace occultation {
// Uses only public NDK entry points, resolved at runtime to preserve minSdk 21.
// Ownership: the runtime must outlive its models, compilations and executions.
class NnapiRuntime {
public:
    NnapiRuntime();
    ~NnapiRuntime();
    NnapiRuntime(const NnapiRuntime&) = delete;
    NnapiRuntime& operator=(const NnapiRuntime&) = delete;
    bool available() const { return ready_; }
    std::string selfTest();

    // Caller supplies a finished graph and its exact operation count. Compilation
    // is restricted to one ACCELERATOR device after full operation support checks.
    int compile(ANeuralNetworksModel* model, uint32_t operationCount,
                const ANeuralNetworksDevice* device, ANeuralNetworksCompilation** result);
    struct Buffer { void* data; size_t bytes; };
    // Synchronous: buffers and compilation remain owned by caller until return.
    // timeoutNs is a driver hint, not a cancellation or wall-clock guarantee.
    int execute(ANeuralNetworksCompilation* compilation, const std::vector<Buffer>& inputs,
                const std::vector<Buffer>& outputs, uint64_t timeoutNs);

    // Explicit public ABI signatures avoid referencing unavailable function
    // declarations while compiling the minSdk-21 module. Every call uses dlsym.
    int (*getDeviceCount)(uint32_t*) = nullptr;
    int (*getDevice)(uint32_t, ANeuralNetworksDevice**) = nullptr;
    int (*Device_getName)(const ANeuralNetworksDevice*, const char**) = nullptr;
    int (*Device_getType)(const ANeuralNetworksDevice*, int32_t*) = nullptr;
    int (*Model_create)(ANeuralNetworksModel**) = nullptr;
    void (*Model_free)(ANeuralNetworksModel*) = nullptr;
    int (*Model_addOperand)(ANeuralNetworksModel*, const ANeuralNetworksOperandType*) = nullptr;
    int (*Model_setOperandValue)(ANeuralNetworksModel*, int32_t, const void*, size_t) = nullptr;
    int (*Model_addOperation)(ANeuralNetworksModel*, ANeuralNetworksOperationType, uint32_t,
                             const uint32_t*, uint32_t, const uint32_t*) = nullptr;
    int (*Model_identifyInputsAndOutputs)(ANeuralNetworksModel*, uint32_t, const uint32_t*,
                                         uint32_t, const uint32_t*) = nullptr;
    int (*Model_finish)(ANeuralNetworksModel*) = nullptr;
    int (*Model_getSupportedOperationsForDevices)(const ANeuralNetworksModel*,
            const ANeuralNetworksDevice* const*, uint32_t, bool*) = nullptr;
    int (*Compilation_createForDevices)(ANeuralNetworksModel*, const ANeuralNetworksDevice* const*,
                                         uint32_t, ANeuralNetworksCompilation**) = nullptr;
    int (*Compilation_setPreference)(ANeuralNetworksCompilation*, int32_t) = nullptr;
    int (*Compilation_setTimeout)(ANeuralNetworksCompilation*, uint64_t) = nullptr;
    int (*Compilation_finish)(ANeuralNetworksCompilation*) = nullptr;
    void (*Compilation_free)(ANeuralNetworksCompilation*) = nullptr;
    int (*Execution_create)(ANeuralNetworksCompilation*, ANeuralNetworksExecution**) = nullptr;
    int (*Execution_setInput)(ANeuralNetworksExecution*, int32_t, const ANeuralNetworksOperandType*,
                               const void*, size_t) = nullptr;
    int (*Execution_setOutput)(ANeuralNetworksExecution*, int32_t, const ANeuralNetworksOperandType*,
                                void*, size_t) = nullptr;
    int (*Execution_setTimeout)(ANeuralNetworksExecution*, uint64_t) = nullptr;
    int (*Execution_compute)(ANeuralNetworksExecution*) = nullptr;
    void (*Execution_free)(ANeuralNetworksExecution*) = nullptr;
private:
    void* library_ = nullptr;
    bool ready_ = false;
};
}
