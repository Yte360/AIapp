import traceback
from datetime import datetime
from flask import Blueprint, jsonify, request as flask_request

from core.clients import db_manager, rag_system
from services.llm_service import ark_chat, extract_content
from services.file_service import file_service
from utils.text_format import format_text_for_display
from config.prompts import STUDY_EXPERT_PROMPT, DIGITAL_HUMAN_DEFAULT_PROMPT

bp = Blueprint("chat", __name__)

def get_conversation_id():
    """生成会话ID"""
    return datetime.now().strftime("%Y%m%d_%H%M%S_%f")

def add_message_to_history(user_id: int, conversation_id: str, role: str, content: str):
    """添加消息到对话历史（数据库）"""
    db_manager.add_message(user_id, conversation_id, role, content)

def get_messages_for_api(user_id: int, conversation_id: str, user_query: str = None) -> list:
    """获取用于API调用的消息列表（从数据库）"""
    db_messages = db_manager.get_messages(user_id, conversation_id, limit=50)
    
    if not db_messages:
        return []
    
    messages = []
    
    rag_context = ""
    if user_query:
        rag_context = rag_system.get_context_for_query(user_id, user_query)
    
    # 使用 config/prompts.py 中的系统提示词
    system_prompt = STUDY_EXPERT_PROMPT
    
    if rag_context:
        system_prompt += f"\n\n基于以下知识库信息回答用户问题：\n{rag_context}"
    
    messages.append({"role": "system", "content": system_prompt})
    
    for msg in db_messages:
        messages.append({
            "role": msg["role"], 
            "content": msg["content"]
        })
    
    return messages

@bp.route("/api/chat", methods=["POST"])
def api_chat():
    try:
        data = flask_request.get_json(silent=True) or {}
        prompt = data.get("prompt") or data.get("message")
        conversation_id = data.get("conversation_id")
        user_id = data.get("user_id")
        save_history = data.get("save_history", True)
        
        if not user_id:
            return jsonify({"ok": False, "error": "missing field: user_id"}), 400
            
        if not prompt:
            return jsonify({"error": "missing field: prompt"}), 400

        if save_history:
            if not conversation_id:
                conversation_id = get_conversation_id()
                db_manager.create_conversation(user_id, conversation_id)
            add_message_to_history(user_id, conversation_id, "user", prompt)
        
        if save_history:
            messages = get_messages_for_api(user_id, conversation_id, prompt)
        else:
            messages = [
                {"role": "system", "content": DIGITAL_HUMAN_DEFAULT_PROMPT},
                {"role": "user", "content": prompt}
            ]
        
        obj = ark_chat(messages)
        content = extract_content(obj)
        formatted_content = format_text_for_display(content)
        
        if save_history and conversation_id:
            add_message_to_history(user_id, conversation_id, "assistant", content)
        
        return jsonify({
            "ok": True,
            "content": content,
            "formatted_content": formatted_content,
            "conversation_id": conversation_id if save_history else None,
        })
    except Exception as ex:
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/chat-with-file", methods=["POST"])
def api_chat_with_file():
    try:
        prompt = flask_request.form.get("prompt")
        conversation_id = flask_request.form.get("conversation_id")
        user_id = flask_request.form.get("user_id")
        file_count = int(flask_request.form.get("file_count", 0))

        if not user_id:
            return jsonify({"ok": False, "error": "缺少输入：user_id"}), 400
        
        try:
            user_id = int(user_id)
        except:
            return jsonify({"ok": False, "error": "invalid user_id"}), 400

        file_contexts = []
        filenames = []
        
        # 使用 FileService 集中处理文件
        for i in range(file_count):
            file = flask_request.files.get(f"file_{i}")
            if file:
                content, fname = file_service.process_chat_file(user_id, file, i)
                if content:
                    file_contexts.append(content)
                    filenames.append(fname)

        extra_context = ""
        if file_contexts:
            max_len_per_file = 2000 // len(file_contexts) if file_contexts else 2000
            file_snippets = []
            for i, (content, fname) in enumerate(zip(file_contexts, filenames)):
                snippet = content[:max_len_per_file]
                file_snippets.append(f"文件 {i+1} ({fname}):\n{snippet}")
            extra_context = f"\n\n以下是用户上传的{len(filenames)}个文件的内容片段（可能已截断）：\n\n" + "\n\n".join(file_snippets)

        if not conversation_id:
            conversation_id = get_conversation_id()
            db_manager.create_conversation(user_id, conversation_id)

        user_msg = prompt or ""
        if filenames:
            file_list = ", ".join(filenames)
            user_msg = f"[文件: {file_list}]\n" + user_msg
        add_message_to_history(user_id, conversation_id, user_msg)

        messages = get_messages_for_api(user_id, conversation_id, prompt or "")
        if extra_context:
            messages.insert(1, {"role": "system", "content": extra_context})

        obj = ark_chat(messages)
        content = extract_content(obj)
        formatted_content = format_text_for_display(content)
        add_message_to_history(user_id, conversation_id, content)

        return jsonify({
            "ok": True,
            "content": content,
            "formatted_content": formatted_content,
            "conversation_id": conversation_id,
            "filename": filenames[0] if filenames else None,
            "raw": obj,
        })
    except Exception as ex:
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500
