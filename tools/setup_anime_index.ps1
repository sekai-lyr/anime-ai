$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv-clip\Scripts\python.exe"
if (-not (Test-Path $python)) {
    python -m venv (Join-Path $root ".venv-clip")
}
& $python -m pip install --disable-pip-version-check numpy==2.2.6 pillow==11.3.0 onnxruntime==1.22.1
$modelDir = Join-Path $root "data\clip-model"
New-Item -ItemType Directory -Force $modelDir | Out-Null
$model = Join-Path $modelDir "vision_model.onnx"
if (-not (Test-Path $model)) {
    Invoke-WebRequest "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model.onnx?download=true" -OutFile $model
}
& $python (Join-Path $PSScriptRoot "anime_index.py") build --pages 20
