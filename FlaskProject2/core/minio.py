import os
import tempfile
from datetime import datetime
from io import BytesIO

try:
    from minio import Minio
    from minio.error import S3Error
except ImportError:
    Minio = None
    S3Error = None


def _bool_env(name: str, default: bool = False) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() in {"1", "true", "yes", "y", "on"}


class MinIOStorage:
    def __init__(self):
        if Minio is None:
            raise RuntimeError("MinIO 库未安装")
            
        self.endpoint = os.getenv("MINIO_ENDPOINT", "localhost:9000")
        self.access_key = os.getenv("MINIO_ACCESS_KEY")
        self.secret_key = os.getenv("MINIO_SECRET_KEY")
        self.secure = _bool_env("MINIO_SECURE", False)
        self.bucket = os.getenv("MINIO_BUCKET", "uploads")

        if not self.access_key or not self.secret_key:
            raise RuntimeError("MINIO_ACCESS_KEY / MINIO_SECRET_KEY 未设置")

        self.client = Minio(
            self.endpoint,
            access_key=self.access_key,
            secret_key=self.secret_key,
            secure=self.secure,
        )

        self._ensure_bucket()

    def _ensure_bucket(self):
        try:
            if not self.client.bucket_exists(self.bucket):
                self.client.make_bucket(self.bucket)
        except S3Error as e:
            raise RuntimeError(f"MinIO bucket 初始化失败: {e}")

    def build_object_key_chat(self, user_id: int, original_filename: str) -> str:
        now = datetime.now()
        date_path = now.strftime("%Y/%m/%d")
        ts = now.strftime("%H%M%S_%f")
        return f"users/{user_id}/chat/{date_path}/{ts}_{original_filename}"

    def build_object_key_knowledge(self, user_id: int, original_filename: str) -> str:
        now = datetime.now()
        date_path = now.strftime("%Y/%m/%d")
        ts = now.strftime("%H%M%S_%f")
        return f"users/{user_id}/knowledge/{date_path}/{ts}_{original_filename}"

    def upload_fileobj(self, object_key: str, fileobj, content_type: str = "application/octet-stream") -> str:
        fileobj.seek(0, os.SEEK_END)
        size = fileobj.tell()
        fileobj.seek(0)

        self.client.put_object(
            self.bucket,
            object_key,
            fileobj,
            length=size,
            content_type=content_type or "application/octet-stream",
        )
        return object_key

    def download_to_tempfile(self, object_key: str, suffix: str = "") -> str:
        fd, path = tempfile.mkstemp(prefix="minio_", suffix=suffix)
        os.close(fd)
        self.client.fget_object(self.bucket, object_key, path)
        return path
