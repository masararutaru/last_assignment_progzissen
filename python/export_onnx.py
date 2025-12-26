"""
ONNX形式へのモデル書き出しスクリプト（YOLOv5対応）

使い方:
    # プロジェクトルートから実行（推奨）
    # Dockerコンテナ内: cd /app
    # ローカル環境: プロジェクトルート（pom.xmlがあるディレクトリ）に移動
    
    # assetsディレクトリ内のptファイルから変換（--output省略可、自動でassets/model.onnxに出力）
    python python/export_onnx.py \\
      --checkpoint assets/best.pt \\
      --verify
    
    # 別のパスから変換
    python python/export_onnx.py \\
      --checkpoint assets/model.pt \\
      --output assets/model.onnx \\
      --verify

注意:
    - YOLOv5の.ptファイルをONNX形式に変換します
    - YOLOv5公式のexport.pyを使用します（ultralytics/YOLOv8は使用しません）
    - NMS や TopK などの後処理はモデル内部に入れず、Java側で実装すること
    - 入力サイズは640x640を推奨（YOLOv5のデフォルト）
    - プロジェクトルートから実行すると、自動的にassets/model.onnxに出力されます
    - ptファイルはassetsディレクトリに配置してください
"""

import argparse
import sys
import subprocess
import shutil
from pathlib import Path


def find_project_root(start_path: Path = None) -> Path:
    """
    プロジェクトルートディレクトリを検出する
    
    Args:
        start_path: 検索開始パス（デフォルト: スクリプトの場所）
    
    Returns:
        プロジェクトルートのパス
    """
    if start_path is None:
        start_path = Path(__file__).resolve().parent
    
    current = start_path.resolve()
    
    # プロジェクトルートの目印（pom.xml や docker-compose.yml があるディレクトリ）
    markers = ['pom.xml', 'docker-compose.yml', '.git']
    
    while current != current.parent:
        for marker in markers:
            if (current / marker).exists():
                return current
        current = current.parent
    
    # 見つからない場合は、スクリプトの2階層上（python/export_onnx.py → プロジェクトルート）
    return start_path.parent.parent


def find_yolov5_repo(project_root: Path) -> Path:
    """
    YOLOv5リポジトリのパスを検出する
    
    Args:
        project_root: プロジェクトルートのパス
    
    Returns:
        YOLOv5リポジトリのパス
    """
    # Docker環境では /app/yolov5 にクローンされている
    # ローカル環境では project_root/yolov5 を探す
    possible_paths = [
        Path("/app/yolov5"),  # Docker環境
        project_root / "yolov5",  # ローカル環境
        Path.cwd() / "yolov5",  # カレントディレクトリ
    ]
    
    for yolov5_path in possible_paths:
        if yolov5_path.exists() and (yolov5_path / "export.py").exists():
            return yolov5_path
    
    raise FileNotFoundError(
        "YOLOv5 repository not found. Expected locations:\n"
        "  - /app/yolov5 (Docker environment)\n"
        "  - {project_root}/yolov5 (Local environment)\n"
        "Please ensure YOLOv5 is cloned:\n"
        "  git clone https://github.com/ultralytics/yolov5.git"
    )


def export_yolov5_to_onnx(
    checkpoint_path: Path,
    output_path: Path,
    yolov5_repo_path: Path,
    imgsz: int = 640,
    opset_version: int = 17,
    simplify: bool = True,
):
    """
    YOLOv5モデルをONNX形式に書き出す（YOLOv5公式export.pyを使用）
    
    Args:
        checkpoint_path: .ptファイルのパス
        output_path: 出力ONNXファイルのパス
        yolov5_repo_path: YOLOv5リポジトリのパス
        imgsz: 入力画像サイズ（デフォルト640）
        opset_version: ONNX opsetバージョン（デフォルト17）
        simplify: ONNX Simplifierを使用するか（デフォルトTrue）
    
    Returns:
        なし（ファイルに書き出す）
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"[INFO] Exporting YOLOv5 model to ONNX using official export.py...")
    print(f"  Checkpoint: {checkpoint_path}")
    print(f"  Input size: {imgsz}x{imgsz}")
    print(f"  Output path: {output_path}")
    print(f"  Opset version: {opset_version}")
    
    try:
        # YOLOv5公式のexport.pyを実行
        export_script = yolov5_repo_path / "export.py"
        if not export_script.exists():
            raise FileNotFoundError(
                f"YOLOv5 export.py not found at: {export_script}"
            )
        
        # export.pyの引数を構築
        cmd = [
            sys.executable,
            str(export_script),
            "--weights", str(checkpoint_path),
            "--imgsz", str(imgsz),
            "--opset", str(opset_version),
            "--include", "onnx",
        ]
        
        if simplify:
            cmd.append("--simplify")
        
        # 実行
        print(f"[INFO] Running: {' '.join(cmd)}")
        result = subprocess.run(
            cmd,
            cwd=str(yolov5_repo_path),
            capture_output=True,
            text=True,
            check=False
        )
        
        if result.returncode != 0:
            print(f"[ERROR] YOLOv5 export.py failed:")
            print(result.stderr)
            raise RuntimeError(f"Export failed with return code {result.returncode}")
        
        # 出力ファイルを探す（export.pyはチェックポイントと同じディレクトリに出力する）
        checkpoint_dir = checkpoint_path.parent
        checkpoint_stem = checkpoint_path.stem
        exported_path = checkpoint_dir / f"{checkpoint_stem}.onnx"
        
        # 見つからない場合、yolov5リポジトリ内を探す
        if not exported_path.exists():
            possible_paths = [
                yolov5_repo_path / f"{checkpoint_stem}.onnx",
                yolov5_repo_path / "runs" / "train" / "exp" / f"{checkpoint_stem}.onnx",
            ]
            for path in possible_paths:
                if path.exists():
                    exported_path = path
                    break
        
        if not exported_path.exists():
            raise FileNotFoundError(
                f"ONNX file not found after export. "
                f"Checked: {exported_path} and other locations. "
                "Check YOLOv5 export.py output."
            )
        
        # 指定された場所に移動（必要に応じて）
        if exported_path.resolve() != output_path.resolve():
            shutil.move(str(exported_path), str(output_path))
            print(f"[OK] Moved ONNX file to: {output_path}")
        else:
            print(f"[OK] ONNX file already at: {output_path}")
        
        # ファイルサイズを表示
        size_mb = output_path.stat().st_size / (1024 * 1024)
        print(f"  File size: {size_mb:.2f} MB")
        
    except Exception as e:
        print(f"[ERROR] ONNX export failed: {e}")
        import traceback
        traceback.print_exc()
        raise


def verify_onnx(onnx_path: str):
    """
    ONNXファイルを検証する（onnxruntimeで読み込めるか確認）
    
    Args:
        onnx_path: ONNXファイルのパス
    """
    try:
        import onnxruntime as ort
        
        print(f"[INFO] Verifying ONNX file: {onnx_path}")
        
        # ONNXファイルを読み込んで検証
        session = ort.InferenceSession(onnx_path)
        
        # 入力・出力情報を表示
        print("  Inputs:")
        for inp in session.get_inputs():
            print(f"    {inp.name}: shape={inp.shape}, dtype={inp.type}")
        
        print("  Outputs:")
        for out in session.get_outputs():
            print(f"    {out.name}: shape={out.shape}, dtype={out.type}")
        
        print("[OK] ONNX file is valid")
        
    except ImportError:
        print("[WARN] onnxruntime not installed, skipping verification")
    except Exception as e:
        print(f"[ERROR] ONNX verification failed: {e}")
        raise


def main():
    parser = argparse.ArgumentParser(
        description='Export YOLOv5 model to ONNX format',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # プロジェクトルートから実行（推奨）
  # Dockerコンテナ内: cd /app
  # ローカル環境: プロジェクトルート（pom.xmlがあるディレクトリ）に移動
  
  # assetsディレクトリ内のptファイルから変換（--output省略可、自動でassets/model.onnxに出力）
  python python/export_onnx.py \\
    --checkpoint assets/best.pt \\
    --verify
  
  # 別のパスから変換
  python python/export_onnx.py \\
    --checkpoint assets/model.pt \\
    --output assets/model.onnx \\
    --verify
  
  # python/ディレクトリから実行する場合
  cd python
  python export_onnx.py \\
    --checkpoint ../assets/best.pt \\
    --output ../assets/model.onnx \\
    --verify

Note:
  - This script uses YOLOv5 official export.py (not ultralytics/YOLOv8)
  - YOLOv5 weights are NOT forward compatible with YOLOv8
        """
    )
    
    parser.add_argument(
        '--checkpoint',
        type=str,
        required=True,
        help='Path to local PyTorch checkpoint file (.pt). Recommended: assets/best.pt or assets/model.pt'
    )
    
    parser.add_argument(
        '--output',
        type=str,
        default=None,
        help='Output ONNX file path (default: auto-detected project_root/assets/model.onnx)'
    )
    parser.add_argument(
        '--imgsz',
        type=int,
        default=640,
        help='Input image size (default: 640, YOLOv5 standard)'
    )
    parser.add_argument(
        '--opset',
        type=int,
        default=17,
        help='ONNX opset version (default: 17)'
    )
    parser.add_argument(
        '--no-simplify',
        action='store_true',
        help='Disable ONNX Simplifier (default: enabled)'
    )
    parser.add_argument(
        '--verify',
        action='store_true',
        help='Verify ONNX file after export'
    )
    
    args = parser.parse_args()
    
    # プロジェクトルートを検出
    project_root = find_project_root()
    
    # 出力パスの解決（デフォルト値の設定）
    if args.output is None:
        args.output = str(project_root / "assets" / "model.onnx")
    
    # 相対パスの場合はプロジェクトルート基準に変換
    output_path = Path(args.output)
    if not output_path.is_absolute():
        output_path = project_root / output_path
    
    args.output = str(output_path.resolve())
    
    print(f"[INFO] Project root: {project_root}")
    print(f"[INFO] Output path: {args.output}")
    
    # チェックポイントパスの解決
    checkpoint_path = Path(args.checkpoint)
    
    # 相対パスの場合はプロジェクトルート基準に変換
    if not checkpoint_path.is_absolute():
        checkpoint_path = project_root / checkpoint_path
    
    checkpoint_path = checkpoint_path.resolve()
    
    if not checkpoint_path.exists():
        raise FileNotFoundError(
            f"Checkpoint not found: {checkpoint_path}\n"
            f"Please place your .pt file in the assets directory (e.g., assets/best.pt)"
        )
    
    # YOLOv5リポジトリのパスを取得
    yolov5_repo_path = find_yolov5_repo(project_root)
    print(f"[INFO] YOLOv5 repository: {yolov5_repo_path}")
    
    # ONNXに書き出す（YOLOv5公式export.pyを使用）
    output_path = Path(args.output)
    export_yolov5_to_onnx(
        checkpoint_path=checkpoint_path,
        output_path=output_path,
        yolov5_repo_path=yolov5_repo_path,
        imgsz=args.imgsz,
        opset_version=args.opset,
        simplify=not args.no_simplify,
    )
    
    # 検証（オプション）
    if args.verify:
        verify_onnx(str(output_path))


if __name__ == '__main__':
    main()
