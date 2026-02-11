import os


# 上传文件夹
UPLOAD_FOLDER = os.getenv("UPLOAD_FOLDER", "uploads")

# 扩展支持的文件类型：文本/文档/表格/演示/网页/结构化/电子书/富文本
ALLOWED_EXTENSIONS = {
    'txt', 'pdf', 'docx', 'xlsx', 'md',
    'pptx', 'csv', 'html', 'htm', 'xml', 'json', 'rtf', 'epub'
}

MAX_CONTENT_LENGTH = int(os.getenv("MAX_CONTENT_LENGTH", str(16 * 1024 * 1024)))  # 默认 16MB


# LLM 配置
ARK_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
ARK_API_KEY = os.getenv("ARK_API_KEY", "sk-5d2a6d2b9cf84024a553aa237f6e95c2")


# CosyVoice 配置
COSYVOICE_API_KEY = os.getenv("COSYVOICE_API_KEY", "sk-5d2a6d2b9cf84024a553aa237f6e95c2")
COSYVOICE_MODEL = os.getenv("COSYVOICE_MODEL", "cosyvoice-v3-flash")
COSYVOICE_VOICE = os.getenv("COSYVOICE_VOICE", "longyingling_v3")


# Chroma 配置
CHROMA_HOST = os.getenv("CHROMA_HOST", "localhost")
CHROMA_PORT = int(os.getenv("CHROMA_PORT", "8000"))
CHROMA_COLLECTION = os.getenv("CHROMA_COLLECTION", "knowledge_base")
CHROMA_TENANT = os.getenv("CHROMA_TENANT", "default_tenant")
CHROMA_DATABASE = os.getenv("CHROMA_DATABASE", "default_database")


# Flask 配置
FLASK_PORT = int(os.getenv("PORT", "5000"))
FLASK_DEBUG = os.getenv("FLASK_DEBUG", "0") == "1"
