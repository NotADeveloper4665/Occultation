// Test driver only. Never packaged into Android APKs.
#include <android/NeuralNetworks.h>
#include <cstdlib>
#include <cstring>
#include <string>
static int live = 0;
static float* output = nullptr;
static bool scenario(const char* value) { const char* s = std::getenv("NNAPI_TEST_CASE"); return s && std::string(s) == value; }
template<class T> static int allocate(T** value) { *value = reinterpret_cast<T*>(new char[1]); ++live; return 0; }
template<class T> static void release(T* value) { delete[] reinterpret_cast<char*>(value); --live; }
extern "C" int fakeLiveObjects() { return live; }
extern "C" {
int ANeuralNetworks_getDeviceCount(uint32_t* count) { *count = 2; return 0; }
int ANeuralNetworks_getDevice(uint32_t index, ANeuralNetworksDevice** device) { *device = reinterpret_cast<ANeuralNetworksDevice*>(static_cast<uintptr_t>(index + 1)); return 0; }
int ANeuralNetworksDevice_getName(const ANeuralNetworksDevice*, const char** name) { *name = "Test driver"; return 0; }
int ANeuralNetworksDevice_getType(const ANeuralNetworksDevice* d, int32_t* type) { *type = scenario("cpu-only") || reinterpret_cast<uintptr_t>(d) == 1 ? ANEURALNETWORKS_DEVICE_CPU : ANEURALNETWORKS_DEVICE_ACCELERATOR; return 0; }
int ANeuralNetworksModel_create(ANeuralNetworksModel** m) { return allocate(m); }
void ANeuralNetworksModel_free(ANeuralNetworksModel* m) { release(m); }
int ANeuralNetworksModel_addOperand(ANeuralNetworksModel*, const ANeuralNetworksOperandType*) { return 0; }
int ANeuralNetworksModel_setOperandValue(ANeuralNetworksModel*, int32_t, const void*, size_t) { return 0; }
int ANeuralNetworksModel_addOperation(ANeuralNetworksModel*, ANeuralNetworksOperationType, uint32_t, const uint32_t*, uint32_t, const uint32_t*) { return 0; }
int ANeuralNetworksModel_identifyInputsAndOutputs(ANeuralNetworksModel*, uint32_t, const uint32_t*, uint32_t, const uint32_t*) { return 0; }
int ANeuralNetworksModel_finish(ANeuralNetworksModel*) { return scenario("graph-fail") ? ANEURALNETWORKS_BAD_DATA : 0; }
int ANeuralNetworksModel_getSupportedOperationsForDevices(const ANeuralNetworksModel*, const ANeuralNetworksDevice* const* devices, uint32_t count, bool* supported) {
    if (count != 1 || reinterpret_cast<uintptr_t>(devices[0]) != 2) std::abort();
    *supported = !scenario("unsupported"); return 0;
}
int ANeuralNetworksCompilation_createForDevices(ANeuralNetworksModel*, const ANeuralNetworksDevice* const* devices, uint32_t count, ANeuralNetworksCompilation** c) {
    if (count != 1 || reinterpret_cast<uintptr_t>(devices[0]) != 2 || scenario("unsupported")) std::abort();
    return allocate(c);
}
int ANeuralNetworksCompilation_setPreference(ANeuralNetworksCompilation*, int32_t) { return 0; }
int ANeuralNetworksCompilation_setTimeout(ANeuralNetworksCompilation*, uint64_t timeout) { return timeout ? 0 : ANEURALNETWORKS_BAD_DATA; }
int ANeuralNetworksCompilation_finish(ANeuralNetworksCompilation*) { return scenario("compile-fail") ? ANEURALNETWORKS_OP_FAILED : 0; }
void ANeuralNetworksCompilation_free(ANeuralNetworksCompilation* c) { release(c); }
int ANeuralNetworksExecution_create(ANeuralNetworksCompilation*, ANeuralNetworksExecution** e) { return allocate(e); }
int ANeuralNetworksExecution_setInput(ANeuralNetworksExecution*, int32_t, const ANeuralNetworksOperandType*, const void*, size_t) { return scenario("bind-fail") ? ANEURALNETWORKS_BAD_DATA : 0; }
int ANeuralNetworksExecution_setOutput(ANeuralNetworksExecution*, int32_t, const ANeuralNetworksOperandType*, void* data, size_t bytes) { if (bytes != 16) std::abort(); output = static_cast<float*>(data); return 0; }
int ANeuralNetworksExecution_setTimeout(ANeuralNetworksExecution*, uint64_t timeout) { return timeout ? 0 : ANEURALNETWORKS_BAD_DATA; }
int ANeuralNetworksExecution_compute(ANeuralNetworksExecution*) {
    if (scenario("execute-fail")) return ANEURALNETWORKS_OP_FAILED;
    const float expected[] = {3, 1, 0, 75}; std::memcpy(output, expected, sizeof(expected));
    if (scenario("wrong-output")) output[0] = 0;
    return 0;
}
void ANeuralNetworksExecution_free(ANeuralNetworksExecution* e) { release(e); }
}
