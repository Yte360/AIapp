from flask import Blueprint, jsonify, request as flask_request
from core.clients import db_manager
from datetime import datetime

bp = Blueprint("conversation", __name__)

def get_conversation_id():
    """生成会话ID"""
    return datetime.now().strftime("%Y%m%d_%H%M%S_%f")

@bp.route("/api/conversation/<conversation_id>", methods=["GET"])  # 获取对话历史
def get_conversation(conversation_id):
    try:
        user_id = flask_request.args.get("user_id", type=int)
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
            
        conversation = db_manager.get_conversation(user_id, conversation_id)
        if not conversation:
            return jsonify({"ok": False, "error": "conversation not found"}), 404
        
        messages = db_manager.get_messages(user_id, conversation_id)
        
        return jsonify({
            "ok": True,
            "conversation": conversation,
            "messages": messages
        })
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/conversation/<conversation_id>", methods=["DELETE"])  # 删除对话
def delete_conversation(conversation_id):
    try:
        user_id = flask_request.args.get("user_id", type=int)
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
            
        success = db_manager.delete_conversation(user_id, conversation_id)
        if not success:
            return jsonify({"ok": False, "error": "conversation not found"}), 404
        
        return jsonify({
            "ok": True,
            "message": "conversation deleted"
        })
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/conversations", methods=["GET"])  # 获取所有对话列表
def get_conversations():
    try:
        user_id = flask_request.args.get("user_id", type=int)
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
            
        limit = flask_request.args.get("limit", 100, type=int)
        conversations = db_manager.get_all_conversations(user_id, limit=limit)
        
        return jsonify({
            "ok": True,
            "conversations": conversations,
            "total": len(conversations)
        })
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/conversations", methods=["POST"])  # 创建新对话
def create_conversation():
    try:
        data = flask_request.get_json(silent=True) or {}
        title = data.get("title")
        user_id = data.get("user_id")
        
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
        
        conversation_id = get_conversation_id()
        conversation = db_manager.create_conversation(user_id, conversation_id, title)
        
        return jsonify({
            "ok": True,
            "conversation": conversation
        })
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/conversation/<conversation_id>/title", methods=["PUT"])  # 更新对话标题
def update_conversation_title(conversation_id):
    try:
        data = flask_request.get_json(silent=True) or {}
        title = data.get("title")
        user_id = data.get("user_id")
        
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
        
        if not title:
            return jsonify({"ok": False, "error": "title is required"}), 400
        
        success = db_manager.update_conversation_title(user_id, conversation_id, title)
        if not success:
            return jsonify({"ok": False, "error": "conversation not found"}), 404
        
        return jsonify({
            "ok": True,
            "message": "title updated"
        })
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500
