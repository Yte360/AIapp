import re

from utils.text_clean import clean_text


def format_text_for_display(text: str) -> dict:
    """格式化文本用于前端显示"""
    cleaned_text = clean_text(text)

    # 解析文本结构
    lines = cleaned_text.split("\n")
    formatted_lines = []
    current_list = []
    in_list = False

    for line in lines:
        line = line.strip()
        if not line:
            if in_list and current_list:
                formatted_lines.append({
                    'type': 'list',
                    'items': current_list
                })
                current_list = []
                in_list = False
            continue

        # 检测列表项（使用更简单的方法）
        is_list_item = False

        # 检测数字列表
        if re.match(r'^\d+[\.\)]\s+', line):
            is_list_item = True
            item_text = re.sub(r'^\d+[\.\)]\s+', '', line)
        # 检测破折号列表
        elif re.match(r'^[-*]\s+', line):
            is_list_item = True
            item_text = re.sub(r'^[-*]\s+', '', line)
        # 检测字母列表
        elif re.match(r'^[a-zA-Z][.)]\s+', line):
            is_list_item = True
            item_text = re.sub(r'^[a-zA-Z][.)]\s+', '', line)
        # 检测Unicode符号列表（使用更安全的方法）
        elif line.startswith(('•', '·', '▪', '▫')) and len(line) > 1 and line[1] == ' ':
            is_list_item = True
            item_text = line[2:].strip()

        if is_list_item:
            current_list.append(item_text.strip())
            in_list = True
        else:
            if in_list and current_list:
                formatted_lines.append({
                    'type': 'list',
                    'items': current_list
                })
                current_list = []
                in_list = False

            # 检测标题（以#开头或全大写）
            if line.startswith('#') or (len(line) < 50 and line.isupper() and len(line) > 3):
                formatted_lines.append({
                    'type': 'heading',
                    'text': line.lstrip('#').strip()
                })
            else:
                formatted_lines.append({
                    'type': 'paragraph',
                    'text': line
                })

    # 处理最后的列表
    if in_list and current_list:
        formatted_lines.append({
            'type': 'list',
            'items': current_list
        })

    return {
        'raw': cleaned_text,
        'formatted': formatted_lines
    }
