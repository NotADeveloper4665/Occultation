#include "nnapi_runtime.h"
#include <dlfcn.h>
#include <chrono>
#include <cmath>
#include <memory>
#include <sstream>

namespace occultation {
NnapiRuntime::NnapiRuntime() {
    library_ = dlopen("libneuralnetworks.so", RTLD_NOW | RTLD_LOCAL);
    if (!library_) return;
#define LOAD(name) name = reinterpret_cast<decltype(name)>(dlsym(library_, "ANeuralNetworks" #name))
    getDeviceCount = reinterpret_cast<decltype(getDeviceCount)>(dlsym(library_, "ANeuralNetworks_getDeviceCount"));
    getDevice = reinterpret_cast<decltype(getDevice)>(dlsym(library_, "ANeuralNetworks_getDevice"));
    LOAD(Device_getName); LOAD(Device_getType);
    LOAD(Model_create); LOAD(Model_free); LOAD(Model_addOperand); LOAD(Model_setOperandValue);
    LOAD(Model_addOperation); LOAD(Model_identifyInputsAndOutputs); LOAD(Model_finish);
    LOAD(Model_getSupportedOperationsForDevices); LOAD(Compilation_createForDevices);
    LOAD(Compilation_setPreference); LOAD(Compilation_setTimeout); LOAD(Compilation_finish);
    LOAD(Compilation_free); LOAD(Execution_create); LOAD(Execution_setInput);
    LOAD(Execution_setOutput); LOAD(Execution_setTimeout); LOAD(Execution_compute); LOAD(Execution_free);
#undef LOAD
    ready_ = getDeviceCount && getDevice && Device_getName && Device_getType &&
        Model_create && Model_free && Model_addOperand && Model_setOperandValue &&
        Model_addOperation && Model_identifyInputsAndOutputs && Model_finish &&
        Model_getSupportedOperationsForDevices && Compilation_createForDevices &&
        Compilation_setPreference && Compilation_finish && Compilation_free &&
        Execution_create && Execution_setInput && Execution_setOutput && Execution_compute && Execution_free;
}
NnapiRuntime::~NnapiRuntime() { if (library_) dlclose(library_); }

int NnapiRuntime::compile(ANeuralNetworksModel* model, uint32_t operationCount,
        const ANeuralNetworksDevice* device, ANeuralNetworksCompilation** result) {
    if (!result) return ANEURALNETWORKS_UNEXPECTED_NULL;
    *result = nullptr;
    if (!ready_ || !model || !device || operationCount == 0 || operationCount > 100000)
        return ANEURALNETWORKS_BAD_DATA;
    int32_t type = ANEURALNETWORKS_DEVICE_UNKNOWN;
    int status = Device_getType(device, &type);
    if (status != ANEURALNETWORKS_NO_ERROR) return status;
    if (type != ANEURALNETWORKS_DEVICE_ACCELERATOR) return ANEURALNETWORKS_BAD_DATA;
    std::unique_ptr<bool[]> supported(new bool[operationCount]());
    status = Model_getSupportedOperationsForDevices(model, &device, 1, supported.get());
    if (status != ANEURALNETWORKS_NO_ERROR) return status;
    for (uint32_t i = 0; i < operationCount; ++i)
        if (!supported[i]) return ANEURALNETWORKS_BAD_DATA;
    ANeuralNetworksCompilation* compilation = nullptr;
    status = Compilation_createForDevices(model, &device, 1, &compilation);
    if (status == ANEURALNETWORKS_NO_ERROR && !compilation) status = ANEURALNETWORKS_OP_FAILED;
    if (status == ANEURALNETWORKS_NO_ERROR)
        status = Compilation_setPreference(compilation, ANEURALNETWORKS_PREFER_FAST_SINGLE_ANSWER);
    if (status == ANEURALNETWORKS_NO_ERROR && Compilation_setTimeout)
        status = Compilation_setTimeout(compilation, 2000000000ULL);
    if (status == ANEURALNETWORKS_NO_ERROR) status = Compilation_finish(compilation);
    if (status != ANEURALNETWORKS_NO_ERROR) {
        if (compilation) Compilation_free(compilation);
        return status;
    }
    *result = compilation;
    return ANEURALNETWORKS_NO_ERROR;
}

int NnapiRuntime::execute(ANeuralNetworksCompilation* compilation,
        const std::vector<Buffer>& inputs, const std::vector<Buffer>& outputs, uint64_t timeoutNs) {
    if (!ready_ || !compilation || inputs.empty() || outputs.empty() || timeoutNs == 0)
        return ANEURALNETWORKS_BAD_DATA;
    for (const auto& buffers : {inputs, outputs})
        for (const auto& buffer : buffers)
            if (!buffer.data || !buffer.bytes) return ANEURALNETWORKS_BAD_DATA;
    ANeuralNetworksExecution* execution = nullptr;
    int status = Execution_create(compilation, &execution);
    if (status == ANEURALNETWORKS_NO_ERROR && !execution) status = ANEURALNETWORKS_OP_FAILED;
    for (size_t i = 0; status == ANEURALNETWORKS_NO_ERROR && i < inputs.size(); ++i)
        status = Execution_setInput(execution, static_cast<int32_t>(i), nullptr, inputs[i].data, inputs[i].bytes);
    for (size_t i = 0; status == ANEURALNETWORKS_NO_ERROR && i < outputs.size(); ++i)
        status = Execution_setOutput(execution, static_cast<int32_t>(i), nullptr, outputs[i].data, outputs[i].bytes);
    if (status == ANEURALNETWORKS_NO_ERROR && Execution_setTimeout)
        status = Execution_setTimeout(execution, timeoutNs);
    if (status == ANEURALNETWORKS_NO_ERROR) status = Execution_compute(execution);
    if (execution) Execution_free(execution);
    return status;
}

std::string NnapiRuntime::selfTest() {
    std::ostringstream report;
    report << "NNAPI accelerator execution test (float32 ADD, 4 elements)\n";
    report << "CPU/GPU/unknown devices excluded. No default-device compilation or CPU fallback.\n";
    if (!ready_) return report.str() + "Required NNAPI device-assignment APIs unavailable (Android 10+).\n";
    uint32_t count = 0;
    int status = getDeviceCount(&count);
    if (status != ANEURALNETWORKS_NO_ERROR)
        return report.str() + "Enumeration failed: " + std::to_string(status) + "\n";
    ANeuralNetworksModel* model = nullptr;
    status = Model_create(&model);
    if (status == ANEURALNETWORKS_NO_ERROR && !model) status = ANEURALNETWORKS_OP_FAILED;
    const uint32_t dimensions[] = {4};
    const ANeuralNetworksOperandType tensor = {ANEURALNETWORKS_TENSOR_FLOAT32, 1, dimensions, 0, 0};
    const ANeuralNetworksOperandType scalar = {ANEURALNETWORKS_INT32, 0, nullptr, 0, 0};
    for (int i = 0; status == ANEURALNETWORKS_NO_ERROR && i < 3; ++i) status = Model_addOperand(model, &tensor);
    if (status == ANEURALNETWORKS_NO_ERROR) status = Model_addOperand(model, &scalar);
    const int32_t activation = ANEURALNETWORKS_FUSED_NONE;
    if (status == ANEURALNETWORKS_NO_ERROR) status = Model_setOperandValue(model, 3, &activation, sizeof(activation));
    const uint32_t opInputs[] = {0, 1, 3}, opOutputs[] = {2}, modelInputs[] = {0, 1};
    if (status == ANEURALNETWORKS_NO_ERROR) status = Model_addOperation(model, ANEURALNETWORKS_ADD, 3, opInputs, 1, opOutputs);
    if (status == ANEURALNETWORKS_NO_ERROR) status = Model_identifyInputsAndOutputs(model, 2, modelInputs, 1, opOutputs);
    if (status == ANEURALNETWORKS_NO_ERROR) status = Model_finish(model);
    if (status != ANEURALNETWORKS_NO_ERROR) {
        if (model) Model_free(model);
        return report.str() + "Test graph creation failed: " + std::to_string(status) + "\n";
    }
    uint32_t eligible = 0;
    for (uint32_t i = 0; i < count; ++i) {
        ANeuralNetworksDevice* device = nullptr;
        int32_t type = ANEURALNETWORKS_DEVICE_UNKNOWN;
        const char* name = nullptr;
        if (getDevice(i, &device) != ANEURALNETWORKS_NO_ERROR || !device ||
                Device_getType(device, &type) != ANEURALNETWORKS_NO_ERROR ||
                type != ANEURALNETWORKS_DEVICE_ACCELERATOR) continue;
        ++eligible;
        Device_getName(device, &name);
        report << "Device " << i << " (" << (name ? name : "unnamed accelerator") << "): ";
        ANeuralNetworksCompilation* compilation = nullptr;
        status = compile(model, 1, device, &compilation);
        if (status != ANEURALNETWORKS_NO_ERROR) {
            report << "unsupported graph or compilation failure, NNAPI status=" << status << '\n';
            continue;
        }
        float a[] = {1, -2, 0.5f, 100}, b[] = {2, 3, -0.5f, -25}, output[] = {NAN, NAN, NAN, NAN};
        const auto start = std::chrono::steady_clock::now();
        status = execute(compilation, {{a, sizeof(a)}, {b, sizeof(b)}}, {{output, sizeof(output)}}, 100000000ULL);
        const double elapsedMs = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
        bool correct = status == ANEURALNETWORKS_NO_ERROR;
        for (int j = 0; j < 4; ++j)
            correct = correct && std::isfinite(output[j]) && std::fabs(output[j] - (a[j] + b[j])) < 0.0001f;
        report << (correct ? "PASS" : "FAIL") << ", NNAPI status=" << status
            << ", single cold execution wall time=" << elapsedMs << " ms\n";
        Compilation_free(compilation);
    }
    Model_free(model);
    if (!eligible) report << "No NNAPI ACCELERATOR devices exposed. No CPU fallback was attempted.\n";
    report << "Timeouts are driver hints. PASS verifies only this ADD graph on the selected driver; "
        "it does not prove TPU identity, interpolation-model support, zero-copy, or streaming performance.\n";
    return report.str();
}
}
