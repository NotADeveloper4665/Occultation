#!/usr/bin/env python3
"""Validate real packaged model graphs, layout and artifact provenance."""
import hashlib, json, pathlib
import onnx
root = pathlib.Path(__file__).resolve().parents[2]
lock = json.loads((root / 'scripts/interpolation/dependencies.json').read_text())
assets = root / 'app/build/interpolation/assets/interpolation'
model = assets / 'rife-lite-4.25.onnx'
assert hashlib.sha256(model.read_bytes()).hexdigest() == lock['lite']['model_sha256']
graph = onnx.load(model)
onnx.checker.check_model(graph)
assert len(graph.graph.input) == 1 and graph.graph.input[0].name == 'input'
assert graph.graph.input[0].type.tensor_type.shape.dim[1].dim_value == 7
assert graph.graph.input[0].type.tensor_type.elem_type == onnx.TensorProto.FLOAT
ops = sorted({node.op_type for node in graph.graph.node})
assert 'GridSample' in ops, 'Review changed RIFE export before enabling NNAPI'
for item in lock['files']:
    if item['path'].startswith('models/'):
        path = assets / item['path'].removeprefix('models/')
        assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']
param = (assets / 'rife-v4/flownet.param').read_text()
assert param.startswith('7767517') and 'Warp' in param
source = (root / 'app/build/interpolation/rife/rife.cpp').read_text()
part = source[source.index('int RIFE::process_v4('):source.index('int RIFE::process_v4_cpu(')]
assert 'return process_cpu(' not in part
assert 'AllocatorGuard' in part and 'if (cmd.submit_and_wait() != 0)' in part
assert 'if (ex.extract(' in part
report = {'model': 'Practical-RIFE 4.25 Lite / vs-mlrt v2 export',
          'sha256': lock['lite']['model_sha256'], 'onnx_operations': ops,
          'nnapi_full_graph_verified': False,
          'known_nnapi_ep_blocker': 'GridSample is absent from the documented NNAPI EP operator list',
          'vulkan_model': 'nihui RIFE v4 (distinct from Lite 4.25)',
          'stream_integration': False}
(assets / 'model-audit.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS: real RIFE model hashes, ONNX graph/layout, ncnn param and GPU error guards')
print('NNAPI full-graph support remains unverified; GridSample requires explicit compatibility work')
