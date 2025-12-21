#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kaggle OCRデータセットの構造を確認するスクリプト

正解ラベルとbboxを記号・数字ごとに表示します。
"""

import json
import sys
import io
from pathlib import Path
from collections import defaultdict
from typing import Dict, List

# 設定ファイルからクラス定義をインポート
sys.path.insert(0, str(Path(__file__).parent.parent))
from config import CLASSES, latex_to_class

# WindowsでのUnicode出力対応
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')


def load_kaggle_json(json_path: Path) -> List[Dict]:
    """Kaggle JSONファイルを読み込む"""
    print(f"読み込み中: {json_path}")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    print(f"  読み込み完了: {len(data)} 件のデータ")
    return data


def inspect_sample(data: List[Dict], sample_idx: int = 0):
    """サンプルデータの構造を確認"""
    if sample_idx >= len(data):
        print(f"エラー: サンプルインデックス {sample_idx} が範囲外です（最大: {len(data)-1}）")
        return
    
    sample = data[sample_idx]
    
    print("\n" + "=" * 80)
    print(f"サンプル {sample_idx + 1} の詳細")
    print("=" * 80)
    
    print(f"\nUUID: {sample.get('uuid', 'N/A')}")
    print(f"\nLaTeX: {sample.get('latex', 'N/A')}")
    unicode_str = sample.get('unicode_str', 'N/A')
    try:
        print(f"Unicode: {unicode_str}")
    except UnicodeEncodeError:
        print(f"Unicode: (表示不可な文字が含まれています)")
    
    if 'image_data' in sample:
        img_data = sample['image_data']
        print(f"\n画像サイズ: {img_data.get('width', 'N/A')} x {img_data.get('height', 'N/A')}")
        print(f"Depth: {img_data.get('depth', 'N/A')}")
        
        # 文字情報
        full_chars = img_data.get('full_latex_chars', [])
        visible_chars = img_data.get('visible_latex_chars', [])
        visible_map = img_data.get('visible_char_map', [])
        
        print(f"\n全文字数: {len(full_chars)}")
        print(f"可視文字数: {len(visible_chars)}")
        
        # 座標情報
        xmins = img_data.get('xmins', [])
        xmaxs = img_data.get('xmaxs', [])
        ymins = img_data.get('ymins', [])
        ymaxs = img_data.get('ymaxs', [])
        
        xmins_raw = img_data.get('xmins_raw', [])
        xmaxs_raw = img_data.get('xmaxs_raw', [])
        ymins_raw = img_data.get('ymins_raw', [])
        ymaxs_raw = img_data.get('ymaxs_raw', [])
        
        print(f"\n座標配列の長さ:")
        print(f"  xmins: {len(xmins)}")
        print(f"  xmaxs: {len(xmaxs)}")
        print(f"  ymins: {len(ymins)}")
        print(f"  ymaxs: {len(ymaxs)}")
        
        # 記号・数字ごとの情報を表示
        print("\n" + "-" * 80)
        print("記号・数字ごとの正解ラベルとBBox（可視文字のみ）")
        print("-" * 80)
        
        if len(visible_chars) > 0:
            print(f"\n{'Index':<6} {'文字':<15} {'LaTeX':<20} {'BBox (正規化)':<30} {'BBox (生座標)':<30}")
            print("-" * 100)
            
            for i, char in enumerate(visible_chars):
                if i < len(xmins) and i < len(xmaxs) and i < len(ymins) and i < len(ymaxs):
                    # 正規化座標
                    xmin_norm = xmins[i] if i < len(xmins) else 0
                    xmax_norm = xmaxs[i] if i < len(xmaxs) else 0
                    ymin_norm = ymins[i] if i < len(ymins) else 0
                    ymax_norm = ymaxs[i] if i < len(ymaxs) else 0
                    
                    # 生座標
                    xmin_raw = xmins_raw[i] if i < len(xmins_raw) else 0
                    xmax_raw = xmaxs_raw[i] if i < len(xmaxs_raw) else 0
                    ymin_raw = ymins_raw[i] if i < len(ymins_raw) else 0
                    ymax_raw = ymaxs_raw[i] if i < len(ymaxs_raw) else 0
                    
                    bbox_norm = f"({xmin_norm:.4f},{ymin_norm:.4f})-({xmax_norm:.4f},{ymax_norm:.4f})"
                    bbox_raw = f"({xmin_raw},{ymin_raw})-({xmax_raw},{ymax_raw})"
                    
                    print(f"{i:<6} {char:<15} {char:<20} {bbox_norm:<30} {bbox_raw:<30}")
        else:
            print("可視文字が見つかりませんでした。")
        
        # 全文字（full_latex_chars）の情報も表示
        print("\n" + "-" * 80)
        print("全文字（full_latex_chars）の情報")
        print("-" * 80)
        print(f"\n{'Index':<6} {'LaTeX文字':<30}")
        print("-" * 40)
        for i, char in enumerate(full_chars[:20]):  # 最初の20文字だけ表示
            print(f"{i:<6} {char:<30}")
        if len(full_chars) > 20:
            print(f"... 他 {len(full_chars) - 20} 文字")


def analyze_char_distribution(data: List[Dict], top_n: int = 30):
    """文字の分布を分析"""
    print("\n" + "=" * 80)
    print("文字分布の分析（可視文字）")
    print("=" * 80)
    
    char_counts = defaultdict(int)
    
    for sample in data:
        if 'image_data' in sample:
            visible_chars = sample['image_data'].get('visible_latex_chars', [])
            for char in visible_chars:
                char_counts[char] += 1
    
    # 頻度順にソート
    sorted_chars = sorted(char_counts.items(), key=lambda x: x[1], reverse=True)
    
    print(f"\n上位 {top_n} 文字:")
    print(f"{'文字':<30} {'出現回数':<15} {'割合 (%)':<15}")
    print("-" * 60)
    
    total = sum(char_counts.values())
    for char, count in sorted_chars[:top_n]:
        percentage = (count / total * 100) if total > 0 else 0
        print(f"{char:<30} {count:<15} {percentage:.2f}%")
    
    print(f"\n総文字数: {total}")
    print(f"ユニーク文字数: {len(char_counts)}")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Kaggle OCRデータセットの構造を確認"
    )
    parser.add_argument(
        '--json',
        type=Path,
        default=Path(__file__).parent.parent / 'raw' / 'aidapearson_ocr' / 'batch_1' / 'JSON' / 'kaggle_data_1.json',
        help='確認するJSONファイルのパス'
    )
    parser.add_argument(
        '--sample',
        type=int,
        default=0,
        help='確認するサンプルのインデックス（デフォルト: 0）'
    )
    parser.add_argument(
        '--analyze',
        action='store_true',
        help='文字分布を分析する'
    )
    
    args = parser.parse_args()
    
    if not args.json.exists():
        print(f"エラー: ファイルが見つかりません: {args.json}")
        return
    
    # JSONファイルを読み込む
    data = load_kaggle_json(args.json)
    
    if len(data) == 0:
        print("エラー: データが空です")
        return
    
    # サンプルデータを確認
    inspect_sample(data, args.sample)
    
    # 文字分布を分析
    if args.analyze:
        analyze_char_distribution(data)


if __name__ == '__main__':
    main()
