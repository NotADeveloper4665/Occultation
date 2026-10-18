#include "nnapi_runtime.h"
#include <android/NeuralNetworks.h>
#include <type_traits>
#include <dlfcn.h>
#include <cstdlib>
#include <iostream>
#include <string>
// Compile-time ABI checks against the real NDK declarations.
#define CHECK_ABI(member, symbol) static_assert(std::is_same<decltype(occultation::NnapiRuntime::member), decltype(&symbol)>::value, "NNAPI ABI mismatch")
CHECK_ABI(getDeviceCount, ANeuralNetworks_getDeviceCount);
CHECK_ABI(getDevice, ANeuralNetworks_getDevice);
CHECK_ABI(Device_getName, ANeuralNetworksDevice_getName);
CHECK_ABI(Device_getType, ANeuralNetworksDevice_getType);
CHECK_ABI(Model_create, ANeuralNetworksModel_create);
CHECK_ABI(Model_free, ANeuralNetworksModel_free);
CHECK_ABI(Model_addOperand, ANeuralNetworksModel_addOperand);
CHECK_ABI(Model_setOperandValue, ANeuralNetworksModel_setOperandValue);
CHECK_ABI(Model_addOperation, ANeuralNetworksModel_addOperation);
CHECK_ABI(Model_identifyInputsAndOutputs, ANeuralNetworksModel_identifyInputsAndOutputs);
CHECK_ABI(Model_finish, ANeuralNetworksModel_finish);
CHECK_ABI(Model_getSupportedOperationsForDevices, ANeuralNetworksModel_getSupportedOperationsForDevices);
CHECK_ABI(Compilation_createForDevices, ANeuralNetworksCompilation_createForDevices);
CHECK_ABI(Compilation_setPreference, ANeuralNetworksCompilation_setPreference);
CHECK_ABI(Compilation_setTimeout, ANeuralNetworksCompilation_setTimeout);
CHECK_ABI(Compilation_finish, ANeuralNetworksCompilation_finish);
CHECK_ABI(Compilation_free, ANeuralNetworksCompilation_free);
CHECK_ABI(Execution_create, ANeuralNetworksExecution_create);
CHECK_ABI(Execution_setInput, ANeuralNetworksExecution_setInput);
CHECK_ABI(Execution_setOutput, ANeuralNetworksExecution_setOutput);
CHECK_ABI(Execution_setTimeout, ANeuralNetworksExecution_setTimeout);
CHECK_ABI(Execution_compute, ANeuralNetworksExecution_compute);
CHECK_ABI(Execution_free, ANeuralNetworksExecution_free);
#undef CHECK_ABI
static void require(bool value) { if (!value) { std::cerr << "NNAPI runtime test failed\n"; std::exit(1); } }
int main(int argc, char**) {
    occultation::NnapiRuntime runtime;
    if (argc > 1) { require(!runtime.available()); require(runtime.selfTest().find("unavailable") != std::string::npos); return 0; }
    require(runtime.available());
    void* lib = dlopen("libneuralnetworks.so", RTLD_NOW);
    auto live = reinterpret_cast<int (*)()>(dlsym(lib, "fakeLiveObjects"));
    require(live != nullptr);
    for (const char* test : {"pass", "unsupported", "compile-fail", "execute-fail", "wrong-output", "cpu-only", "graph-fail", "bind-fail"}) {
        setenv("NNAPI_TEST_CASE", test, 1);
        const auto report = runtime.selfTest();
        require((report.find("PASS,") != std::string::npos) == (std::string(test) == "pass"));
        require(live() == 0);
    }
    dlclose(lib);
    std::cout << "PASS: 8 native NNAPI driver scenarios, device restriction and object cleanup\n";
}
