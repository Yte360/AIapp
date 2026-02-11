# MinIO 部署完整指南

## 第一步：启动 MinIO 服务

### 1.1 创建 .env 文件

在 `FlaskProject2/minio/` 目录下创建 `.env` 文件：

```bash
cd FlaskProject2/minio
# 复制示例文件
cp env.example .env
# 或手动创建 .env 文件
```

**重要配置项：**
```env
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD="Whz258369?"
MINIO_SERVER_URL=http://192.168.1.26:9000
MINIO_BROWSER_REDIRECT_URL=http://192.168.1.26:9889
APP_PATH=./miniodata
```

### 1.2 启动 MinIO 容器

```bash
cd FlaskProject2/minio
docker-compose up -d
```

### 1.3 验证 MinIO 运行

```bash
# 查看容器状态
docker ps | grep minio

# 查看日志
docker-compose logs -f

# 检查端口
netstat -an | grep 9000
netstat -an | grep 9889
```

---

## 第二步：配置 MinIO（Web 控制台）

### 2.1 登录 Web 控制台

1. 打开浏览器访问: `http://192.168.1.26:9889`
2. 登录信息：
   - Username: `minio`
   - Password: `Whz258369?`

### 2.2 创建 Bucket（存储桶）

1. 点击左侧菜单 "Buckets"
2. 点击 "Create Bucket"
3. 配置：
   - **Bucket Name**: `uploads`（用于存储上传的文件）
   - **Versioning**: Disabled（默认）
   - **Object Locking**: Disabled（默认）
4. 点击 "Create Bucket"

**可选：创建第二个 Bucket**
- Bucket Name: `knowledge-base`（用于知识库文件）
- 或使用同一个 `uploads` bucket，用文件夹区分

### 2.3 创建 Access Key（访问密钥）

1. 点击左侧菜单 "Access Keys"
2. 点击 "Create Access Key"
3. 配置：
   - **Access Key**: 可以自定义或使用自动生成的
   - **Secret Key**: 自动生成（**重要：复制保存，只显示一次**）
   - **Expiry**: 不设置（永久有效）
4. 点击 "Create"
5. **重要**：复制并保存 Access Key 和 Secret Key

**示例：**
```
Access Key: your_access_key_here
Secret Key: your_secret_key_here
```

### 2.4 设置 Bucket 访问策略（可选）

如果需要公开访问文件：
1. 进入 Bucket 设置
2. 点击 "Access Policy"
3. 选择 "Public"（公开）或 "Custom"（自定义）

---

## 第三步：在 Flask 中集成 MinIO

### 3.1 安装 MinIO Python 客户端

```bash
cd FlaskProject2
pip install minio
```

### 3.2 创建 MinIO 配置文件

创建 `FlaskProject2/minio_config.py`:

```python
import os
from minio import Minio
from minio.error import S3Error

# MinIO 配置（从环境变量读取）
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "192.168.1.26:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "your_access_key")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "your_secret_key")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "uploads")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

# 初始化 MinIO 客户端
minio_client = Minio(
    MINIO_ENDPOINT,
    access_key=MINIO_ACCESS_KEY,
    secret_key=MINIO_SECRET_KEY,
    secure=MINIO_SECURE
)

def ensure_bucket_exists(bucket_name: str):
    """确保 Bucket 存在，不存在则创建"""
    try:
        if not minio_client.bucket_exists(bucket_name):
            minio_client.make_bucket(bucket_name)
            print(f"[INFO] 创建 Bucket: {bucket_name}")
    except S3Error as e:
        print(f"[ERROR] 创建 Bucket 失败: {e}")
        raise

# 初始化时确保 Bucket 存在
try:
    ensure_bucket_exists(MINIO_BUCKET)
except Exception as e:
    print(f"[WARN] MinIO 初始化失败: {e}")
```

### 3.3 修改 app.py 集成 MinIO

需要修改的地方：
1. 导入 MinIO 配置
2. 修改文件上传逻辑（`/api/chat-with-file` 和 `/api/knowledge/upload`）
3. 将文件上传到 MinIO 而不是本地

---

## 第四步：修改文件上传逻辑

### 4.1 修改 `/api/chat-with-file` 接口

**当前逻辑：**
- 文件保存到本地 `uploads/chat_*`
- 解析文件内容
- 临时文件保留

**改为：**
- 文件上传到 MinIO
- 解析文件内容（可以从 MinIO 下载或直接使用内存）
- 可选：保留临时文件或直接删除

### 4.2 修改 `/api/knowledge/upload` 接口

**当前逻辑：**
- 文件保存到本地 `uploads/`
- 添加到知识库

**改为：**
- 文件上传到 MinIO
- 从 MinIO 下载到临时目录（用于解析）
- 添加到知识库
- 可选：删除临时文件

---

## 第五步：环境变量配置

在 Flask 应用启动前设置环境变量：

**Windows PowerShell:**
```powershell
$env:MINIO_ENDPOINT="192.168.1.26:9000"
$env:MINIO_ACCESS_KEY="你的AccessKey"
$env:MINIO_SECRET_KEY="你的SecretKey"
$env:MINIO_BUCKET="uploads"
$env:MINIO_SECURE="false"
```

**macOS/Linux:**
```bash
export MINIO_ENDPOINT="192.168.1.26:9000"
export MINIO_ACCESS_KEY="你的AccessKey"
export MINIO_SECRET_KEY="你的SecretKey"
export MINIO_BUCKET="uploads"
export MINIO_SECURE="false"
```

---

## 第六步：测试部署

### 6.1 测试 MinIO 连接

```python
# 测试脚本 test_minio.py
from minio_config import minio_client, MINIO_BUCKET

try:
    # 测试上传文件
    test_file = "test.txt"
    with open(test_file, 'w') as f:
        f.write("Hello MinIO!")
    
    minio_client.fput_object(MINIO_BUCKET, "test/test.txt", test_file)
    print("✓ 文件上传成功")
    
    # 测试下载文件
    minio_client.fget_object(MINIO_BUCKET, "test/test.txt", "downloaded_test.txt")
    print("✓ 文件下载成功")
    
    # 清理
    import os
    os.remove(test_file)
    os.remove("downloaded_test.txt")
    
except Exception as e:
    print(f"✗ 测试失败: {e}")
```

### 6.2 测试 Flask 文件上传

1. 启动 Flask 应用
2. 通过前端上传文件
3. 检查 MinIO Web 控制台，确认文件已上传
4. 验证文件可以正常解析和使用

---

## 第七步：文件访问方式

### 方式 A：预签名 URL（推荐）

生成临时访问链接（默认 7 天有效）：

```python
from datetime import timedelta
from minio_config import minio_client

# 生成预签名 URL（7天有效）
url = minio_client.presigned_get_object(
    MINIO_BUCKET,
    "file_path.pdf",
    expires=timedelta(days=7)
)
```

### 方式 B：公共访问

如果 Bucket 设置为 Public，可以直接访问：
```
http://192.168.1.26:9000/uploads/file_path.pdf
```

### 方式 C：通过 Flask 代理

Flask 从 MinIO 下载后返回给客户端（适合私有文件）

---

## 完整部署检查清单

### MinIO 服务
- [ ] Docker 容器运行正常
- [ ] Web 控制台可以访问
- [ ] Bucket 已创建
- [ ] Access Key 已创建并保存

### Flask 应用
- [ ] MinIO Python 库已安装
- [ ] minio_config.py 已创建
- [ ] 环境变量已配置
- [ ] 文件上传逻辑已修改
- [ ] 测试上传功能正常

### 网络配置
- [ ] IP 地址配置正确（192.168.1.26）
- [ ] 端口未被占用
- [ ] 防火墙允许访问（如需要）

---

## 常见问题

### Q1: MinIO 连接失败
**解决**: 检查 IP 地址、端口、Access Key 和 Secret Key

### Q2: 文件上传失败
**解决**: 检查 Bucket 是否存在，权限是否正确

### Q3: 文件无法访问
**解决**: 检查 Bucket 访问策略，或使用预签名 URL

### Q4: Windows 时区挂载错误
**解决**: 在 docker-compose.yml 中注释掉时区相关的 volumes

---

## 下一步

完成以上步骤后，你的文件将存储在 MinIO 中，而不是本地磁盘。这样可以：
- ✅ 支持分布式部署
- ✅ 更好的扩展性
- ✅ 统一管理所有文件
- ✅ 支持文件版本控制（如果启用）
