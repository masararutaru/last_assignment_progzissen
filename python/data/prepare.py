#!/usr/bin/env python3
"""
データセット前処理スクリプト

公開データセットから19クラス分だけ抽出し、YOLO形式に変換します。
"""

import argparse
import json
import shutil
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Tuple
import cv2
import numpy as np
from tqdm import tqdm


# 検出対象クラス（19クラス）
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',  # 数字（10クラス）
    '+', '-', '*', '/', '=',                            # 演算子（5クラス）
    'x',                                                 # 変数（1クラス）
    '(', ')'                                             # 括弧（2クラス）
]

CLASS_TO_ID = {cls: idx for idx, cls in enumerate(CLASSES)}


def parse_aida_format(source_dir: Path) -> List[Dict]:
    """
    Aida形式のデータセットをパース
    
    注意: 実際のフォーマットに合わせて実装が必要です。
    ここではプレースホルダーです。
    """
    annotations = []
    
    # TODO: 実際のフォーマットに合わせて実装
    # 例: JSONファイル、XMLファイル、CSVファイルなど
    
    return annotations


def parse_mathwriting_format(source_dir: Path) -> List[Dict]:
    """
    MathWriting形式のデータセットをパース
    
    注意: 実際のフォーマットに合わせて実装が必要です。
    ここではプレースホルダーです。
    """
    annotations = []
    
    # TODO: 実際のフォーマットに合わせて実装
    # 例: LaTeX → レンダリング → BBox生成
    
    return annotations


def convert_to_yolo_format(
    bbox: List[float],
    img_width: int,
    img_height: int
) -> Tuple[float, float, float, float]:
    """
    バウンディングボックスをYOLO形式に変換
    
    Args:
        bbox: [x1, y1, x2, y2] 形式の座標
        img_width: 画像の幅
        img_height: 画像の高さ
    
    Returns:
        (center_x, center_y, width, height) 正規化済み（0.0-1.0）
    """
    x1, y1, x2, y2 = bbox
    
    # 中心座標とサイズを計算
    center_x = (x1 + x2) / 2.0 / img_width
    center_y = (y1 + y2) / 2.0 / img_height
    width = (x2 - x1) / img_width
    height = (y2 - y1) / img_height
    
    # 0.0-1.0の範囲にクリップ
    center_x = max(0.0, min(1.0, center_x))
    center_y = max(0.0, min(1.0, center_y))
    width = max(0.0, min(1.0, width))
    height = max(0.0, min(1.0, height))
    
    return center_x, center_y, width, height


def filter_classes(annotations: List[Dict], target_classes: List[str]) -> List[Dict]:
    """19クラス分だけ抽出"""
    filtered = []
    
    for ann in annotations:
        cls = ann.get('class', ann.get('label', ''))
        if cls in target_classes:
            filtered.append(ann)
    
    return filtered


def process_dataset(
    source_dir: Path,
    target_dir: Path,
    min_instances: int = 10
):
    """
    データセットを前処理してYOLO形式に変換
    
    Args:
        source_dir: ソースデータセットのディレクトリ
        target_dir: 出力先ディレクトリ
        min_instances: クラスあたりの最小インスタンス数
    """
    print("=" * 60)
    print("データセット前処理")
    print("=" * 60)
    print(f"ソース: {source_dir}")
    print(f"出力先: {target_dir}")
    print(f"対象クラス: {len(CLASSES)}クラス ({', '.join(CLASSES)})")
    
    # 出力ディレクトリを作成
    target_dir.mkdir(parents=True, exist_ok=True)
    images_dir = target_dir / 'images'
    labels_dir = target_dir / 'labels'
    images_dir.mkdir(exist_ok=True)
    labels_dir.mkdir(exist_ok=True)
    
    # データセット形式を自動検出（簡易版）
    # TODO: 実際のフォーマットに合わせて実装
    
    # 統計情報
    stats = defaultdict(int)
    processed_count = 0
    skipped_count = 0
    
    # プレースホルダー: 実際のデータセット読み込み処理
    print("\n⚠️  注意: このスクリプトはプレースホルダーです。")
    print("実際のデータセットフォーマットに合わせて実装が必要です。")
    print("\n実装が必要な関数:")
    print("  - parse_aida_format()")
    print("  - parse_mathwriting_format()")
    print("  - データセット固有の読み込み処理")
    
    # サンプル処理（実際のデータセットに合わせて修正）
    # annotations = parse_aida_format(source_dir)
    # filtered = filter_classes(annotations, CLASSES)
    
    # for ann in tqdm(filtered, desc="処理中"):
    #     # 画像をコピー
    #     img_path = source_dir / ann['image_path']
    #     if not img_path.exists():
    #         skipped_count += 1
    #         continue
    #     
    #     img = cv2.imread(str(img_path))
    #     if img is None:
    #         skipped_count += 1
    #         continue
    #     
    #     img_height, img_width = img.shape[:2]
    #     
    #     # ラベルファイルを作成
    #     label_path = labels_dir / f"{ann['image_id']}.txt"
    #     with open(label_path, 'w') as f:
    #         for bbox_info in ann['bboxes']:
    #             cls = bbox_info['class']
    #             if cls not in CLASS_TO_ID:
    #                 continue
    #             
    #             class_id = CLASS_TO_ID[cls]
    #             bbox = bbox_info['bbox']
    #             center_x, center_y, width, height = convert_to_yolo_format(
    #                 bbox, img_width, img_height
    #             )
    #             
    #             f.write(f"{class_id} {center_x:.6f} {center_y:.6f} {width:.6f} {height:.6f}\n")
    #             stats[cls] += 1
    #     
    #     # 画像をコピー
    #     shutil.copy(img_path, images_dir / f"{ann['image_id']}.png")
    #     processed_count += 1
    
    print("\n" + "=" * 60)
    print("処理完了")
    print("=" * 60)
    print(f"処理済み: {processed_count} 画像")
    print(f"スキップ: {skipped_count} 画像")
    print("\nクラス別統計:")
    for cls in CLASSES:
        count = stats[cls]
        print(f"  {cls:>3}: {count:>6} インスタンス")
    
    # 最小インスタンス数のチェック
    print("\n最小インスタンス数チェック:")
    for cls in CLASSES:
        count = stats[cls]
        if count < min_instances:
            print(f"  ⚠️  {cls}: {count} < {min_instances} (不足)")


def show_stats(source_dir: Path):
    """データセット統計を表示"""
    print("=" * 60)
    print("データセット統計")
    print("=" * 60)
    print(f"ソース: {source_dir}")
    
    labels_dir = source_dir / 'labels'
    if not labels_dir.exists():
        print(f"❌ ラベルディレクトリが見つかりません: {labels_dir}")
        return
    
    stats = defaultdict(int)
    total_images = 0
    
    for label_file in tqdm(list(labels_dir.glob('*.txt')), desc="統計収集中"):
        total_images += 1
        with open(label_file, 'r') as f:
            for line in f:
                parts = line.strip().split()
                if len(parts) >= 5:
                    class_id = int(parts[0])
                    if 0 <= class_id < len(CLASSES):
                        stats[CLASSES[class_id]] += 1
    
    print(f"\n総画像数: {total_images}")
    print("\nクラス別統計:")
    for cls in CLASSES:
        count = stats[cls]
        print(f"  {cls:>3}: {count:>6} インスタンス")
    
    total_instances = sum(stats.values())
    print(f"\n総インスタンス数: {total_instances}")


def main():
    parser = argparse.ArgumentParser(
        description="データセット前処理スクリプト"
    )
    parser.add_argument(
        '--source',
        type=Path,
        help='ソースデータセットのディレクトリ'
    )
    parser.add_argument(
        '--target',
        type=Path,
        help='出力先ディレクトリ'
    )
    parser.add_argument(
        '--classes',
        type=int,
        default=19,
        help='抽出するクラス数（デフォルト: 19）'
    )
    parser.add_argument(
        '--min-instances',
        type=int,
        default=10,
        help='クラスあたりの最小インスタンス数（デフォルト: 10）'
    )
    parser.add_argument(
        '--stats',
        action='store_true',
        help='データセット統計を表示'
    )
    
    args = parser.parse_args()
    
    if args.stats:
        if args.source is None:
            parser.error("--stats を使用する場合は --source を指定してください")
        show_stats(args.source)
    else:
        if args.source is None or args.target is None:
            parser.error("--source と --target を指定してください")
        
        if args.classes != 19:
            print(f"⚠️  警告: クラス数が19以外です。通常は19クラスを使用します。")
        
        process_dataset(
            args.source,
            args.target,
            min_instances=args.min_instances
        )


if __name__ == '__main__':
    main()
