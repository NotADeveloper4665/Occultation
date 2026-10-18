#!/usr/bin/env python3
"""Fetch hash-pinned upstream RIFE/ncnn assets and prepare the Android build."""
import concurrent.futures, hashlib, json, os, pathlib, re, shutil, sys, urllib.request, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = json.loads((ROOT / 'scripts/interpolation/dependencies.json').read_text())
OUT = ROOT / 'app/build/interpolation'
CACHE = OUT / 'downloads'
ASSETS = OUT / 'assets/interpolation'

def digest(path):
    h = hashlib.sha256()
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()

def fetch(item):
    target = CACHE / item['sha256']
    if target.exists() and digest(target) == item['sha256']: return target
    CACHE.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.part')
    with urllib.request.urlopen(item['url'], timeout=60) as response, open(temporary, 'wb') as stream:
        shutil.copyfileobj(response, stream)
    if digest(temporary) != item['sha256']:
        temporary.unlink(); raise RuntimeError('Checksum mismatch: ' + item['url'])
    temporary.replace(target)
    return target

def patch_rife(source):
    start = source.index('static void load_param_model(ncnn::Net& net, const std::string&')
    end = source.index('\n#endif', start)
    source = source[:start] + '''static int load_param_model(ncnn::Net& net, const std::string& modeldir, const char* name)
{
    const std::string base = modeldir + "/" + name;
    if (net.load_param((base + ".param").c_str()) != 0) return -1;
    if (net.load_model((base + ".bin").c_str()) != 0) return -1;
    for (const ncnn::Layer* layer : net.layers()) {
        if (!layer->support_vulkan && layer->type != "Input" && layer->type != "Split") return -1;
    }
    return 0;
}''' + source[end:]
    # Only the Android/non-Windows helper returns status; Windows is not built here.
    source = re.sub(r'    load_param_model\(([^;]*"[^;]*)\);', r'    if (load_param_model(\1) != 0) return -1;', source)
    # The selected v4 GPU path has no CPU fallback and checks every extractor call.
    start = source.index('int RIFE::process_v4(')
    end = source.index('int RIFE::process_v4_cpu(', start)
    part = source[start:end]
    part = part.replace('return process_cpu(in0image, in1image, timestep, outimage);', 'return -1;')
    marker = 'ncnn::VkAllocator* staging_vkallocator = vkdev->acquire_staging_allocator();'
    assert part.count(marker) == 1
    part = part.replace(marker, marker + '''
    struct AllocatorGuard {
        ncnn::VulkanDevice* device;
        ncnn::VkAllocator* blob;
        ncnn::VkAllocator* staging;
        ~AllocatorGuard() { device->reclaim_blob_allocator(blob); device->reclaim_staging_allocator(staging); }
    } allocatorGuard{vkdev, blob_vkallocator, staging_vkallocator};''')
    part = part.replace('    vkdev->reclaim_blob_allocator(blob_vkallocator);', '')
    part = part.replace('    vkdev->reclaim_staging_allocator(staging_vkallocator);', '')
    part = re.sub(r'(?m)^(\s*)ex\.(input|extract)\(([^\n]+)\);', r'\1if (ex.\2(\3) != 0) return -1;', part)
    part = part.replace('cmd.submit_and_wait();', 'if (cmd.submit_and_wait() != 0) return -1;')
    return source[:start] + part + source[end:]

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    items = LOCK['files'] + [LOCK['ncnn'], LOCK['lite']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        paths = list(pool.map(fetch, items))
    for item, cached in zip(LOCK['files'], paths):
        relative = item['path']
        if relative.startswith('src/'):
            target = OUT / 'rife' / pathlib.Path(relative).name
        elif relative.startswith('models/'):
            target = ASSETS / 'rife-v4' / pathlib.Path(relative).name
        else: target = ASSETS / pathlib.Path(relative).name
        target.parent.mkdir(parents=True, exist_ok=True)
        if relative == 'src/rife.cpp': target.write_text(patch_rife(cached.read_text()))
        else: shutil.copyfile(cached, target)
    # Keep Android and host C library include roots separate in other build steps.
    with zipfile.ZipFile(paths[-2]) as archive:
        for member in archive.infolist():
            parts = pathlib.PurePosixPath(member.filename).parts[1:]
            if '..' in parts: raise RuntimeError('Unsafe SDK archive path')
            if not parts or member.is_dir(): continue
            target = OUT / 'ncnn' / pathlib.Path(*parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists(): target.write_bytes(archive.read(member))
    model = ASSETS / 'rife-lite-4.25.onnx'
    if not model.exists() or digest(model) != LOCK['lite']['model_sha256']:
        import py7zr
        unpack = OUT / 'lite-unpack'
        with py7zr.SevenZipFile(paths[-1]) as archive:
            archive.extract(path=unpack, targets=[LOCK['lite']['entry']])
        shutil.copyfile(unpack / LOCK['lite']['entry'], model)
        if digest(model) != LOCK['lite']['model_sha256']: raise RuntimeError('Lite model checksum mismatch')
        shutil.rmtree(unpack)
    for shader in (OUT / 'rife').glob('*.comp'):
        text = shader.read_text(); text = text[text.index('#version'):]
        text = re.sub(r'\n +', '\n', text)
        data = ','.join('0x%02x' % byte for byte in text.encode())
        (OUT / 'rife' / (shader.name + '.hex.h')).write_text('static const char ' + shader.stem + '_comp_data[] = {' + data + '};\n')
    (ASSETS / 'model-provenance.json').write_text(json.dumps(LOCK, indent=2) + '\n')
    print('Prepared hash-verified RIFE v4 Vulkan and RIFE Lite 4.25 ONNX assets')

if __name__ == '__main__': main()
