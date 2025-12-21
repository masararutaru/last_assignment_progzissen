"""
データセット前処理の設定ファイル

クラス定義やマッピング設定を管理します。
"""

from .classes import (
    CLASSES,
    CLASS_TO_ID,
    ID_TO_CLASS,
    latex_to_class,
    get_class_count
)

__all__ = [
    'CLASSES',
    'CLASS_TO_ID',
    'ID_TO_CLASS',
    'latex_to_class',
    'get_class_count',
]
