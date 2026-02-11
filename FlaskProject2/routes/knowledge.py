import os
import traceback
from flask import Blueprint, jsonify, request as flask_request
import os
from core.clients import rag_system
from services.file_service import file_service

bp = Blueprint("knowledge", __name__)

@bp.route("/api/knowledge/upload", methods=["POST"])
def upload_document():
    try:
        user_id = flask_request.form.get("user_id")
        if not user_id:
            return jsonify({"ok": False, "error": "missing field: user_id"}), 400
        
        try:
            user_id = int(user_id)
        except:
            return jsonify({"ok": False, "error": "invalid user_id"}), 400

        if 'file' not in flask_request.files:
            return jsonify({"ok": False, "error": "没有文件"}), 400
        
        file = flask_request.files['file']
        if file.filename == '':
            return jsonify({"ok": False, "error": "没有选择文件"}), 400
        
        # 使用 FileService 集中处理文件上传、MinIO 存储
        try:
            file_path, original_filename = file_service.process_knowledge_file(user_id, file)
            if not file_path:
                return jsonify({"ok": False, "error": "文件处理失败"}), 500
        except Exception as e:
            return jsonify({"ok": False, "error": str(e)}), 500
            
        title = flask_request.form.get('title', original_filename)
        category = flask_request.form.get('category', 'general')
        
        # 添加到知识库
        success = rag_system.add_document(user_id, file_path, title, category)

        # 解析完成后删除临时文件
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
        except:
            pass
        
        if success:
            return jsonify({
                "ok": True,
                "message": "文档上传成功",
                "filename": original_filename,
                "title": title,
                "category": category
            })
        else:
            return jsonify({"ok": False, "error": "文档处理失败"}), 500
    except Exception as ex:
        import traceback
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/knowledge/add-text", methods=["POST"])
def add_text_to_knowledge():
    try:
        data = flask_request.get_json()
        user_id = data.get("user_id")
        if not user_id:
            return jsonify({"ok": False, "error": "missing field: user_id"}), 400
            
        text = data.get('text')
        title = data.get('title', '用户添加的文本')
        category = data.get('category', 'general')
        
        if not text:
            return jsonify({"ok": False, "error": "缺少文本内容"}), 400
        
        success = rag_system.add_text(user_id, text, title, category)
        
        if success:
            return jsonify({
                "ok": True,
                "message": "文本添加成功",
                "title": title,
                "category": category
            })
        else:
            return jsonify({"ok": False, "error": "文本处理失败"}), 500
    except Exception as ex:
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/knowledge/search", methods=["POST"])
def search_knowledge():
    try:
        data = flask_request.get_json()
        user_id = data.get("user_id")
        if not user_id:
            return jsonify({"ok": False, "error": "missing field: user_id"}), 400
            
        query = data.get('query')
        top_k = data.get('top_k', 5)
        
        if not query:
            return jsonify({"ok": False, "error": "缺少查询内容"}), 400
        
        results = rag_system.search(user_id, query, top_k)
        
        return jsonify({
            "ok": True,
            "query": query,
            "results": results,
            "total": len(results)
        })
    except Exception as ex:
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/knowledge/stats", methods=["GET"])
def get_knowledge_stats():
    try:
        user_id = flask_request.args.get("user_id", type=int)
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400
            
        stats = rag_system.get_knowledge_stats(user_id)
        return jsonify({
            "ok": True,
            "stats": stats
        })
    except Exception as ex:
        return jsonify({"ok": False, "error": str(ex)}), 500

@bp.route("/api/knowledge/clear", methods=["DELETE"])
def clear_knowledge_base():
    try:
        user_id = flask_request.args.get("user_id", type=int)
        if not user_id:
            return jsonify({"ok": False, "error": "user_id is required"}), 400

        vs = getattr(rag_system, "vector_store", None)
        collection = getattr(vs, "collection", None) if vs else None

        if collection is None:
            return jsonify({
                "ok": False,
                "error": "Chroma 未连接，无法操作向量数据库。"
            }), 500

        try:
            res = collection.get(where={"user_id": user_id}, include=[])
            deleted_count = len(res['ids']) if res and 'ids' in res else 0
            collection.delete(where={"user_id": user_id})
        except Exception as e:
            return jsonify({"ok": False, "error": f"删除操作失败: {str(e)}"}), 500

        return jsonify({
            "ok": True,
            "message": f"用户 {user_id} 的个人知识库已清空",
            "deleted_chunks": deleted_count
        })
    except Exception as ex:
        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500
