#pragma once

#include <android/NeuralNetworks.h>
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

#define NNAPI_FN(name) decltype(&ANeuralNetworks##name) name = nullptr
    decltype(&ANeuralNetworks_getDeviceCount) getDeviceCount = nullptr;
    decltype(&ANeuralNetworks_getDevice) getDevice = nullptr;
    NNAPI_FN(Device_getName);
    NNAPI_FN(Device_getType);
    NNAPI_FN(Model_create);
    NNAPI_FN(Model_free);
    NNAPI_FN(Model_addOperand);
    NNAPI_FN(Model_setOperandValue);
    NNAPI_FN(Model_addOperation);
    NNAPI_FN(Model_identifyInputsAndOutputs);
    NNAPI_FN(Model_finish);
    NNAPI_FN(Model_getSupportedOperationsForDevices);
    NNAPI_FN(Compilation_createForDevices);
    NNAPI_FN(Compilation_setPreference);
    NNAPI_FN(Compilation_setTimeout);
    NNAPI_FN(Compilation_finish);
    NNAPI_FN(Compilation_free);
    NNAPI_FN(Execution_create);
    NNAPI_FN(Execution_setInput);
    NNAPI_FN(Execution_setOutput);
    NNAPI_FN(Execution_setTimeout);
    NNAPI_FN(Execution_compute);
    NNAPI_FN(Execution_free);
#undef NNAPI_FN
private:
    void* library_ = nullptr;
    bool ready_ = false;
};
}
