#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
lim、abs、exp、sqrtがどのように表現されているかを確認するスクリプト
"""

import json
import sys
from pathlib import Path
from collections import defaultdict

# 設定ファイルからクラス定義をインポート
sys.path.insert(0, str(Path(__file__).parent.parent))
from config import CLASSES, latex_to_class

def check_function_names(json_path: Path, max_samples: int = 100):
    """関数名がどのように表現されているかを確認"""
    
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # 関数名の出現パターンを記録
    function_patterns = defaultdict(list)
    target_functions = ['lim', 'abs', 'exp', 'sqrt']
    
    sample_count = 0
    for sample in data:
        if sample_count >= max_samples:
            break
            
        if 'image_data' not in sample:
            continue
        
        img_data = sample['image_data']
        visible_chars = img_data.get('visible_latex_chars', [])
        full_chars = img_data.get('full_latex_chars', [])
        
        # visible_charsで関数名を探す
        for func in target_functions:
            # 連続する文字列をチェック
            for i in range(len(visible_chars) - len(func) + 1):
                candidate = ''.join(visible_chars[i:i+len(func)])
                if candidate == func:
                    context = visible_chars[max(0, i-3):min(len(visible_chars), i+len(func)+3)]
                    function_patterns[func].append({
                        'context': context,
                        'sample_idx': sample_count
                    })
        
        # full_charsでも確認
        for func in target_functions:
            # LaTeXコマンド形式をチェック
            for i, char in enumerate(full_chars):
                if f'\\{func}' in char or func in char:
                    context = full_chars[max(0, i-3):min(len(full_chars), i+3)]
                    function_patterns[f'{func}_full'].append({
                        'context': context,
                        'sample_idx': sample_count,
                        'char': char
                    })
        
        sample_count += 1
    
    # 結果を表示
    print("=" * 80)
    print("関数名の出現パターン確認")
    print("=" * 80)
    print(f"確認したサンプル数: {sample_count}")
    print()
    
    for func in target_functions:
        print(f"\n{func}:")
        if func in function_patterns:
            print(f"  visible_charsでの出現: {len(function_patterns[func])} 回")
            if len(function_patterns[func]) > 0:
                print("  例:")
                for i, pattern in enumerate(function_patterns[func][:3]):
                    print(f"    {i+1}. コンテキスト: {pattern['context']}")
        else:
            print(f"  visible_charsでの出現: 0 回")
        
        if f'{func}_full' in function_patterns:
            print(f"  full_charsでの出現: {len(function_patterns[f'{func}_full'])} 回")
            if len(function_patterns[f'{func}_full']) > 0:
                print("  例:")
                for i, pattern in enumerate(function_patterns[f'{func}_full'][:3]):
                    print(f"    {i+1}. 文字: {pattern['char']}, コンテキスト: {pattern['context']}")
        else:
            print(f"  full_charsでの出現: 0 回")
    
    # すべてのvisible_charsのユニークな値を確認
    print("\n" + "=" * 80)
    print("visible_charsのユニークな値（関数名関連）")
    print("=" * 80)
    
    unique_chars = set()
    for sample in data[:max_samples]:
        if 'image_data' in sample:
            visible_chars = sample['image_data'].get('visible_latex_chars', [])
            for char in visible_chars:
                if any(func in char for func in target_functions) or len(char) > 1:
                    unique_chars.add(char)
    
    print(f"ユニークな値の数: {len(unique_chars)}")
    print("値の例:")
    for char in sorted(unique_chars)[:20]:
        print(f"  '{char}'")

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(
        description="lim、abs、exp、sqrtがどのように表現されているかを確認"
    )
    parser.add_argument(
        '--json',
        type=Path,
        required=True,
        help='確認するJSONファイルのパス'
    )
    parser.add_argument(
        '--max-samples',
        type=int,
        default=1000,
        help='確認するサンプル数の上限（デフォルト: 1000）'
    )
    
    args = parser.parse_args()
    
    if not args.json.exists():
        print(f"エラー: ファイルが見つかりません: {args.json}")
        sys.exit(1)
    
    check_function_names(args.json, args.max_samples)
