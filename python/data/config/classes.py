"""
検出対象クラスの定義とマッピング

19クラスから拡張する場合は、このファイルを編集してください。
"""

from typing import Optional, Dict, List


# 検出対象クラス（19クラス）
# 拡張する場合は、ここにクラスを追加してください
CLASSES = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',  # 数字（10クラス）
    '+', '-', '*', '/', '=',                            # 演算子（5クラス）
    'x',                                                 # 変数（1クラス）
    '(', ')'                                             # 括弧（2クラス）
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
        latex_char: LaTeX文字列（例: '\\frac', '+', 'x'）
    
    Returns:
        変換後のクラス文字、またはNone（対象外の場合）
    
    Examples:
        >>> latex_to_class('+')
        '+'
        >>> latex_to_class('\\frac')
        '/'
        >>> latex_to_class('\\sin')
        None
    """
    # 直接マッチする場合
    if latex_char in CLASSES:
        return latex_char
    
    # LaTeX記号の変換マッピング
    # 拡張する場合は、ここにマッピングを追加してください
    latex_to_class_map: Dict[str, str] = {
        '\\frac': '/',      # 分数 → 除算
        '\\left(': '(',     # 左括弧
        '\\right)': ')',    # 右括弧
        '\\times': '*',     # 乗算記号
        '\\cdot': '*',      # ドット乗算
    }
    
    if latex_char in latex_to_class_map:
        return latex_to_class_map[latex_char]
    
    # 対象外のLaTeX記号
    # 例: \lim_, \sin, \cos, \tan, \pi, \to, d, h, u, w, y, t, \infty など
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
