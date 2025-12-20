"""
ONNX形式へのモデル書き出しスクリプト

使い方:
    python export_onnx.py --checkpoint weights/model.pth --output ../assets/model.onnx

注意:
    - モデルの forward メソッドは ONNX が理解できる演算のみを使用すること
    - 固定サイズの入力・出力を想定すること（可変長は避ける）
    - NMS や TopK などの後処理はモデル内部に入れず、Java側で実装すること
"""

import argparse
import torch
import torch.onnx
from pathlib import Path


def export_to_onnx(
    model,
    output_path: str,
    input_size: tuple = (1, 3, 256, 256),  # (batch, channels, height, width)
    opset_version: int = 17,
    dynamic_axes: dict = None,
):
    """
    モデルをONNX形式に書き出す
    
    Args:
        model: PyTorchモデル（evalモード）
        output_path: 出力ONNXファイルのパス
        input_size: 入力テンソルのサイズ
        opset_version: ONNX opsetバージョン（デフォルト17）
        dynamic_axes: 動的軸の定義（Noneの場合は固定サイズ）
    
    Returns:
        なし（ファイルに書き出す）
    """
    model.eval()
    
    # ダミー入力を作成
    dummy_input = torch.randn(*input_size)
    
    # 出力パスの親ディレクトリを作成
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"[INFO] Exporting model to ONNX...")
    print(f"  Input shape: {input_size}")
    print(f"  Output path: {output_path}")
    print(f"  Opset version: {opset_version}")
    
    try:
        torch.onnx.export(
            model,
            dummy_input,
            str(output_path),
            input_names=['image'],
            output_names=['boxes', 'scores', 'class_ids'],  # モデルの出力に合わせて変更
            opset_version=opset_version,
            dynamic_axes=dynamic_axes,
            do_constant_folding=True,
            verbose=False,
        )
        print(f"[OK] ONNX export successful: {output_path}")
        
        # ファイルサイズを表示
        size_mb = output_path.stat().st_size / (1024 * 1024)
        print(f"  File size: {size_mb:.2f} MB")
        
    except Exception as e:
        print(f"[ERROR] ONNX export failed: {e}")
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
    parser = argparse.ArgumentParser(description='Export PyTorch model to ONNX')
    parser.add_argument(
        '--checkpoint',
        type=str,
        required=True,
        help='Path to PyTorch checkpoint file (.pt or .pth)'
    )
    parser.add_argument(
        '--output',
        type=str,
        default='../assets/model.onnx',
        help='Output ONNX file path (default: ../assets/model.onnx)'
    )
    parser.add_argument(
        '--input-size',
        type=int,
        nargs=4,
        default=[1, 3, 256, 256],
        metavar=('BATCH', 'CHANNELS', 'HEIGHT', 'WIDTH'),
        help='Input tensor size (default: 1 3 256 256)'
    )
    parser.add_argument(
        '--opset',
        type=int,
        default=17,
        help='ONNX opset version (default: 17)'
    )
    parser.add_argument(
        '--verify',
        action='store_true',
        help='Verify ONNX file after export'
    )
    
    args = parser.parse_args()
    
    # チェックポイントを読み込む
    checkpoint_path = Path(args.checkpoint)
    if not checkpoint_path.exists():
        raise FileNotFoundError(f"Checkpoint not found: {checkpoint_path}")
    
    print(f"[INFO] Loading checkpoint: {checkpoint_path}")
    
    # TODO: ここでモデルを定義してロードする
    # 例:
    # from train.model import YourModel
    # model = YourModel(...)
    # checkpoint = torch.load(checkpoint_path, map_location='cpu')
    # model.load_state_dict(checkpoint['model_state_dict'])
    
    # 現在はダミー実装（実際のモデルに置き換える）
    print("[WARN] Using dummy model. Replace with your actual model!")
    model = torch.nn.Identity()  # ダミー
    
    # ONNXに書き出す
    input_size = tuple(args.input_size)
    export_to_onnx(
        model=model,
        output_path=args.output,
        input_size=input_size,
        opset_version=args.opset,
    )
    
    # 検証（オプション）
    if args.verify:
        verify_onnx(args.output)


if __name__ == '__main__':
    main()
