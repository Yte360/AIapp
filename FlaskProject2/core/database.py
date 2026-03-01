"""
数据库管理器 - 使用 SQLite
避免 MySQL 连接问题
"""
import sqlite3
from datetime import datetime
from typing import List, Dict, Optional
import os


class DatabaseManager:
    """数据库管理器 - 使用 SQLite"""

    def __init__(self,
                 db_file: str = "chat_app.db"):

        """
        初始化SQLite数据库连接

        参数:
            db_file: SQLite数据库文件路径
        """
        self.db_file = db_file

        db_dir = os.path.dirname(db_file) or "."
        if db_dir and not os.path.exists(db_dir):
            os.makedirs(db_dir, exist_ok=True)

        self._init_tables()

    def _get_connection(self):
        """获取数据库连接"""
        conn = sqlite3.connect(self.db_file)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_tables(self):
        """初始化数据库表结构"""
        conn = self._get_connection()
        try:
            with conn:
                conn.execute("PRAGMA foreign_keys = ON;")

                conn.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_account TEXT NOT NULL UNIQUE,
                        user_password TEXT NOT NULL,
                        user_name TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """)

                conn.execute("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        conversation_id TEXT NOT NULL,
                        title TEXT,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        UNIQUE (user_id, conversation_id),
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    )
                """)

                conn.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        conversation_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        FOREIGN KEY (user_id, conversation_id) REFERENCES conversations(user_id, conversation_id) ON DELETE CASCADE
                    )
                """)

                try:
                    conn.execute("""
                        INSERT INTO users (user_account, user_password, user_name, created_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    """, ('test', '123456', '测试用户'))
                except sqlite3.IntegrityError:
                    pass
        except Exception as e:
            print(f"[ERROR] 初始化数据库表失败: {e}")
            raise
        finally:
            conn.close()

    def verify_user(self, account: str, password: str) -> Optional[Dict]:
        """验证用户登录"""
        conn = self._get_connection()
        try:
            with conn:
                sql = "SELECT user_id, user_account, user_name FROM users WHERE user_account = ? AND user_password = ?"
                cursor = conn.execute(sql, (account, password))
                result = cursor.fetchone()
                return dict(result) if result else None
        except Exception as e:
            print(f"[ERROR] 验证用户失败: {e}")
            return None
        finally:
            conn.close()

    def create_conversation(self, user_id: int, conversation_id: str, title: str = None) -> Dict:
        """创建新对话"""
        conn = self._get_connection()
        try:
            with conn:
                now = datetime.now()
                cursor = conn.execute("""
                    INSERT INTO conversations (user_id, conversation_id, title, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                """, (user_id, conversation_id, title, now, now))

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
            raise e
        finally:
            conn.close()

    def get_conversation(self, user_id: int, conversation_id: str) -> Optional[Dict]:
        """获取对话"""
        conn = self._get_connection()
        try:
            with conn:
                sql = "SELECT * FROM conversations WHERE user_id = ? AND conversation_id = ?"
                cursor = conn.execute(sql, (user_id, conversation_id))
                result = cursor.fetchone()

                if result:
                    result_dict = dict(result)
                    cursor = conn.execute("SELECT COUNT(*) as count FROM messages WHERE user_id = ? AND conversation_id = ?", (user_id, conversation_id))
                    count_result = cursor.fetchone()
                    message_count = count_result['count'] if count_result else 0

                    return {
                        'id': result_dict['id'],
                        'user_id': result_dict['user_id'],
                        'conversation_id': result_dict['conversation_id'],
                        'title': result_dict['title'] or f"对话 {conversation_id[:8]}",
                        'created_at': result_dict['created_at'] if result_dict['created_at'] else None,
                        'updated_at': result_dict['updated_at'] if result_dict['updated_at'] else None,
                        'message_count': message_count
                    }
                return None
        finally:
            conn.close()

    def get_all_conversations(self, user_id: int, limit: int = 100, order_by: str = 'updated_at') -> List[Dict]:
        """获取用户的所有对话列表"""
        conn = self._get_connection()
        try:
            with conn:
                valid_columns = ['updated_at', 'created_at', 'id']
                if order_by not in valid_columns:
                    order_by = 'updated_at'

                sql = f"""
                    SELECT c.*, COUNT(m.id) as message_count
                    FROM conversations c
                    LEFT JOIN messages m ON c.conversation_id = m.conversation_id
                    WHERE c.user_id = ?
                    GROUP BY c.user_id, c.conversation_id, c.id, c.title, c.created_at, c.updated_at
                    ORDER BY c.{order_by} DESC
                    LIMIT ?
                """
                cursor = conn.execute(sql, (user_id, limit))
                results = cursor.fetchall()

                conversations = []
                for row in results:
                    row_dict = dict(row)
                    conversations.append({
                        'id': row_dict['id'],
                        'user_id': row_dict['user_id'],
                        'conversation_id': row_dict['conversation_id'],
                        'title': row_dict['title'] or f"对话 {row_dict['conversation_id'][:8]}",
                        'created_at': row_dict['created_at'] if row_dict['created_at'] else None,
                        'updated_at': row_dict['updated_at'] if row_dict['updated_at'] else None,
                        'message_count': row_dict['message_count'] or 0
                    })

                return conversations
        finally:
            conn.close()

    def add_message(self, user_id: int, conversation_id: str, role: str, content: str) -> Dict:
        """添加消息到对话"""
        conn = self._get_connection()
        try:
            with conn:
                cursor = conn.execute("SELECT id FROM conversations WHERE user_id = ? AND conversation_id = ?", (user_id, conversation_id))
                conversation = cursor.fetchone()

                if not conversation:
                    now = datetime.now()
                    conn.execute("""
                        INSERT INTO conversations (user_id, conversation_id, title, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                    """, (user_id, conversation_id, None, now, now))

                now = datetime.now()
                cursor = conn.execute("""
                    INSERT INTO messages (user_id, conversation_id, role, content, created_at)
                    VALUES (?, ?, ?, ?, ?)
                """, (user_id, conversation_id, role, content, now))

                message_id = cursor.lastrowid

                conn.execute("""
                    UPDATE conversations
                    SET updated_at = ?
                    WHERE user_id = ? AND conversation_id = ?
                """, (now, user_id, conversation_id))

                if role == 'user' and len(content) > 0:
                    cursor = conn.execute("SELECT title FROM conversations WHERE user_id = ? AND conversation_id = ?", (user_id, conversation_id))
                    conv_result = cursor.fetchone()
                    if conv_result and not conv_result['title']:
                        title = content[:30].strip()
                        if len(content) > 30:
                            title += "..."
                        conn.execute("""
                            UPDATE conversations
                            SET title = ?
                            WHERE user_id = ? AND conversation_id = ?
                        """, (title, user_id, conversation_id))

                return {
                    'id': message_id,
                    'conversation_id': conversation_id,
                    'role': role,
                    'content': content,
                    'timestamp': now.isoformat()
                }
        except Exception as e:
            raise e
        finally:
            conn.close()

    def get_messages(self, user_id: int, conversation_id: str, limit: int = None) -> List[Dict]:
        """获取对话的所有消息"""
        conn = self._get_connection()
        try:
            with conn:
                cursor = conn.execute("SELECT id FROM conversations WHERE user_id = ? AND conversation_id = ?", (user_id, conversation_id))
                if not cursor.fetchone():
                    return []

                sql = """
                    SELECT * FROM messages
                    WHERE conversation_id = ?
                    ORDER BY created_at ASC
                """
                if limit:
                    sql += " LIMIT ?"
                    cursor = conn.execute(sql, (conversation_id, limit))
                else:
                    cursor = conn.execute(sql, (conversation_id,))

                results = cursor.fetchall()

                messages = []
                for row in results:
                    row_dict = dict(row)
                    messages.append({
                        'id': row_dict['id'],
                        'conversation_id': row_dict['conversation_id'],
                        'role': row_dict['role'],
                        'content': row_dict['content'],
                        'timestamp': row_dict['created_at'] if row_dict['created_at'] else None
                    })

                return messages
        finally:
            conn.close()

    def delete_conversation(self, user_id: int, conversation_id: str) -> bool:
        """删除用户自己的对话"""
        conn = self._get_connection()
        try:
            with conn:
                cursor = conn.execute("DELETE FROM conversations WHERE user_id = ? AND conversation_id = ?", (user_id, conversation_id))
                deleted_count = cursor.rowcount
                return deleted_count > 0
        except Exception as e:
            raise e
        finally:
            conn.close()

    def update_conversation_title(self, user_id: int, conversation_id: str, title: str) -> bool:
        """更新对话标题"""
        conn = self._get_connection()
        try:
            with conn:
                cursor = conn.execute("""
                    UPDATE conversations
                    SET title = ?
                    WHERE user_id = ? AND conversation_id = ?
                """, (title, user_id, conversation_id))
                updated_count = cursor.rowcount
                return updated_count > 0
        except Exception as e:
            raise e
        finally:
            conn.close()
