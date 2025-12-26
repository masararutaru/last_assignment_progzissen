"""
ONNX形式へのモデル書き出しスクリプト（YOLOv5対応）

使い方:
    # プロジェクトルートから実行（推奨）
    # Dockerコンテナ内: cd /app
    # ローカル環境: プロジェクトルート（pom.xmlがあるディレクトリ）に移動
    
    # S3からダウンロードして変換（--output省略可、自動でassets/model.onnxに出力）
    python python/export_onnx.py \\
      --s3-bucket km62m-ml-storage \\
      --s3-key yolo-dataset/v1/weights/run_xxx/best.pt \\
      --verify
    
    # ローカルファイルから変換
    python python/export_onnx.py \\
      --checkpoint python/weights/best.pt \\
      --output assets/model.onnx \\
      --verify

注意:
    - YOLOv5の.ptファイルをONNX形式に変換します
    - NMS や TopK などの後処理はモデル内部に入れず、Java側で実装すること
    - 入力サイズは640x640を推奨（YOLOv5のデフォルト）
    - プロジェクトルートから実行すると、自動的にassets/model.onnxに出力されます
"""

import argparse
import os
import subprocess
import sys
import torch
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


def download_from_s3(s3_bucket: str, s3_key: str, local_path: Path) -> Path:
    """
    S3からファイルをダウンロードする
    
    Args:
        s3_bucket: S3バケット名
        s3_key: S3キー（パス）
        local_path: ローカル保存先パス
    
    Returns:
        ダウンロードしたファイルのパス
    """
    s3_path = f"s3://{s3_bucket}/{s3_key}"
    
    print(f"[INFO] Downloading from S3...")
    print(f"  S3 path: {s3_path}")
    print(f"  Local path: {local_path}")
    
    # AWS CLIがインストールされているか確認
    try:
        subprocess.run(["aws", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        raise RuntimeError(
            "AWS CLI is not installed or not in PATH. "
            "Please install AWS CLI or use --checkpoint with local file path."
        )
    
    # AWS認証情報の確認
    aws_access_key = os.environ.get("AWS_ACCESS_KEY_ID")
    aws_secret_key = os.environ.get("AWS_SECRET_ACCESS_KEY")
    aws_region = os.environ.get("AWS_DEFAULT_REGION")
    aws_profile = os.environ.get("AWS_PROFILE")
    
    if not aws_access_key and not aws_profile:
        print("[WARN] AWS_ACCESS_KEY_ID not found in environment.")
        print("  Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY, or configure AWS CLI.")
    elif aws_access_key and not aws_secret_key:
        print("[WARN] AWS_SECRET_ACCESS_KEY not found in environment.")
        print("  Set AWS_SECRET_ACCESS_KEY for S3 access.")
    
    if aws_region:
        print(f"[INFO] Using AWS region: {aws_region}")
    elif not aws_profile:
        print("[INFO] AWS_DEFAULT_REGION not set, using default region from AWS CLI config.")
    
    # ダウンロード実行
    try:
        subprocess.run(
            ["aws", "s3", "cp", s3_path, str(local_path)],
            check=True,
            capture_output=True,
            text=True,
        )
        size_mb = local_path.stat().st_size / (1024 * 1024)
        print(f"[OK] Downloaded: {size_mb:.2f} MB")
        return local_path
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] S3 download failed: {e.stderr}")
        raise


def load_yolov5_model(checkpoint_path: Path, device: str = "cpu"):
    """
    YOLOv5モデルをロードする（ultralyticsパッケージを使用）
    
    Args:
        checkpoint_path: .ptファイルのパス
        device: デバイス（'cpu' または 'cuda'）
    
    Returns:
        YOLOモデルインスタンス
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        raise ImportError(
            "ultralytics package is not installed. "
            "Install it with: pip install ultralytics>=8.0.0"
        )
    
    print(f"[INFO] Loading YOLOv5 model from: {checkpoint_path}")
    
    # YOLOモデルをロード
    model = YOLO(str(checkpoint_path))
    
    print(f"[OK] Model loaded successfully")
    print(f"  Model type: {type(model).__name__}")
    
    return model


def export_yolov5_to_onnx(
    model,
    output_path: Path,
    imgsz: int = 640,
    opset_version: int = 17,
    simplify: bool = True,
):
    """
    YOLOv5モデルをONNX形式に書き出す
    
    Args:
        model: ultralytics YOLOモデルインスタンス
        output_path: 出力ONNXファイルのパス
        imgsz: 入力画像サイズ（デフォルト640）
        opset_version: ONNX opsetバージョン（デフォルト17）
        simplify: ONNX Simplifierを使用するか（デフォルトTrue）
    
    Returns:
        なし（ファイルに書き出す）
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"[INFO] Exporting YOLOv5 model to ONNX...")
    print(f"  Input size: {imgsz}x{imgsz}")
    print(f"  Output path: {output_path}")
    print(f"  Opset version: {opset_version}")
    
    try:
        # ultralyticsのexportメソッドを使用
        # これにより、YOLOv5のONNXエクスポートが自動的に処理される
        # export()は出力ファイルのパスを返す
        exported_path = model.export(
            format="onnx",
            imgsz=imgsz,
            opset=opset_version,
            simplify=simplify,
            dynamic=False,  # 固定サイズ（Java側で扱いやすい）
        )
        
        # 出力ファイルのパスを取得
        exported_path = Path(exported_path) if exported_path else None
        
        # 見つからない場合、元のチェックポイントファイルの場所を基準に探す
        if not exported_path or not exported_path.exists():
            # モデルのckpt_path属性から元のファイルパスを取得
            if hasattr(model, 'ckpt_path') and model.ckpt_path:
                checkpoint_path = Path(model.ckpt_path)
                exported_path = checkpoint_path.parent / f"{checkpoint_path.stem}.onnx"
            else:
                # フォールバック: カレントディレクトリを探す
                possible_names = ["yolov5n.onnx", "model.onnx", "best.onnx", "last.onnx"]
                for name in possible_names:
                    candidate = Path.cwd() / name
                    if candidate.exists():
                        exported_path = candidate
                        break
        
        if not exported_path or not exported_path.exists():
            raise FileNotFoundError(
                f"ONNX file not found after export. "
                f"Expected location: {exported_path}. "
                "Check ultralytics export output."
            )
        
        # 指定された場所に移動（必要に応じて）
        if exported_path.resolve() != output_path.resolve():
            import shutil
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
  
  # S3からダウンロードして変換（--output省略可、自動でassets/model.onnxに出力）
  python python/export_onnx.py \\
    --s3-bucket km62m-ml-storage \\
    --s3-key yolo-dataset/v1/weights/run_xxx/best.pt \\
    --verify
  
  # ローカルファイルから変換
  python python/export_onnx.py \\
    --checkpoint python/weights/best.pt \\
    --output assets/model.onnx \\
    --verify
  
  # python/ディレクトリから実行する場合
  cd python
  python export_onnx.py \\
    --s3-bucket km62m-ml-storage \\
    --s3-key yolo-dataset/v1/weights/run_xxx/best.pt \\
    --output ../assets/model.onnx \\
    --verify
        """
    )
    
    # チェックポイントの指定方法（ローカル or S3）
    checkpoint_group = parser.add_mutually_exclusive_group(required=True)
    checkpoint_group.add_argument(
        '--checkpoint',
        type=str,
        help='Path to local PyTorch checkpoint file (.pt)'
    )
    checkpoint_group.add_argument(
        '--s3-bucket',
        type=str,
        help='S3 bucket name (use with --s3-key)'
    )
    
    parser.add_argument(
        '--s3-key',
        type=str,
        help='S3 key (path) to checkpoint file (use with --s3-bucket)'
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
    checkpoint_path = None
    
    if args.checkpoint:
        # ローカルファイル
        checkpoint_path = Path(args.checkpoint)
        if not checkpoint_path.exists():
            raise FileNotFoundError(f"Checkpoint not found: {checkpoint_path}")
    elif args.s3_bucket and args.s3_key:
        # S3からダウンロード
        temp_dir = Path.cwd() / "tmp_weights"
        temp_dir.mkdir(exist_ok=True)
        checkpoint_filename = Path(args.s3_key).name
        local_temp_path = temp_dir / checkpoint_filename
        
        checkpoint_path = download_from_s3(
            s3_bucket=args.s3_bucket,
            s3_key=args.s3_key,
            local_path=local_temp_path,
        )
    else:
        parser.error("Either --checkpoint or both --s3-bucket and --s3-key must be specified")
    
    # YOLOv5モデルをロード
    model = load_yolov5_model(checkpoint_path, device="cpu")
    
    # ONNXに書き出す
    output_path = Path(args.output)
    export_yolov5_to_onnx(
        model=model,
        output_path=output_path,
        imgsz=args.imgsz,
        opset_version=args.opset,
        simplify=not args.no_simplify,
    )
    
    # 検証（オプション）
    if args.verify:
        verify_onnx(str(output_path))
    
    # 一時ファイルのクリーンアップ
    if args.s3_bucket and checkpoint_path.parent.name == "tmp_weights":
        print(f"[INFO] Cleaning up temporary file: {checkpoint_path}")
        checkpoint_path.unlink()
        try:
            checkpoint_path.parent.rmdir()
        except OSError:
            pass  # ディレクトリが空でない場合は無視


if __name__ == '__main__':
    main()
