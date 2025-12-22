#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kaggle OCRデータセットからラベルファイル（YOLO形式）のみを生成するスクリプト

画像はコピーせず、ラベルファイル（.txt）のみを生成します。
"""

import argparse
import json
import sys
import io
import time
from pathlib import Path
from collections import defaultdict
from typing import Dict, List
from tqdm import tqdm

# 設定ファイルからクラス定義をインポート
sys.path.insert(0, str(Path(__file__).parent.parent))
from config import CLASSES, CLASS_TO_ID, latex_to_class

# WindowsでのUnicode出力対応
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')


def convert_bbox_to_yolo_format(
    xmin: float,
    ymin: float,
    xmax: float,
    ymax: float
) -> tuple[float, float, float, float]:
    """
    バウンディングボックスをYOLO形式に変換
    
    Args:
        xmin, ymin, xmax, ymax: 正規化済み座標（0.0-1.0）
    
    Returns:
        (center_x, center_y, width, height) 正規化済み（0.0-1.0）
    """
    # 中心座標とサイズを計算
    center_x = (xmin + xmax) / 2.0
    center_y = (ymin + ymax) / 2.0
    width = xmax - xmin
    height = ymax - ymin
    
    # 0.0-1.0の範囲にクリップ
    center_x = max(0.0, min(1.0, center_x))
    center_y = max(0.0, min(1.0, center_y))
    width = max(0.0, min(1.0, width))
    height = max(0.0, min(1.0, height))
    
    return center_x, center_y, width, height


def extract_labels_from_sample(sample: Dict) -> List[Dict]:
    """
    サンプルから正解ラベルとbboxを抽出
    
    Returns:
        [{'class': str, 'class_id': int, 'bbox_yolo': (cx, cy, w, h)}, ...]
    """
    if 'image_data' not in sample:
        return []
    
    img_data = sample['image_data']
    visible_chars = img_data.get('visible_latex_chars', [])
    
    xmins = img_data.get('xmins', [])
    xmaxs = img_data.get('xmaxs', [])
    ymins = img_data.get('ymins', [])
    ymaxs = img_data.get('ymaxs', [])
    
    results = []
    
    for i, latex_char in enumerate(visible_chars):
        # LaTeX文字をクラスに変換（設定ファイルから）
        class_char = latex_to_class(latex_char)
        
        if class_char is None:
            # 対象外の文字はスキップ
            continue
        
        if i >= len(xmins) or i >= len(xmaxs) or i >= len(ymins) or i >= len(ymaxs):
            continue
        
        # クラスIDを取得（設定ファイルから）
        class_id = CLASS_TO_ID[class_char]
        
        # 正規化座標を取得（既に0.0-1.0の範囲）
        xmin = xmins[i]
        ymin = ymins[i]
        xmax = xmaxs[i]
        ymax = ymaxs[i]
        
        # YOLO形式に変換
        center_x, center_y, width, height = convert_bbox_to_yolo_format(
            xmin, ymin, xmax, ymax
        )
        
        results.append({
            'class': class_char,
            'class_id': class_id,
            'bbox_yolo': (center_x, center_y, width, height)
        })
    
    return results


def find_image_path(uuid: str, json_path: Path) -> Path | None:
    """
    UUIDから対応する画像ファイルのパスを検索
    
    Args:
        uuid: サンプルのUUID
        json_path: JSONファイルのパス
    
    Returns:
        画像ファイルのパス（見つからない場合はNone）
    """
    # JSONファイルの親ディレクトリからbackground_imagesディレクトリを探す
    # 例: .../batch1/JSON/kaggle_data_1.json -> .../batch1/background_images/
    json_dir = json_path.parent
    parent_dir = json_dir.parent
    
    # background_imagesディレクトリを探す
    background_images_dir = parent_dir / 'background_images'
    if not background_images_dir.exists():
        return None
    
    # 画像ファイルの拡張子を試す（.png, .jpg, .jpeg）
    for ext in ['.png', '.jpg', '.jpeg']:
        image_path = background_images_dir / f"{uuid}{ext}"
        if image_path.exists():
            return image_path
    
    return None


def process_kaggle_json(
    json_path: Path,
    output_labels_dir: Path,
    stats: defaultdict,
    image_mapping: Dict[str, str] | None = None
) -> tuple[int, int]:
    """
    Kaggle JSONファイルを処理してラベルファイルを生成
    
    Args:
        json_path: 処理するJSONファイルのパス
        output_labels_dir: ラベルファイルの出力先ディレクトリ
        stats: 統計情報を格納する辞書
        image_mapping: 画像ファイルのパスマッピングを格納する辞書（uuid -> 画像パス）
    
    Returns:
        (処理済み数, スキップ数)
    
    Raises:
        FileNotFoundError: JSONファイルが見つからない場合
        json.JSONDecodeError: JSONファイルの解析に失敗した場合
        IOError: ファイルの読み書きに失敗した場合
    """
    try:
        # JSONファイルを読み込む
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"⚠️  警告: JSONファイルが見つかりません: {json_path}")
        return 0, 0
    except json.JSONDecodeError as e:
        print(f"⚠️  警告: JSONファイルの解析に失敗しました: {json_path}")
        print(f"   エラー: {e}")
        return 0, 0
    
    if not isinstance(data, list):
        print(f"⚠️  警告: JSONファイルの形式が不正です（リスト形式を期待）: {json_path}")
        return 0, 0
    
    processed_count = 0
    skipped_count = 0
    
    for sample in data:
        if not isinstance(sample, dict):
            skipped_count += 1
            continue
            
        uuid = sample.get('uuid', '')
        if not uuid:
            skipped_count += 1
            continue
        
        # ラベルを抽出
        labels = extract_labels_from_sample(sample)
        
        if len(labels) == 0:
            # 対象クラスに該当する文字がない場合はスキップ
            skipped_count += 1
            continue
        
        # 画像ファイルのパスを検索してマッピングに追加
        if image_mapping is not None:
            image_path = find_image_path(uuid, json_path)
            if image_path is not None:
                # 相対パスまたは絶対パスを保存（文字列として）
                image_mapping[uuid] = str(image_path)
        
        # ラベルファイルを生成
        label_path = output_labels_dir / f"{uuid}.txt"
        try:
            with open(label_path, 'w', encoding='utf-8') as f:
                for label in labels:
                    class_id = label['class_id']
                    center_x, center_y, width, height = label['bbox_yolo']
                    
                    # YOLO形式で書き込み
                    f.write(f"{class_id} {center_x:.6f} {center_y:.6f} {width:.6f} {height:.6f}\n")
                    
                    # 統計情報を更新
                    stats[label['class']] += 1
            
            processed_count += 1
        except IOError as e:
            print(f"⚠️  警告: ラベルファイルの書き込みに失敗しました: {label_path}")
            print(f"   エラー: {e}")
            skipped_count += 1
    
    return processed_count, skipped_count


def main():
    parser = argparse.ArgumentParser(
        description="Kaggle OCRデータセットからラベルファイル（YOLO形式）のみを生成"
    )
    parser.add_argument(
        '--source',
        type=Path,
        required=True,
        help='Kaggleデータのソースディレクトリ（例: raw/aidapearson_ocr）'
    )
    parser.add_argument(
        '--target',
        type=Path,
        required=True,
        help='出力先ディレクトリ（例: processed）'
    )
    parser.add_argument(
        '--split',
        type=str,
        default='train',
        choices=['train', 'val', 'test'],
        help='データセットの分割（デフォルト: train）'
    )
    
    args = parser.parse_args()
    
    # パスを解決（絶対パスの場合はそのまま使用、相対パスの場合はスクリプトの親ディレクトリ基準）
    script_dir = Path(__file__).parent.parent
    
    # 絶対パスかどうかを判定
    source_path_obj = Path(args.source)
    target_path_obj = Path(args.target)
    
    if source_path_obj.is_absolute():
        # 絶対パスの場合
        source_dir = source_path_obj
    else:
        # 相対パスの場合
        source_dir = (script_dir / args.source).resolve()
    
    if target_path_obj.is_absolute():
        # 絶対パスの場合
        target_dir = target_path_obj
    else:
        # 相対パスの場合
        target_dir = (script_dir / args.target).resolve()
    
    # デバッグ情報（パス解決の確認）
    print(f"[DEBUG] script_dir: {script_dir}")
    print(f"[DEBUG] args.source (元): {args.source} (絶対パス: {source_path_obj.is_absolute()})")
    print(f"[DEBUG] args.target (元): {args.target} (絶対パス: {target_path_obj.is_absolute()})")
    print(f"[DEBUG] source_dir (解決後): {source_dir}")
    print(f"[DEBUG] target_dir (解決後): {target_dir}")
    print()
    
    # ソースディレクトリの存在確認
    if not source_dir.exists():
        print(f"❌ エラー: ソースディレクトリが存在しません: {source_dir}")
        return
    
    if not source_dir.is_dir():
        print(f"❌ エラー: ソースパスはディレクトリではありません: {source_dir}")
        return
    
    # 出力先パスの検証（MyDrive直下に保存されないようにする）
    target_path_str = str(target_dir)
    if target_path_str == "/content/drive/MyDrive" or str(target_dir.parent) == "/content/drive/MyDrive":
        print(f"❌ エラー: 出力先がMyDrive直下を指しています: {target_dir}")
        print("   これは許可されていません。必ずサブディレクトリを指定してください。")
        return
    
    # 出力ディレクトリを作成
    labels_dir = target_dir / args.split / 'labels'
    try:
        labels_dir.mkdir(parents=True, exist_ok=True)
        # 作成されたパスを確認（MyDrive直下でないことを再確認）
        if str(labels_dir.parent.parent.parent) == "/content/drive/MyDrive":
            print(f"❌ エラー: 出力先がMyDrive直下になってしまいます: {labels_dir}")
            print(f"   出力先パスを確認してください: {target_dir}")
            return
    except OSError as e:
        print(f"❌ エラー: 出力ディレクトリの作成に失敗しました: {labels_dir}")
        print(f"   エラー: {e}")
        return
    
    # 画像マッピングファイルのパス
    mapping_file = target_dir / args.split / 'image_mapping.json'
    
    print("=" * 80)
    print("Kaggle OCRデータセットのラベルファイル生成")
    print("=" * 80)
    print(f"ソース: {source_dir}")
    print(f"  存在確認: ✓")
    print(f"出力先: {labels_dir}")
    print(f"  存在確認: ✓")
    print(f"分割: {args.split}")
    print(f"対象クラス: {len(CLASSES)}クラス ({', '.join(CLASSES)})")
    print()
    
    # JSONファイルを検索
    json_files = list(source_dir.rglob('kaggle_data_*.json'))
    
    if len(json_files) == 0:
        print(f"❌ エラー: JSONファイルが見つかりません: {source_dir}")
        print(f"   検索パターン: kaggle_data_*.json")
        print(f"   確認: ソースディレクトリ配下にJSONファイルが存在するか確認してください")
        return
    
    print(f"見つかったJSONファイル: {len(json_files)} 件")
    if len(json_files) <= 10:
        print("  ファイル一覧:")
        for json_file in json_files:
            print(f"    - {json_file}")
    else:
        print("  ファイル一覧（最初の10件）:")
        for json_file in json_files[:10]:
            print(f"    - {json_file}")
        print(f"    ... 他 {len(json_files) - 10} 件")
    print()
    
    # 統計情報
    stats = defaultdict(int)
    total_processed = 0
    total_skipped = 0
    
    # 画像ファイルのマッピング（uuid -> 画像パス）
    image_mapping: Dict[str, str] = {}
    
    # 処理開始時刻
    start_time = time.time()
    last_log_time = start_time
    
    # 各JSONファイルを処理（詳細な進捗表示付き）
    print("\n処理を開始します...")
    print(f"総ファイル数: {len(json_files)} 件\n")
    
    for idx, json_path in enumerate(tqdm(json_files, desc="処理中", ncols=100, mininterval=1.0), 1):
        # 定期的に詳細ログを出力（10ファイルごと、または5秒ごと）
        current_time = time.time()
        if idx % 10 == 0 or (current_time - last_log_time) >= 5.0:
            elapsed = current_time - start_time
            rate = idx / elapsed if elapsed > 0 else 0
            remaining = (len(json_files) - idx) / rate if rate > 0 else 0
            print(f"\n[進捗] {idx}/{len(json_files)} ファイル処理済み "
                  f"(経過時間: {elapsed:.1f}秒, "
                  f"処理速度: {rate:.2f}ファイル/秒, "
                  f"残り時間目安: {remaining:.1f}秒)")
            print(f"  現在処理中: {json_path.name}")
            print(f"  処理済みサンプル: {total_processed}, スキップ: {total_skipped}")
            last_log_time = current_time
        
        processed, skipped = process_kaggle_json(json_path, labels_dir, stats, image_mapping)
        total_processed += processed
        total_skipped += skipped
    
    # 画像マッピングファイルを保存
    try:
        with open(mapping_file, 'w', encoding='utf-8') as f:
            json.dump(image_mapping, f, ensure_ascii=False, indent=2)
        print(f"\n画像マッピングファイルを保存しました: {mapping_file}")
        print(f"  マッピング数: {len(image_mapping)} 件")
    except IOError as e:
        print(f"⚠️  警告: 画像マッピングファイルの保存に失敗しました: {mapping_file}")
        print(f"   エラー: {e}")
    
    # 結果を表示
    print("\n" + "=" * 80)
    print("処理完了")
    print("=" * 80)
    print(f"処理済み: {total_processed} サンプル")
    print(f"スキップ: {total_skipped} サンプル（{len(CLASSES)}クラスに該当する文字がない）")
    print(f"生成されたラベルファイル: {total_processed} 件")
    print()
    
    print("クラス別統計:")
    for cls in CLASSES:
        count = stats[cls]
        print(f"  {cls:>3}: {count:>8} インスタンス")
    
    total_instances = sum(stats.values())
    print(f"\n総インスタンス数: {total_instances}")
    print(f"出力先: {labels_dir}")
    
    # 生成されたラベルファイル数の確認
    generated_files = list(labels_dir.glob('*.txt'))
    print(f"生成されたラベルファイル数: {len(generated_files)} 件")
    
    # 画像マッピングの情報を表示
    if len(image_mapping) > 0:
        print(f"\n画像マッピング:")
        print(f"  マッピング数: {len(image_mapping)} 件")
        print(f"  マッピングファイル: {mapping_file}")
        print(f"  説明: 各ラベルファイル（<uuid>.txt）に対応する画像ファイルのパスが記録されています")
    else:
        print(f"\n⚠️  警告: 画像マッピングが空です")
        print(f"   画像ファイルが見つからなかった可能性があります")
        print(f"   確認: background_images/ディレクトリが正しい場所にあるか確認してください")
    
    if len(generated_files) == 0:
        print("⚠️  警告: ラベルファイルが1つも生成されませんでした")
        print("   確認事項:")
        print("   1. JSONファイルに有効なサンプルが含まれているか")
        print("   2. サンプルに19クラスに該当する文字が含まれているか")
        print("   3. サンプルに'uuid'フィールドが存在するか")


if __name__ == '__main__':
    main()
