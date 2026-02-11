import os
from core.rag_system import RAGSystem
from core.database import DatabaseManager
from core.minio import MinIOStorage

# 初始化数据库管理器
db_manager = DatabaseManager(
    host=os.getenv("MYSQL_HOST") or "localhost",
    port=int(os.getenv("MYSQL_PORT") or "3306"),
    user=os.getenv("MYSQL_USER") or "root",
    password=os.getenv("MYSQL_PASSWORD") or "Whz258369?",
    database=os.getenv("MYSQL_DATABASE") or "chat_app",
    charset=os.getenv("MYSQL_CHARSET") or "utf8mb4"
)

# 初始化 RAG 系统
rag_system = RAGSystem()

# 初始化 MinIO
minio_storage = None
try:
    minio_storage = MinIOStorage()
except Exception as e:
    print(f"[WARN] MinIO 未启用或初始化失败: {e}")