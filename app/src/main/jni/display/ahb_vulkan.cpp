#define VK_USE_PLATFORM_ANDROID_KHR
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <android/hardware_buffer.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <dlfcn.h>
#include <algorithm>
#include <array>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include "video.vert.h"
#include "video.frag.h"

namespace {
void check(VkResult r, const char* operation) {
    if (r != VK_SUCCESS) throw std::runtime_error(std::string(operation) + ": Vulkan result " + std::to_string(r));
}
#define INSTANCE_FUNCTIONS(X) X(DestroyInstance) X(CreateAndroidSurfaceKHR) X(DestroySurfaceKHR) \
 X(EnumeratePhysicalDevices) X(GetPhysicalDeviceProperties) X(GetPhysicalDeviceQueueFamilyProperties) \
 X(GetPhysicalDeviceSurfaceSupportKHR) X(GetPhysicalDeviceSurfaceCapabilitiesKHR) X(GetPhysicalDeviceSurfaceFormatsKHR) \
 X(GetPhysicalDeviceSurfacePresentModesKHR) X(EnumerateDeviceExtensionProperties) X(GetPhysicalDeviceFeatures2) \
 X(GetPhysicalDeviceMemoryProperties) X(CreateDevice) X(GetDeviceProcAddr)
#define DEVICE_FUNCTIONS(X) X(DestroyDevice) X(DeviceWaitIdle) X(GetDeviceQueue) X(CreateSwapchainKHR) X(DestroySwapchainKHR) \
 X(GetSwapchainImagesKHR) X(AcquireNextImageKHR) X(QueuePresentKHR) X(CreateImageView) X(DestroyImageView) \
 X(CreateRenderPass) X(DestroyRenderPass) X(CreateFramebuffer) X(DestroyFramebuffer) X(CreateCommandPool) X(DestroyCommandPool) \
 X(AllocateCommandBuffers) X(ResetCommandPool) X(BeginCommandBuffer) X(EndCommandBuffer) X(CmdPipelineBarrier) \
 X(CmdBeginRenderPass) X(CmdEndRenderPass) X(CmdBindPipeline) X(CmdBindDescriptorSets) X(CmdSetViewport) X(CmdSetScissor) \
 X(CmdPushConstants) X(CmdDraw) X(CreateSemaphore) X(DestroySemaphore) X(CreateFence) X(DestroyFence) X(ResetFences) \
 X(WaitForFences) X(QueueSubmit) X(GetAndroidHardwareBufferPropertiesANDROID) X(CreateImage) X(DestroyImage) \
 X(AllocateMemory) X(FreeMemory) X(BindImageMemory) X(CreateSamplerYcbcrConversion) X(DestroySamplerYcbcrConversion) \
 X(CreateSampler) X(DestroySampler) X(CreateDescriptorSetLayout) X(DestroyDescriptorSetLayout) X(CreateDescriptorPool) \
 X(DestroyDescriptorPool) X(AllocateDescriptorSets) X(UpdateDescriptorSets) X(CreatePipelineLayout) X(DestroyPipelineLayout) \
 X(CreateShaderModule) X(DestroyShaderModule) X(CreateGraphicsPipelines) X(DestroyPipeline)
struct Api {
    void* library = nullptr;
    PFN_vkGetInstanceProcAddr GetInstanceProcAddr = nullptr;
    PFN_vkCreateInstance CreateInstance = nullptr;
#define DECLARE(name) PFN_vk##name name = nullptr;
    INSTANCE_FUNCTIONS(DECLARE)
    DEVICE_FUNCTIONS(DECLARE)
#undef DECLARE
    ~Api() { if (library) dlclose(library); }
};
class Renderer {
    Api v;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t family = 0;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkExtent2D extent{};
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkFormat targetFormat = VK_FORMAT_UNDEFINED;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> framebuffers;
    std::vector<VkSemaphore> finished;
    VkSemaphore acquired = VK_NULL_HANDLE;
    VkFence completion = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkCommandBuffer command = VK_NULL_HANDLE;
    VkShaderModule vertex = VK_NULL_HANDLE, fragment = VK_NULL_HANDLE;
    ANativeWindow* window = nullptr;
    std::thread::id owner = std::this_thread::get_id();
    bool recreate = false;
    int currentModel = -1, currentRange = -1;
    void* android = nullptr;
    AHardwareBuffer* (*fromJava)(JNIEnv*, jobject) = nullptr;
    void (*describe)(const AHardwareBuffer*, AHardwareBuffer_Desc*) = nullptr;
    void (*retain)(AHardwareBuffer*) = nullptr;
    void (*release)(AHardwareBuffer*) = nullptr;
    struct Input {
        Renderer* r;
        AHardwareBuffer* buffer = nullptr;
        AHardwareBuffer_Desc desc{};
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
        VkSamplerYcbcrConversion conversion = VK_NULL_HANDLE;
        VkSampler sampler = VK_NULL_HANDLE;
        VkDescriptorSetLayout layout = VK_NULL_HANDLE;
        VkDescriptorPool descriptors = VK_NULL_HANDLE;
        VkDescriptorSet set = VK_NULL_HANDLE;
        VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
        VkPipeline pipeline = VK_NULL_HANDLE;
        explicit Input(Renderer* r) : r(r) { }
        ~Input() {
            auto& v = r->v; auto d = r->device;
            if (pipeline) v.DestroyPipeline(d, pipeline, nullptr);
            if (pipelineLayout) v.DestroyPipelineLayout(d, pipelineLayout, nullptr);
            if (descriptors) v.DestroyDescriptorPool(d, descriptors, nullptr);
            if (layout) v.DestroyDescriptorSetLayout(d, layout, nullptr);
            if (view) v.DestroyImageView(d, view, nullptr);
            if (sampler) v.DestroySampler(d, sampler, nullptr);
            if (conversion) v.DestroySamplerYcbcrConversion(d, conversion, nullptr);
            if (image) v.DestroyImage(d, image, nullptr);
            if (memory) v.FreeMemory(d, memory, nullptr);
            if (buffer) r->release(buffer);
        }
    };
    std::unordered_map<AHardwareBuffer*, std::unique_ptr<Input>> inputs;
    void ownerOnly() { if (owner != std::this_thread::get_id()) throw std::runtime_error("Vulkan queue accessed outside its presentation thread"); }
    void destroySwapchain() {
        inputs.clear();
        for (auto x : framebuffers) if (x) v.DestroyFramebuffer(device, x, nullptr);
        for (auto x : views) if (x) v.DestroyImageView(device, x, nullptr);
        for (auto x : finished) if (x) v.DestroySemaphore(device, x, nullptr);
        framebuffers.clear(); views.clear(); finished.clear();
        if (renderPass) { v.DestroyRenderPass(device, renderPass, nullptr); renderPass = VK_NULL_HANDLE; }
        if (swapchain) { v.DestroySwapchainKHR(device, swapchain, nullptr); swapchain = VK_NULL_HANDLE; }
    }
    void makeSwapchain() {
        check(v.DeviceWaitIdle(device), "Wait before swapchain replacement");
        destroySwapchain();
        VkSurfaceCapabilitiesKHR caps{};
        check(v.GetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &caps), "Surface capabilities");
        uint32_t count = 0;
        check(v.GetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &count, nullptr), "Surface format count");
        std::vector<VkSurfaceFormatKHR> formats(count);
        check(v.GetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &count, formats.data()), "Surface formats");
        targetFormat = VK_FORMAT_UNDEFINED;
        for (const auto& format : formats) {
            if ((format.format == VK_FORMAT_B8G8R8A8_UNORM || format.format == VK_FORMAT_R8G8B8A8_UNORM) &&
                    format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) { targetFormat = format.format; break; }
        }
        if (targetFormat == VK_FORMAT_UNDEFINED) throw std::runtime_error("No SDR UNORM swapchain format");
        check(v.GetPhysicalDeviceSurfacePresentModesKHR(physical, surface, &count, nullptr), "Present mode count");
        std::vector<VkPresentModeKHR> modes(count);
        check(v.GetPhysicalDeviceSurfacePresentModesKHR(physical, surface, &count, modes.data()), "Present modes");
        if (std::find(modes.begin(), modes.end(), VK_PRESENT_MODE_FIFO_KHR) == modes.end()) throw std::runtime_error("FIFO presentation unavailable");
        extent = caps.currentExtent;
        if (extent.width == UINT32_MAX) {
            extent.width = std::max(caps.minImageExtent.width, std::min(caps.maxImageExtent.width, static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(window)))));
            extent.height = std::max(caps.minImageExtent.height, std::min(caps.maxImageExtent.height, static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(window)))));
        }
        if (!extent.width || !extent.height || !(caps.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)) throw std::runtime_error("Invalid swapchain extent/usage");
        if (!(caps.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)) throw std::runtime_error("Unsupported surface pre-rotation");
        VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        for (auto bit : {VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR})
            if (caps.supportedCompositeAlpha & bit) { alpha = bit; break; }
        uint32_t images = std::max(2u, caps.minImageCount);
        if (caps.maxImageCount) images = std::min(images, caps.maxImageCount);
        VkSwapchainCreateInfoKHR info{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        info.surface = surface; info.minImageCount = images; info.imageFormat = targetFormat;
        info.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR; info.imageExtent = extent; info.imageArrayLayers = 1;
        info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT; info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR; info.compositeAlpha = alpha;
        info.presentMode = VK_PRESENT_MODE_FIFO_KHR; info.clipped = VK_TRUE;
        check(v.CreateSwapchainKHR(device, &info, nullptr, &swapchain), "Create FIFO swapchain");
        check(v.GetSwapchainImagesKHR(device, swapchain, &count, nullptr), "Swapchain image count");
        std::vector<VkImage> swapImages(count);
        check(v.GetSwapchainImagesKHR(device, swapchain, &count, swapImages.data()), "Swapchain images");
        VkAttachmentDescription attachment{}; attachment.format = targetFormat; attachment.samples = VK_SAMPLE_COUNT_1_BIT;
        attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR; attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE; attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED; attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference color{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{}; subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS; subpass.colorAttachmentCount = 1; subpass.pColorAttachments = &color;
        VkSubpassDependency dependency{}; dependency.srcSubpass = VK_SUBPASS_EXTERNAL; dependency.dstSubpass = 0;
        dependency.srcStageMask = dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        VkRenderPassCreateInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO}; rp.attachmentCount = 1; rp.pAttachments = &attachment;
        rp.subpassCount = 1; rp.pSubpasses = &subpass; rp.dependencyCount = 1; rp.pDependencies = &dependency;
        check(v.CreateRenderPass(device, &rp, nullptr, &renderPass), "Create SDR render pass");
        views.resize(count); framebuffers.resize(count); finished.resize(count);
        for (uint32_t i = 0; i < count; i++) {
            VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO}; view.image = swapImages[i]; view.viewType = VK_IMAGE_VIEW_TYPE_2D;
            view.format = targetFormat; view.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            check(v.CreateImageView(device, &view, nullptr, &views[i]), "Create display view");
            VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO}; fb.renderPass = renderPass; fb.attachmentCount = 1; fb.pAttachments = &views[i];
            fb.width = extent.width; fb.height = extent.height; fb.layers = 1;
            check(v.CreateFramebuffer(device, &fb, nullptr, &framebuffers[i]), "Create framebuffer");
            VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
            check(v.CreateSemaphore(device, &sem, nullptr, &finished[i]), "Create per-image present semaphore");
        }
        recreate = false;
    }
    std::unique_ptr<Input> import(AHardwareBuffer* buffer, int model, int range) {
        auto input = std::make_unique<Input>(this);
        retain(buffer); input->buffer = buffer; describe(buffer, &input->desc);
        if (input->desc.layers != 1 || !(input->desc.usage & AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE)) throw std::runtime_error("Unsupported AHB usage or layers");
        VkAndroidHardwareBufferFormatPropertiesANDROID format{VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID};
        VkAndroidHardwareBufferPropertiesANDROID properties{VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID}; properties.pNext = &format;
        check(v.GetAndroidHardwareBufferPropertiesANDROID(device, buffer, &properties), "Query decoder buffer properties");
        if (!(format.formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) throw std::runtime_error("AHB is not sampleable");
        // PRIVATE multi-planar formats use externalFormat and immutable YCbCr conversion.
        if (format.externalFormat == 0) throw std::runtime_error("Decoder buffer exposes no external YCbCr format");
        VkExternalFormatANDROID external{VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID}; external.externalFormat = format.externalFormat;
        VkExternalMemoryImageCreateInfo externalMemory{VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO};
        externalMemory.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID; externalMemory.pNext = &external;
        VkImageCreateInfo image{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO}; image.pNext = &externalMemory; image.imageType = VK_IMAGE_TYPE_2D;
        image.format = VK_FORMAT_UNDEFINED; image.extent = {input->desc.width, input->desc.height, 1}; image.mipLevels = image.arrayLayers = 1;
        image.samples = VK_SAMPLE_COUNT_1_BIT; image.tiling = VK_IMAGE_TILING_OPTIMAL; image.usage = VK_IMAGE_USAGE_SAMPLED_BIT;
        image.sharingMode = VK_SHARING_MODE_EXCLUSIVE; image.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        check(v.CreateImage(device, &image, nullptr, &input->image), "Import YCbCr image");
        VkPhysicalDeviceMemoryProperties memory{}; v.GetPhysicalDeviceMemoryProperties(physical, &memory);
        uint32_t type = UINT32_MAX;
        for (uint32_t i = 0; i < memory.memoryTypeCount; i++) if (properties.memoryTypeBits & (1u << i)) { type = i; break; }
        if (type == UINT32_MAX) throw std::runtime_error("No compatible AHB memory type");
        VkImportAndroidHardwareBufferInfoANDROID hardware{VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID}; hardware.buffer = buffer;
        VkMemoryDedicatedAllocateInfo dedicated{VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO}; dedicated.pNext = &hardware; dedicated.image = input->image;
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO}; allocation.pNext = &dedicated; allocation.allocationSize = properties.allocationSize; allocation.memoryTypeIndex = type;
        check(v.AllocateMemory(device, &allocation, nullptr, &input->memory), "Import dedicated AHB memory");
        check(v.BindImageMemory(device, input->image, input->memory, 0), "Bind decoder buffer memory");
        VkFilter filter = (format.formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        VkSamplerYcbcrConversionCreateInfo conversion{VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_CREATE_INFO}; conversion.pNext = &external;
        conversion.format = VK_FORMAT_UNDEFINED;
        conversion.ycbcrModel = model == 1 ? VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_709 : model == 2 ? VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_2020 : VK_SAMPLER_YCBCR_MODEL_CONVERSION_YCBCR_601;
        conversion.ycbcrRange = range == 1 ? VK_SAMPLER_YCBCR_RANGE_ITU_FULL : VK_SAMPLER_YCBCR_RANGE_ITU_NARROW;
        conversion.components = format.samplerYcbcrConversionComponents; conversion.xChromaOffset = format.suggestedXChromaOffset; conversion.yChromaOffset = format.suggestedYChromaOffset;
        conversion.chromaFilter = (format.formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_LINEAR_FILTER_BIT) ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        // When separate reconstruction filters are not supported they must agree.
        if (!(format.formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_YCBCR_CONVERSION_SEPARATE_RECONSTRUCTION_FILTER_BIT)) filter = conversion.chromaFilter;
        check(v.CreateSamplerYcbcrConversion(device, &conversion, nullptr, &input->conversion), "Create hardware YCbCr conversion");
        VkSamplerYcbcrConversionInfo converted{VK_STRUCTURE_TYPE_SAMPLER_YCBCR_CONVERSION_INFO}; converted.conversion = input->conversion;
        VkSamplerCreateInfo sampler{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO}; sampler.pNext = &converted; sampler.magFilter = sampler.minFilter = filter;
        sampler.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST; sampler.addressModeU = sampler.addressModeV = sampler.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        sampler.maxLod = 0;
        check(v.CreateSampler(device, &sampler, nullptr, &input->sampler), "Create immutable video sampler");
        VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO}; view.pNext = &converted; view.image = input->image;
        view.viewType = VK_IMAGE_VIEW_TYPE_2D; view.format = VK_FORMAT_UNDEFINED; view.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        check(v.CreateImageView(device, &view, nullptr, &input->view), "Create imported video view");
        VkDescriptorSetLayoutBinding binding{}; binding.binding = 0; binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        binding.descriptorCount = 1; binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT; binding.pImmutableSamplers = &input->sampler;
        VkDescriptorSetLayoutCreateInfo layout{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO}; layout.bindingCount = 1; layout.pBindings = &binding;
        check(v.CreateDescriptorSetLayout(device, &layout, nullptr, &input->layout), "Create YCbCr descriptor layout");
        // External-format conversions can consume multiple pool slots. Reserve
        // spare capacity and handle allocation failure; never assume one slot.
        VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 64};
        VkDescriptorPoolCreateInfo descriptorPool{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO}; descriptorPool.maxSets = 1; descriptorPool.poolSizeCount = 1; descriptorPool.pPoolSizes = &size;
        check(v.CreateDescriptorPool(device, &descriptorPool, nullptr, &input->descriptors), "Create conversion descriptor pool");
        VkDescriptorSetAllocateInfo set{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO}; set.descriptorPool = input->descriptors; set.descriptorSetCount = 1; set.pSetLayouts = &input->layout;
        check(v.AllocateDescriptorSets(device, &set, &input->set), "Allocate external-format sampler");
        VkDescriptorImageInfo texture{input->sampler, input->view, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET}; write.dstSet = input->set; write.dstBinding = 0; write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; write.pImageInfo = &texture;
        v.UpdateDescriptorSets(device, 1, &write, 0, nullptr);
        VkPushConstantRange crop{VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(float) * 4};
        VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO}; pl.setLayoutCount = 1; pl.pSetLayouts = &input->layout; pl.pushConstantRangeCount = 1; pl.pPushConstantRanges = &crop;
        check(v.CreatePipelineLayout(device, &pl, nullptr, &input->pipelineLayout), "Create video pipeline layout");
        VkPipelineShaderStageCreateInfo stages[2]{};
        for (auto& stage : stages) { stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stage.pName = "main"; }
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT; stages[0].module = vertex;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module = fragment;
        VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO}; assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo viewport{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO}; viewport.viewportCount = viewport.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO}; raster.polygonMode = VK_POLYGON_MODE_FILL; raster.cullMode = VK_CULL_MODE_NONE; raster.lineWidth = 1;
        VkPipelineMultisampleStateCreateInfo samples{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO}; samples.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blend{}; blend.colorWriteMask = 15;
        VkPipelineColorBlendStateCreateInfo blending{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO}; blending.attachmentCount = 1; blending.pAttachments = &blend;
        VkDynamicState states[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamic{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO}; dynamic.dynamicStateCount = 2; dynamic.pDynamicStates = states;
        VkGraphicsPipelineCreateInfo pipeline{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO}; pipeline.stageCount = 2; pipeline.pStages = stages;
        pipeline.pVertexInputState = &vi; pipeline.pInputAssemblyState = &assembly; pipeline.pViewportState = &viewport;
        pipeline.pRasterizationState = &raster; pipeline.pMultisampleState = &samples; pipeline.pColorBlendState = &blending; pipeline.pDynamicState = &dynamic;
        pipeline.layout = input->pipelineLayout; pipeline.renderPass = renderPass;
        check(v.CreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipeline, nullptr, &input->pipeline), "Create video sampling pipeline");
        return input;
    }
public:
    Renderer() = default;
    void initialize(JNIEnv* env, jobject target) {
        v.library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        if (!v.library) throw std::runtime_error("Vulkan loader unavailable");
        v.GetInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(v.library, "vkGetInstanceProcAddr"));
        if (!v.GetInstanceProcAddr) throw std::runtime_error("Vulkan loader entry point unavailable");
        v.CreateInstance = reinterpret_cast<PFN_vkCreateInstance>(v.GetInstanceProcAddr(VK_NULL_HANDLE, "vkCreateInstance"));
        if (!v.CreateInstance) throw std::runtime_error("Vulkan instance entry point unavailable");
        android = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
        if (!android) throw std::runtime_error("Android hardware buffer library unavailable");
        fromJava = reinterpret_cast<decltype(fromJava)>(dlsym(android, "AHardwareBuffer_fromHardwareBuffer"));
        describe = reinterpret_cast<decltype(describe)>(dlsym(android, "AHardwareBuffer_describe"));
        retain = reinterpret_cast<decltype(retain)>(dlsym(android, "AHardwareBuffer_acquire"));
        release = reinterpret_cast<decltype(release)>(dlsym(android, "AHardwareBuffer_release"));
        if (!fromJava || !describe || !retain || !release) throw std::runtime_error("Android hardware buffer entry points unavailable");
        window = ANativeWindow_fromSurface(env, target); if (!window) throw std::runtime_error("Invalid display surface");
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO}; app.pApplicationName = "Occultation AHB display"; app.apiVersion = VK_API_VERSION_1_1;
        const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO}; info.pApplicationInfo = &app; info.enabledExtensionCount = 2; info.ppEnabledExtensionNames = extensions;
        check(v.CreateInstance(&info, nullptr, &instance), "Create Vulkan 1.1 instance");
#define LOAD_INSTANCE(name) v.name = reinterpret_cast<PFN_vk##name>(v.GetInstanceProcAddr(instance, "vk" #name)); if (!v.name) throw std::runtime_error("Missing vk" #name);
        INSTANCE_FUNCTIONS(LOAD_INSTANCE)
#undef LOAD_INSTANCE
        VkAndroidSurfaceCreateInfoKHR androidSurface{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR}; androidSurface.window = window;
        check(v.CreateAndroidSurfaceKHR(instance, &androidSurface, nullptr, &surface), "Create Android Vulkan surface");
        uint32_t count = 0; check(v.EnumeratePhysicalDevices(instance, &count, nullptr), "GPU count");
        std::vector<VkPhysicalDevice> devices(count); check(v.EnumeratePhysicalDevices(instance, &count, devices.data()), "GPU list");
        const char* required[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME, VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME};
        for (auto candidate : devices) {
            VkPhysicalDeviceProperties props{}; v.GetPhysicalDeviceProperties(candidate, &props);
            if (props.apiVersion < VK_API_VERSION_1_1 || (props.deviceType != VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU && props.deviceType != VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU)) continue;
            VkPhysicalDeviceSamplerYcbcrConversionFeatures ycbcr{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES};
            VkPhysicalDeviceFeatures2 features{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2}; features.pNext = &ycbcr;
            v.GetPhysicalDeviceFeatures2(candidate, &features); if (!ycbcr.samplerYcbcrConversion) continue;
            check(v.EnumerateDeviceExtensionProperties(candidate, nullptr, &count, nullptr), "GPU extension count");
            std::vector<VkExtensionProperties> available(count); check(v.EnumerateDeviceExtensionProperties(candidate, nullptr, &count, available.data()), "GPU extensions");
            bool supported = true;
            for (auto name : required) supported &= std::any_of(available.begin(), available.end(), [name](const auto& x) { return std::string(x.extensionName) == name; });
            if (!supported) continue;
            v.GetPhysicalDeviceQueueFamilyProperties(candidate, &count, nullptr); std::vector<VkQueueFamilyProperties> families(count);
            v.GetPhysicalDeviceQueueFamilyProperties(candidate, &count, families.data());
            for (uint32_t i = 0; i < count; i++) {
                VkBool32 present = VK_FALSE; check(v.GetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &present), "Queue presentation support");
                if ((families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) { physical = candidate; family = i; break; }
            }
            if (physical) break;
        }
        if (!physical) throw std::runtime_error("No physical Vulkan GPU with AHB import, YCbCr conversion and presentation");
        float priority = 1;
        VkDeviceQueueCreateInfo q{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO}; q.queueFamilyIndex = family; q.queueCount = 1; q.pQueuePriorities = &priority;
        VkPhysicalDeviceSamplerYcbcrConversionFeatures ycbcr{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES}; ycbcr.samplerYcbcrConversion = VK_TRUE;
        VkDeviceCreateInfo dev{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO}; dev.pNext = &ycbcr; dev.queueCreateInfoCount = 1; dev.pQueueCreateInfos = &q; dev.enabledExtensionCount = 3; dev.ppEnabledExtensionNames = required;
        check(v.CreateDevice(physical, &dev, nullptr, &device), "Create AHB display device");
#define LOAD_DEVICE(name) v.name = reinterpret_cast<PFN_vk##name>(v.GetDeviceProcAddr(device, "vk" #name)); if (!v.name) throw std::runtime_error("Missing vk" #name);
        DEVICE_FUNCTIONS(LOAD_DEVICE)
#undef LOAD_DEVICE
        v.GetDeviceQueue(device, family, 0, &queue);
        VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO}; poolInfo.queueFamilyIndex = family;
        check(v.CreateCommandPool(device, &poolInfo, nullptr, &pool), "Create presentation command pool");
        VkCommandBufferAllocateInfo allocate{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO}; allocate.commandPool = pool; allocate.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; allocate.commandBufferCount = 1;
        check(v.AllocateCommandBuffers(device, &allocate, &command), "Allocate presentation command buffer");
        VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO}; check(v.CreateSemaphore(device, &sem, nullptr, &acquired), "Create acquire semaphore");
        VkFenceCreateInfo fence{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO}; check(v.CreateFence(device, &fence, nullptr, &completion), "Create completion fence");
        VkShaderModuleCreateInfo shader{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO}; shader.codeSize = sizeof(video_vert); shader.pCode = video_vert;
        check(v.CreateShaderModule(device, &shader, nullptr, &vertex), "Load vertex shader");
        shader.codeSize = sizeof(video_frag); shader.pCode = video_frag; check(v.CreateShaderModule(device, &shader, nullptr, &fragment), "Load sampler shader");
        makeSwapchain();
    }
    void present(JNIEnv* env, jobject hardware, int left, int top, int right, int bottom, int model, int range, int scaleMode) {
        ownerOnly();
        if (recreate || ANativeWindow_getWidth(window) != static_cast<int>(extent.width) || ANativeWindow_getHeight(window) != static_cast<int>(extent.height)) makeSwapchain();
        AHardwareBuffer* buffer = fromJava(env, hardware); if (!buffer) throw std::runtime_error("Invalid Android decoder buffer");
        if (model < 0 || model > 2 || range < 0 || range > 1) throw std::runtime_error("Invalid YCbCr color metadata");
        if (model != currentModel || range != currentRange) { inputs.clear(); currentModel = model; currentRange = range; }
        if (!inputs.count(buffer)) { if (inputs.size() >= 8) inputs.clear(); inputs.emplace(buffer, import(buffer, model, range)); }
        auto& input = *inputs.at(buffer);
        if (left < 0 || top < 0 || right <= left || bottom <= top || right > static_cast<int>(input.desc.width) || bottom > static_cast<int>(input.desc.height)) throw std::runtime_error("Invalid decoded image crop");
        uint32_t index = 0;
        VkResult result = v.AcquireNextImageKHR(device, swapchain, 5000000, acquired, VK_NULL_HANDLE, &index);
        if (result == VK_ERROR_OUT_OF_DATE_KHR) { recreate = true; return; }
        if (result == VK_TIMEOUT || result == VK_NOT_READY) return;
        if (result != VK_SUBOPTIMAL_KHR) check(result, "Acquire display image"); else recreate = true;
        check(v.ResetCommandPool(device, pool, 0), "Reset presentation commands");
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO}; begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        check(v.BeginCommandBuffer(command, &begin), "Begin presentation");
        VkImageMemoryBarrier acquire{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER}; acquire.srcQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT; acquire.dstQueueFamilyIndex = family;
        acquire.oldLayout = VK_IMAGE_LAYOUT_GENERAL; acquire.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        acquire.dstAccessMask = VK_ACCESS_SHADER_READ_BIT; acquire.image = input.image; acquire.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        v.CmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &acquire);
        VkClearValue black{}; black.color.float32[3] = 1;
        VkRenderPassBeginInfo render{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO}; render.renderPass = renderPass; render.framebuffer = framebuffers.at(index);
        render.renderArea.extent = extent; render.clearValueCount = 1; render.pClearValues = &black;
        v.CmdBeginRenderPass(command, &render, VK_SUBPASS_CONTENTS_INLINE);
        float scale = std::min(static_cast<float>(extent.width) / (right - left), static_cast<float>(extent.height) / (bottom - top));
        float width = scaleMode == 0 ? (right - left) * scale : extent.width;
        float height = scaleMode == 0 ? (bottom - top) * scale : extent.height;
        VkViewport viewport{(extent.width - width) / 2, (extent.height - height) / 2, width, height, 0, 1};
        VkRect2D scissor{{0, 0}, extent}; v.CmdSetViewport(command, 0, 1, &viewport); v.CmdSetScissor(command, 0, 1, &scissor);
        v.CmdBindPipeline(command, VK_PIPELINE_BIND_POINT_GRAPHICS, input.pipeline);
        v.CmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_GRAPHICS, input.pipelineLayout, 0, 1, &input.set, 0, nullptr);
        float crop[] = {static_cast<float>(left) / input.desc.width, static_cast<float>(top) / input.desc.height,
                        static_cast<float>(right) / input.desc.width, static_cast<float>(bottom) / input.desc.height};
        if (scaleMode == 1) {
            float sourceAspect = static_cast<float>(right - left) / (bottom - top);
            float targetAspect = static_cast<float>(extent.width) / extent.height;
            if (sourceAspect > targetAspect) {
                float trim = (crop[2] - crop[0]) * (1 - targetAspect / sourceAspect) / 2; crop[0] += trim; crop[2] -= trim;
            } else {
                float trim = (crop[3] - crop[1]) * (1 - sourceAspect / targetAspect) / 2; crop[1] += trim; crop[3] -= trim;
            }
        }
        v.CmdPushConstants(command, input.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(crop), crop);
        v.CmdDraw(command, 3, 1, 0, 0); v.CmdEndRenderPass(command);
        VkImageMemoryBarrier relinquish = acquire; relinquish.srcQueueFamilyIndex = family; relinquish.dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
        relinquish.oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; relinquish.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        relinquish.srcAccessMask = VK_ACCESS_SHADER_READ_BIT; relinquish.dstAccessMask = 0;
        v.CmdPipelineBarrier(command, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &relinquish);
        check(v.EndCommandBuffer(command), "End presentation");
        check(v.ResetFences(device, 1, &completion), "Reset completion");
        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO}; submit.waitSemaphoreCount = 1; submit.pWaitSemaphores = &acquired; submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1; submit.pCommandBuffers = &command; submit.signalSemaphoreCount = 1; submit.pSignalSemaphores = &finished[index];
        check(v.QueueSubmit(queue, 1, &submit, completion), "Submit video sampling");
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR}; present.waitSemaphoreCount = 1; present.pWaitSemaphores = &finished[index];
        present.swapchainCount = 1; present.pSwapchains = &swapchain; present.pImageIndices = &index;
        result = v.QueuePresentKHR(queue, &present);
        // Image ownership must remain with this consumer until GPU sampling and
        // the FOREIGN release complete, even if presentation reports an error.
        check(v.WaitForFences(device, 1, &completion, VK_TRUE, UINT64_MAX), "Finish imported-buffer sampling");
        if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) recreate = true;
        else check(result, "Present video frame");
    }
    ~Renderer() {
        if (device) {
            if (v.DeviceWaitIdle) v.DeviceWaitIdle(device);
            destroySwapchain();
            if (vertex) v.DestroyShaderModule(device, vertex, nullptr);
            if (fragment) v.DestroyShaderModule(device, fragment, nullptr);
            if (completion) v.DestroyFence(device, completion, nullptr);
            if (acquired) v.DestroySemaphore(device, acquired, nullptr);
            if (pool) v.DestroyCommandPool(device, pool, nullptr);
            if (v.DestroyDevice) v.DestroyDevice(device, nullptr);
        }
        if (surface && v.DestroySurfaceKHR) v.DestroySurfaceKHR(instance, surface, nullptr);
        if (instance && v.DestroyInstance) v.DestroyInstance(instance, nullptr);
        if (window) ANativeWindow_release(window);
        if (android) dlclose(android);
    }
};
std::mutex handlesMutex;
std::unordered_map<jlong, std::shared_ptr<Renderer>> handles;
jlong nextHandle = 1;
std::shared_ptr<Renderer> get(jlong handle) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    auto it = handles.find(handle); if (it == handles.end()) throw std::runtime_error("Vulkan display is closed"); return it->second;
}
void exception(JNIEnv* env, const std::exception& e) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what()); }
}
extern "C" JNIEXPORT jlong JNICALL Java_com_limelight_binding_video_ahb_HardwareBufferPresenter_nativeOpen(JNIEnv* env, jclass, jobject target) {
    try { auto r = std::make_shared<Renderer>(); r->initialize(env, target); std::lock_guard<std::mutex> lock(handlesMutex); jlong id = nextHandle++; handles.emplace(id, r); return id; }
    catch (const std::exception& e) { exception(env, e); return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_com_limelight_binding_video_ahb_HardwareBufferPresenter_nativePresent(JNIEnv* env, jclass, jlong id, jobject image, jint l, jint t, jint r, jint b, jint model, jint range, jint scaleMode) {
    try { get(id)->present(env, image, l, t, r, b, model, range, scaleMode); } catch (const std::exception& e) { exception(env, e); }
}
extern "C" JNIEXPORT void JNICALL Java_com_limelight_binding_video_ahb_HardwareBufferPresenter_nativeClose(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> lock(handlesMutex); handles.erase(id);
}
