"""
検出対象クラスの定義とマッピング

19クラスから拡張する場合は、このファイルを編集してください。
"""

from typing import Optional, Dict, List


# 検出対象クラス（拡張版：約50クラス）
# Kaggle Aida Calculus Math Handwriting Recognition Datasetに対応
CLASSES = [
    # 数字（10クラス）
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    
    # 基本演算子（5クラス）
    '+', '-', '*', '/', '=',
    
    # アルファベット変数（小文字26クラス）
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    
    # 括弧類（3クラス）
    '(', ')', '|',  # 丸括弧2種 + 絶対値記号
    
    # ギリシャ文字・定数（4クラス）
    'π',  # 円周率
    'θ',  # 角度変数
    '∞',  # 無限大
    'e',  # 自然対数の底（変数としても使われるが、定数として特別扱い）
    
    # 特殊記号（2クラス）
    '→',  # 矢印（リミットの「～に近づく」）
    '√',  # ルート記号（平方根）
]

# クラスIDマッピング（YOLO形式）
CLASS_TO_ID: Dict[str, int] = {cls: idx for idx, cls in enumerate(CLASSES)}

# IDからクラスへの逆マッピング
ID_TO_CLASS: Dict[int, str] = {idx: cls for cls, idx in CLASS_TO_ID.items()}


def get_class_count() -> int:
    """クラス数を取得"""
    return len(CLASSES)


def latex_to_class(latex_char: str) -> Optional[str]:
    """
    LaTeX文字を今回のプロジェクトのクラスに変換
    
    Args:
        latex_char: LaTeX文字列（例: '\\frac', '+', 'x', '\\pi', '\\to'）
    
    Returns:
        変換後のクラス文字、またはNone（対象外の場合）
    
    Examples:
        >>> latex_to_class('+')
        '+'
        >>> latex_to_class('\\frac')
        '/'
        >>> latex_to_class('\\pi')
        'π'
        >>> latex_to_class('\\to')
        '→'
    """
    # 直接マッチする場合
    if latex_char in CLASSES:
        return latex_char
    
    # LaTeX記号の変換マッピング
    latex_to_class_map: Dict[str, str] = {
        # 分数・除算
        '\\frac': '/',
        
        # 括弧
        '\\left(': '(',
        '\\right)': ')',
        '\\left|': '|',  # 絶対値の開始
        '\\right|': '|',  # 絶対値の終了
        '|': '|',  # 直接の縦棒
        
        # 演算子
        '\\times': '*',
        '\\cdot': '*',
        '\\div': '/',
        
        # ギリシャ文字・定数
        '\\pi': 'π',
        '\\Pi': 'π',  # 大文字も小文字に統一
        '\\theta': 'θ',
        '\\Theta': 'θ',  # 大文字も小文字に統一
        '\\infty': '∞',
        '\\inf': '∞',
        
        # 特殊記号
        '\\to': '→',
        '\\rightarrow': '→',
        '\\sqrt': '√',
        
        # 自然対数の底（LaTeXでは通常そのまま 'e' と書くが、念のため）
        'e': 'e',  # 既に直接マッチするが、明示的に記載
    }
    
    if latex_char in latex_to_class_map:
        return latex_to_class_map[latex_char]
    
    # 小文字アルファベット（変数として使用される文字）
    # LaTeXでは通常そのまま文字として書かれる（例: 'a', 'b', 'x'）
    if len(latex_char) == 1 and latex_char.islower() and latex_char.isalpha():
        if latex_char in CLASSES:
            return latex_char
    
    # 対象外のLaTeX記号
    # 例: \lim_, \sin, \cos, \tan, \log など（これらは複数文字なので後処理で処理）
    return None


def validate_classes(classes: List[str]) -> bool:
    """
    クラス定義の妥当性を検証
    
    Args:
        classes: クラスリスト
    
    Returns:
        妥当な場合はTrue
    """
    if len(classes) == 0:
        return False
    
    if len(classes) != len(set(classes)):
        # 重複チェック
        return False
    
    return True


# 起動時に検証
if not validate_classes(CLASSES):
    raise ValueError(f"クラス定義が不正です: {CLASSES}")
