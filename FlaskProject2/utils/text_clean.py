import re


def clean_text(text: str) -> str:
    """清理和格式化文本"""
    if not text:
        return ""

    # 移除多余的空白字符
    text = text.strip()

    # 移除控制字符（除了换行符和制表符）
    text = re.sub(r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]", "", text)

    # 统一换行符
    text = text.replace("\r\n", "\n").replace("\r", "\n")

    # 移除多余的空行（保留最多两个连续换行）
    text = re.sub(r"\n{3,}", "\n\n", text)

    # 移除行首行尾的空白
    lines = text.split("\n")
    lines = [line.strip() for line in lines]
    text = "\n".join(lines)

    # 移除多余的空格
    text = re.sub(r" +", " ", text)

    return text.strip()
