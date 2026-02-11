"""
数据库管理器 - 使用原生 SQL (PyMySQL)
直接使用 SQL 语句操作 MySQL 数据库
"""
import pymysql
from datetime import datetime
from typing import List, Dict, Optional


class DatabaseManager:
    """数据库管理器 - 使用原生 SQL"""
    
    def __init__(self, 
                 host: str = "localhost",
                 port: int = 3306,
                 user: str = "root",
                 password: str = "Whz258369?",
                 database: str = "chat_app",
                 charset: str = "utf8mb4"):
        """
        初始化MySQL数据库连接
        
        参数:
            host: MySQL服务器地址
            port: MySQL端口（默认3306）
            user: MySQL用户名
            password: MySQL密码
            database: 数据库名称
            charset: 字符集（默认utf8mb4，支持emoji和中文）
        """
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.database = database
        self.charset = charset
        
        # 初始化数据库表
        self._init_tables()
    
    def _get_connection(self):
        """获取数据库连接"""
        return pymysql.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            charset=self.charset,
            cursorclass=pymysql.cursors.DictCursor  # 返回字典格式的结果
        )
    
    def _init_tables(self):
        """初始化数据库表结构"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 创建 conversations 表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INT AUTO_INCREMENT,
                        user_id INT NOT NULL,
                        conversation_id VARCHAR(100) NOT NULL,
                        title VARCHAR(500),
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL,
                        PRIMARY KEY (user_id, conversation_id),
                        KEY (id),
                        INDEX idx_user_id (user_id),
                        INDEX idx_conversation_id (conversation_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """)
                
                # 创建 messages 表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        conversation_id VARCHAR(100) NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        content TEXT NOT NULL,
                        created_at DATETIME NOT NULL,
                        INDEX idx_user_conv (user_id, conversation_id),
                        INDEX idx_conversation_id (conversation_id),
                        FOREIGN KEY (user_id, conversation_id) REFERENCES conversations(user_id, conversation_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """)
                
                conn.commit()
        except Exception as e:
            conn.rollback()
            print(f"[ERROR] 初始化数据库表失败: {e}")
            raise
        finally:
            conn.close()
    
    def verify_user(self, account: str, password: str) -> Optional[Dict]:
        """验证用户登录"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = "SELECT user_id, user_account, user_name FROM users WHERE user_account = %s AND user_password = %s"
                cursor.execute(sql, (account, password))
                result = cursor.fetchone()
                return result
        except Exception as e:
            print(f"[ERROR] 验证用户失败: {e}")
            return None
        finally:
            conn.close()
    
    def create_conversation(self, user_id: int, conversation_id: str, title: str = None) -> Dict:
        """创建新对话"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                now = datetime.now()
                sql = """
                    INSERT INTO conversations (user_id, conversation_id, title, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s)
                """
                cursor.execute(sql, (user_id, conversation_id, title, now, now))
                conn.commit()
                
                # 返回创建的对话信息
                return {
                    'id': cursor.lastrowid,
                    'user_id': user_id,
                    'conversation_id': conversation_id,
                    'title': title or f"对话 {conversation_id[:8]}",
                    'created_at': now.isoformat(),
                    'updated_at': now.isoformat(),
                    'message_count': 0
                }
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    
    def get_conversation(self, user_id: int, conversation_id: str) -> Optional[Dict]:
        """获取对话"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = "SELECT * FROM conversations WHERE user_id = %s AND conversation_id = %s"
                cursor.execute(sql, (user_id, conversation_id))
                result = cursor.fetchone()
                
                if result:
                    # 获取消息数量（使用 user_id 和 conversation_id 联合索引提高效率）
                    cursor.execute("SELECT COUNT(*) as count FROM messages WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                    count_result = cursor.fetchone()
                    message_count = count_result['count'] if count_result else 0
                    
                    return {
                        'id': result['id'],
                        'user_id': result['user_id'],
                        'conversation_id': result['conversation_id'],
                        'title': result['title'] or f"对话 {conversation_id[:8]}",
                        'created_at': result['created_at'].isoformat() if result['created_at'] else None,
                        'updated_at': result['updated_at'].isoformat() if result['updated_at'] else None,
                        'message_count': message_count
                    }
                return None
        finally:
            conn.close()
    
    def get_all_conversations(self, user_id: int, limit: int = 100, order_by: str = 'updated_at') -> List[Dict]:
        """获取用户的所有对话列表"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 验证 order_by 字段，防止 SQL 注入
                valid_columns = ['updated_at', 'created_at', 'id']
                if order_by not in valid_columns:
                    order_by = 'updated_at'
                
                sql = f"""
                    SELECT c.*, COUNT(m.id) as message_count
                    FROM conversations c
                    LEFT JOIN messages m ON c.conversation_id = m.conversation_id
                    WHERE c.user_id = %s
                    GROUP BY c.user_id, c.conversation_id, c.id, c.title, c.created_at, c.updated_at
                    ORDER BY c.{order_by} DESC
                    LIMIT %s
                """
                cursor.execute(sql, (user_id, limit))
                results = cursor.fetchall()
                
                conversations = []
                for row in results:
                    conversations.append({
                        'id': row['id'],
                        'user_id': row['user_id'],
                        'conversation_id': row['conversation_id'],
                        'title': row['title'] or f"对话 {row['conversation_id'][:8]}",
                        'created_at': row['created_at'].isoformat() if row['created_at'] else None,
                        'updated_at': row['updated_at'].isoformat() if row['updated_at'] else None,
                        'message_count': row['message_count'] or 0
                    })
                
                return conversations
        finally:
            conn.close()
    
    def add_message(self, user_id: int, conversation_id: str, role: str, content: str) -> Dict:
        """添加消息到对话"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 确保该用户的对话存在
                cursor.execute("SELECT id FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                conversation = cursor.fetchone()
                
                if not conversation:
                    # 如果对话不存在，创建新对话
                    now = datetime.now()
                    cursor.execute("""
                        INSERT INTO conversations (user_id, conversation_id, title, created_at, updated_at)
                        VALUES (%s, %s, %s, %s, %s)
                    """, (user_id, conversation_id, None, now, now))
                    conn.commit()
                
                # 添加消息
                now = datetime.now()
                cursor.execute("""
                    INSERT INTO messages (user_id, conversation_id, role, content, created_at)
                    VALUES (%s, %s, %s, %s, %s)
                """, (user_id, conversation_id, role, content, now))
                
                message_id = cursor.lastrowid
                
                # 更新对话的更新时间
                cursor.execute("""
                    UPDATE conversations 
                    SET updated_at = %s 
                    WHERE user_id = %s AND conversation_id = %s
                """, (now, user_id, conversation_id))
                
                # 如果没有标题，使用第一条用户消息的前30个字符作为标题
                if role == 'user' and len(content) > 0:
                    cursor.execute("SELECT title FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                    conv_result = cursor.fetchone()
                    if conv_result and not conv_result['title']:
                        title = content[:30].strip()
                        if len(content) > 30:
                            title += "..."
                        cursor.execute("""
                            UPDATE conversations 
                            SET title = %s 
                            WHERE user_id = %s AND conversation_id = %s
                        """, (title, user_id, conversation_id))
                
                conn.commit()
                
                return {
                    'id': message_id,
                    'conversation_id': conversation_id,
                    'role': role,
                    'content': content,
                    'timestamp': now.isoformat()
                }
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    
    def get_messages(self, user_id: int, conversation_id: str, limit: int = None) -> List[Dict]:
        """获取对话的所有消息（校验用户归属）"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 先校验对话是否属于该用户
                cursor.execute("SELECT id FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                if not cursor.fetchone():
                    return []

                sql = """
                    SELECT * FROM messages 
                    WHERE conversation_id = %s 
                    ORDER BY created_at ASC
                """
                if limit:
                    sql += " LIMIT %s"
                    cursor.execute(sql, (conversation_id, limit))
                else:
                    cursor.execute(sql, (conversation_id,))
                
                results = cursor.fetchall()
                
                messages = []
                for row in results:
                    messages.append({
                        'id': row['id'],
                        'conversation_id': row['conversation_id'],
                        'role': row['role'],
                        'content': row['content'],
                        'timestamp': row['created_at'].isoformat() if row['created_at'] else None
                    })
                
                return messages
        finally:
            conn.close()
    
    def delete_conversation(self, user_id: int, conversation_id: str) -> bool:
        """删除用户自己的对话（级联删除所有消息）"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                # 校验并删除
                cursor.execute("DELETE FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                deleted_count = cursor.rowcount
                conn.commit()
                return deleted_count > 0
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    
    def update_conversation_title(self, user_id: int, conversation_id: str, title: str) -> bool:
        """更新对话标题（校验用户归属）"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    UPDATE conversations 
                    SET title = %s 
                    WHERE user_id = %s AND conversation_id = %s
                """, (title, user_id, conversation_id))
                updated_count = cursor.rowcount
                conn.commit()
                return updated_count > 0
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
