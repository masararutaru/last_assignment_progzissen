#!/usr/bin/env python3
"""
データセット取得スクリプト

Aida Calculus Math Handwriting Dataset または MathWriting Dataset を取得します。
"""

import argparse
import os
import sys
import requests
import zipfile
import tarfile
from pathlib import Path
from tqdm import tqdm


# 検出対象クラス（19クラス）
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',  # 数字（10クラス）
    '+', '-', '*', '/', '=',                            # 演算子（5クラス）
    'x',                                                 # 変数（1クラス）
    '(', ')'                                             # 括弧（2クラス）
]


def download_file(url: str, output_path: Path, desc: str = None):
    """ファイルをダウンロード（プログレスバー付き）"""
    response = requests.get(url, stream=True)
    response.raise_for_status()
    
    total_size = int(response.headers.get('content-length', 0))
    
    with open(output_path, 'wb') as f, tqdm(
        desc=desc or output_path.name,
        total=total_size,
        unit='B',
        unit_scale=True,
        unit_divisor=1024,
    ) as pbar:
        for chunk in response.iter_content(chunk_size=8192):
            f.write(chunk)
            pbar.update(len(chunk))


def extract_archive(archive_path: Path, output_dir: Path):
    """アーカイブを展開"""
    print(f"展開中: {archive_path} -> {output_dir}")
    
    if archive_path.suffix == '.zip':
        with zipfile.ZipFile(archive_path, 'r') as zip_ref:
            zip_ref.extractall(output_dir)
    elif archive_path.suffix in ['.tar', '.tar.gz', '.tgz']:
        with tarfile.open(archive_path, 'r:*') as tar_ref:
            tar_ref.extractall(output_dir)
    else:
        raise ValueError(f"未対応のアーカイブ形式: {archive_path.suffix}")
    
    print(f"展開完了: {output_dir}")


def download_aida(output_dir: Path):
    """
    Aida Calculus Math Handwriting Dataset を取得
    
    注意: 実際のURLは要確認。ここではプレースホルダーです。
    """
    print("=" * 60)
    print("Aida Calculus Math Handwriting Dataset の取得")
    print("=" * 60)
    
    # TODO: 実際のURLを確認して更新
    # 参考: https://github.com/MaliParag/CalcForm など
    url = "https://example.com/aida-dataset.zip"  # プレースホルダー
    
    output_dir.mkdir(parents=True, exist_ok=True)
    archive_path = output_dir / "aida.zip"
    
    print(f"URL: {url}")
    print(f"出力先: {output_dir}")
    
    if archive_path.exists():
        print(f"既に存在します: {archive_path}")
        response = input("再ダウンロードしますか？ (y/N): ")
        if response.lower() != 'y':
            return
    
    try:
        download_file(url, archive_path, desc="Aida Dataset")
        extract_archive(archive_path, output_dir)
        archive_path.unlink()  # アーカイブを削除
        print("✅ ダウンロード完了")
    except requests.exceptions.RequestException as e:
        print(f"❌ ダウンロードエラー: {e}")
        print("\n手動でダウンロードしてください:")
        print(f"1. {url} にアクセス")
        print(f"2. ダウンロードしたファイルを {output_dir} に配置")
        sys.exit(1)


def download_mathwriting(output_dir: Path):
    """
    MathWriting Dataset を取得
    
    注意: 実際のURLは要確認。ここではプレースホルダーです。
    """
    print("=" * 60)
    print("MathWriting Dataset の取得")
    print("=" * 60)
    
    # TODO: 実際のURLを確認して更新
    # 参考: https://github.com/google-research-datasets/math-writing
    url = "https://example.com/mathwriting-dataset.tar.gz"  # プレースホルダー
    
    output_dir.mkdir(parents=True, exist_ok=True)
    archive_path = output_dir / "mathwriting.tar.gz"
    
    print(f"URL: {url}")
    print(f"出力先: {output_dir}")
    
    if archive_path.exists():
        print(f"既に存在します: {archive_path}")
        response = input("再ダウンロードしますか？ (y/N): ")
        if response.lower() != 'y':
            return
    
    try:
        download_file(url, archive_path, desc="MathWriting Dataset")
        extract_archive(archive_path, output_dir)
        archive_path.unlink()  # アーカイブを削除
        print("✅ ダウンロード完了")
    except requests.exceptions.RequestException as e:
        print(f"❌ ダウンロードエラー: {e}")
        print("\n手動でダウンロードしてください:")
        print(f"1. {url} にアクセス")
        print(f"2. ダウンロードしたファイルを {output_dir} に配置")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="データセット取得スクリプト"
    )
    parser.add_argument(
        '--dataset',
        choices=['aida', 'mathwriting', 'all'],
        required=True,
        help='取得するデータセット'
    )
    parser.add_argument(
        '--output',
        type=Path,
        default=Path(__file__).parent / 'raw',
        help='出力ディレクトリ（デフォルト: python/data/raw）'
    )
    
    args = parser.parse_args()
    
    if args.dataset == 'aida' or args.dataset == 'all':
        download_aida(args.output / 'aida')
    
    if args.dataset == 'mathwriting' or args.dataset == 'all':
        download_mathwriting(args.output / 'mathwriting')
    
    print("\n" + "=" * 60)
    print("次のステップ:")
    print("=" * 60)
    print("1. データセットの前処理を実行:")
    print("   python data/prepare.py --source raw/aida --target processed/train --classes 19")
    print("2. データセット統計を確認:")
    print("   python data/prepare.py --source processed/train --stats")


if __name__ == '__main__':
    main()
