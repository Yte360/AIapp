import os
try:
    from core.rag_system import RAGSystem
except Exception as e:
    print(f"[ERROR] 导入 RAGSystem 失败: {e}")
    RAGSystem = None

try:
    from core.database import DatabaseManager
except Exception as e:
    print(f"[ERROR] 导入 DatabaseManager 失败: {e}")
    DatabaseManager = None

try:
    from core.minio import MinIOStorage
except Exception as e:
    print(f"[ERROR] 导入 MinIOStorage 失败: {e}")
    MinIOStorage = None

# 初始化数据库管理器 - 使用 MySQL
db_manager = None
try:
    if DatabaseManager is not None:
        db_manager = DatabaseManager(
            host=os.getenv("MYSQL_HOST") or "localhost",
            port=int(os.getenv("MYSQL_PORT") or 3306),
            user=os.getenv("MYSQL_USER") or "root",
            password=os.getenv("MYSQL_PASSWORD") or "123456",
            database=os.getenv("MYSQL_DATABASE") or "chat_app"
        )
        print(f"[INFO] 数据库初始化成功: MySQL {db_manager.host}:{db_manager.port}/{db_manager.database}")
except Exception as e:
    print(f"[ERROR] 数据库初始化失败: {e}")
    import traceback
    traceback.print_exc()

# 初始化 RAG 系统
rag_system = None
try:
    if RAGSystem is not None:
        rag_system = RAGSystem()
except Exception as e:
    print(f"[ERROR] 初始化 RAG 系统失败: {e}")

# 初始化 MinIO
minio_storage = None
try:
    if MinIOStorage is not None:
        minio_storage = MinIOStorage()
except Exception as e:
    print(f"[WARN] MinIO 未启用或初始化失败: {e}")
