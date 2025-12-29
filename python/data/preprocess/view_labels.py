#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kaggle OCRデータセットの正解ラベルを記号・数字ごとに表示するスクリプト

各サンプルについて、記号・数字ごとに正解ラベルとbboxを表示します。
"""

import json
import sys
import io
from pathlib import Path
from typing import Dict, List

# 設定ファイルからクラス定義をインポート
sys.path.insert(0, str(Path(__file__).parent.parent))
from config import CLASSES, CLASS_TO_ID, latex_to_class

# WindowsでのUnicode出力対応
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')


def load_kaggle_json(json_path: Path) -> List[Dict]:
    """Kaggle JSONファイルを読み込む"""
    print(f"読み込み中: {json_path}")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    print(f"  読み込み完了: {len(data)} 件のデータ\n")
    return data


def extract_labels_and_bboxes(sample: Dict) -> List[Dict]:
    """
    サンプルから正解ラベルとbboxを抽出
    
    Returns:
        [{'class': str, 'bbox_norm': (xmin, ymin, xmax, ymax), 'bbox_raw': (xmin, ymin, xmax, ymax), 'latex': str}, ...]
    """
    if 'image_data' not in sample:
        return []
    
    img_data = sample['image_data']
    visible_chars = img_data.get('visible_latex_chars', [])
    
    xmins = img_data.get('xmins', [])
    xmaxs = img_data.get('xmaxs', [])
    ymins = img_data.get('ymins', [])
    ymaxs = img_data.get('ymaxs', [])
    
    xmins_raw = img_data.get('xmins_raw', [])
    xmaxs_raw = img_data.get('xmaxs_raw', [])
    ymins_raw = img_data.get('ymins_raw', [])
    ymaxs_raw = img_data.get('ymaxs_raw', [])
    
    results = []
    
    # 関数名リスト（CLASSESから関数名を抽出）
    function_names = ['sin', 'cos', 'tan', 'sec', 'csc', 'cot', 'ln', 'log', 'exp', 'sqrt', 'lim', 'abs']
    
    i = 0
    while i < len(visible_chars):
        # 関数名のマッチングを試行
        matched_function = None
        matched_length = 0
        
        for func_name in function_names:
            if i + len(func_name) <= len(visible_chars):
                # 連続する文字列が関数名と一致するかチェック
                candidate = ''.join(visible_chars[i:i+len(func_name)])
                if candidate == func_name:
                    matched_function = func_name
                    matched_length = len(func_name)
                    break
        
        if matched_function:
            # 関数名として処理
            # 最初と最後のbboxを結合して関数全体のbboxを作成
            start_idx = i
            end_idx = i + matched_length - 1
            
            if (end_idx < len(xmins) and end_idx < len(xmaxs) and 
                end_idx < len(ymins) and end_idx < len(ymaxs)):
                # 正規化座標（関数全体のbbox）
                bbox_norm = (
                    min(xmins[start_idx:end_idx+1]),
                    min(ymins[start_idx:end_idx+1]),
                    max(xmaxs[start_idx:end_idx+1]),
                    max(ymaxs[start_idx:end_idx+1])
                )
                
                # 生座標（関数全体のbbox）
                xmins_raw_slice = [xmins_raw[j] for j in range(start_idx, end_idx+1) if j < len(xmins_raw)]
                ymins_raw_slice = [ymins_raw[j] for j in range(start_idx, end_idx+1) if j < len(ymins_raw)]
                xmaxs_raw_slice = [xmaxs_raw[j] for j in range(start_idx, end_idx+1) if j < len(xmaxs_raw)]
                ymaxs_raw_slice = [ymaxs_raw[j] for j in range(start_idx, end_idx+1) if j < len(ymaxs_raw)]
                
                bbox_raw = (
                    min(xmins_raw_slice) if xmins_raw_slice else 0,
                    min(ymins_raw_slice) if ymins_raw_slice else 0,
                    max(xmaxs_raw_slice) if xmaxs_raw_slice else 0,
                    max(ymaxs_raw_slice) if ymaxs_raw_slice else 0
                )
                
                results.append({
                    'class': matched_function,
                    'bbox_norm': bbox_norm,
                    'bbox_raw': bbox_raw,
                    'latex': matched_function,  # 関数名全体
                    'index': i
                })
            
            i += matched_length
        else:
            # 通常の文字として処理
            latex_char = visible_chars[i]
            class_char = latex_to_class(latex_char)
            
            if class_char is not None:
                if i < len(xmins) and i < len(xmaxs) and i < len(ymins) and i < len(ymaxs):
                    # 正規化座標
                    bbox_norm = (
                        xmins[i],
                        ymins[i],
                        xmaxs[i],
                        ymaxs[i]
                    )
                    
                    # 生座標
                    bbox_raw = (
                        xmins_raw[i] if i < len(xmins_raw) else 0,
                        ymins_raw[i] if i < len(ymins_raw) else 0,
                        xmaxs_raw[i] if i < len(xmaxs_raw) else 0,
                        ymaxs_raw[i] if i < len(ymaxs_raw) else 0
                    )
                    
                    results.append({
                        'class': class_char,
                        'bbox_norm': bbox_norm,
                        'bbox_raw': bbox_raw,
                        'latex': latex_char,
                        'index': i
                    })
            
            i += 1
    
    return results


def display_sample_labels(sample: Dict, sample_idx: int):
    """サンプルの正解ラベルを表示"""
    print("=" * 100)
    print(f"サンプル {sample_idx + 1}")
    print("=" * 100)
    
    uuid = sample.get('uuid', 'N/A')
    latex = sample.get('latex', 'N/A')
    
    print(f"\nUUID: {uuid}")
    print(f"LaTeX: {latex}")
    
    if 'image_data' in sample:
        img_data = sample['image_data']
        width = img_data.get('width', 'N/A')
        height = img_data.get('height', 'N/A')
        print(f"画像サイズ: {width} x {height}")
    
    # 正解ラベルとbboxを抽出
    labels = extract_labels_and_bboxes(sample)
    
    if len(labels) == 0:
        print(f"\n⚠️  対象クラス（{len(CLASSES)}クラス）に該当する文字が見つかりませんでした。")
        print("   このサンプルには複雑なLaTeX記号のみが含まれている可能性があります。")
        return
    
    print(f"\n抽出された正解ラベル数: {len(labels)}")
    print("\n" + "-" * 100)
    print("記号・数字ごとの正解ラベルとBBox")
    print("-" * 100)
    print(f"{'Index':<8} {'クラス':<8} {'LaTeX':<25} {'BBox (正規化)':<35} {'BBox (生座標)':<30}")
    print("-" * 100)
    
    for label in labels:
        idx = label['index']
        cls = label['class']
        latex_char = label['latex']
        bbox_norm = label['bbox_norm']
        bbox_raw = label['bbox_raw']
        
        bbox_norm_str = f"({bbox_norm[0]:.4f},{bbox_norm[1]:.4f})-({bbox_norm[2]:.4f},{bbox_norm[3]:.4f})"
        bbox_raw_str = f"({bbox_raw[0]},{bbox_raw[1]})-({bbox_raw[2]},{bbox_raw[3]})"
        
        print(f"{idx:<8} {cls:<8} {latex_char:<25} {bbox_norm_str:<35} {bbox_raw_str:<30}")
    
    print()


def main():
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Kaggle OCRデータセットの正解ラベルを記号・数字ごとに表示"
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
        '--count',
        type=int,
        default=1,
        help='表示するサンプル数（デフォルト: 1）'
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
    
    # サンプルを表示
    end_idx = min(args.sample + args.count, len(data))
    for i in range(args.sample, end_idx):
        display_sample_labels(data[i], i)
        if i < end_idx - 1:
            print("\n")


if __name__ == '__main__':
    main()
