#include "nnapi_runtime.h"
#include <dlfcn.h>
#include <cstdlib>
#include <iostream>
#include <string>
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
