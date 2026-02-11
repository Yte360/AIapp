import os
from datetime import datetime
from werkzeug.utils import secure_filename
from config.settings import UPLOAD_FOLDER
from core.clients import minio_storage, rag_system

class FileService:
    @staticmethod
    def process_chat_file(user_id: int, file, index: int):
        """处理聊天上传的文件：落地、上传MinIO、提取文本"""
        if not file or not file.filename:
            return None, None

        original_filename = file.filename
        if '.' in original_filename:
            name_part = secure_filename(original_filename.rsplit('.', 1)[0])
            ext_part = original_filename.rsplit('.', 1)[1].lower()
            if not name_part:
                name_part = f"file_{int(datetime.now().timestamp() * 1000)}_{index}"
            filename = f"{name_part}.{ext_part}"
        else:
            filename = secure_filename(original_filename) or f"file_{int(datetime.now().timestamp() * 1000)}_{index}"
        
        tmp_path = os.path.join(UPLOAD_FOLDER, f"chat_{filename}")
        file.save(tmp_path)

        # 上传到 MinIO
        if minio_storage is not None:
            try:
                object_key = minio_storage.build_object_key_chat(user_id, filename)
                with open(tmp_path, "rb") as f:
                    minio_storage.upload_fileobj(object_key, f, file.content_type)
            except Exception as e:
                if os.path.exists(tmp_path): os.remove(tmp_path)
                raise RuntimeError(f"文件 {original_filename} 上传到MinIO失败: {e}")

        # 提取文本
        try:
            file_content = rag_system.doc_processor.extract_text_from_file(tmp_path)
            return file_content, original_filename
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    @staticmethod
    def process_knowledge_file(user_id: int, file):
        """处理知识库上传的文件：落地、上传MinIO、返回本地临时路径供RAG解析（解析完需外部删除）"""
        if not file or not file.filename:
            return None, None

        original_filename = file.filename
        file_ext = original_filename.rsplit('.', 1)[1].lower() if '.' in original_filename else ''
        
        safe_name = secure_filename(original_filename.rsplit('.', 1)[0]) if '.' in original_filename else secure_filename(original_filename)
        if not safe_name:
            import time
            safe_name = f"document_{int(time.time() * 1000)}"
        filename = f"{safe_name}.{file_ext}"
        
        file_path = os.path.join(UPLOAD_FOLDER, filename)
        file.save(file_path)

        if minio_storage is not None:
            try:
                object_key = minio_storage.build_object_key_knowledge(user_id, filename)
                with open(file_path, "rb") as f:
                    minio_storage.upload_fileobj(object_key, f, file.content_type)
            except Exception as e:
                if os.path.exists(file_path): os.remove(file_path)
                raise RuntimeError(f"文件 {original_filename} 上传到MinIO失败: {e}")

        return file_path, original_filename

file_service = FileService()
