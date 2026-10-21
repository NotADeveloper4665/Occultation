#!/usr/bin/env python3
import os,pathlib,subprocess,struct
root=pathlib.Path(__file__).resolve().parents[2]
ndk=os.environ.get('ANDROID_NDK_HOME') or str(pathlib.Path(os.environ.get('ANDROID_HOME','/nonexistent'))/'ndk/27.0.12077973')
compiler=pathlib.Path(ndk)/'shader-tools/linux-x86_64/glslc'
out=root/'app/build/display';out.mkdir(parents=True,exist_ok=True)
for name in ['video.vert','video.frag']:
 source=root/'app/src/main/jni/display'/name
 dest=out/(name+'.spv')
 subprocess.run([str(compiler),'--target-env=vulkan1.1',str(source),'-o',str(dest)],check=True)
 words=struct.unpack('<'+'I'*(dest.stat().st_size//4),dest.read_bytes())
 (out/(name+'.h')).write_text('static const uint32_t '+name.replace('.','_')+'[] = {'+','.join(hex(x) for x in words)+'};\n')
print('Compiled Vulkan video shaders for Android')
