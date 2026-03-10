"""
数据库管理器 - 使用 PyMySQL 连接 MySQL
"""
import pymysql
from datetime import datetime, date, timedelta
from typing import List, Dict, Optional
import os


class DatabaseManager:
    """数据库管理器 - 使用 MySQL"""

    def __init__(self,
                 host: str = "localhost",
                 port: int = 3306,
                 user: str = "root",
                 password: str = "123456",
                 database: str = "chat_app",
                 charset: str = "utf8mb4"):

        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.database = database
        self.charset = charset

        self._create_database_if_not_exists()
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
            cursorclass=pymysql.cursors.DictCursor
        )

    def _create_database_if_not_exists(self):
        """创建数据库（如果不存在）"""
        try:
            conn = pymysql.connect(
                host=self.host,
                port=self.port,
                user=self.user,
                password=self.password,
                charset=self.charset
            )
            try:
                with conn.cursor() as cursor:
                    cursor.execute(f"CREATE DATABASE IF NOT EXISTS {self.database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
                conn.commit()
            finally:
                conn.close()
        except Exception as e:
            print(f"[ERROR] 创建数据库失败: {e}")
            raise

    def _init_tables(self):
        """初始化数据库表结构"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_account VARCHAR(50) NOT NULL UNIQUE,
                        user_password VARCHAR(255) NOT NULL,
                        user_name VARCHAR(100) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        conversation_id VARCHAR(100) NOT NULL,
                        title VARCHAR(500),
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL,
                        UNIQUE KEY uk_user_conv (user_id, conversation_id),
                        INDEX idx_user_id (user_id),
                        INDEX idx_conversation_id (conversation_id),
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

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
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

                # 健康记录表 - 新增
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS health_records (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        record_date DATE NOT NULL,
                        session_duration INT DEFAULT 0,
                        focus_minutes INT DEFAULT 0,
                        fatigue_alerts INT DEFAULT 0,
                        avg_stress_level FLOAT DEFAULT 5.0,
                        emotion_data JSON,
                        golden_hour TIME,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_user_date (user_id, record_date),
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

                # 实时状态表 - 新增
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS realtime_status (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        fatigue_level INT,
                        focus_level INT,
                        stress_level INT,
                        current_emotion VARCHAR(20),
                        INDEX idx_user_time (user_id, timestamp)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

                # 疲劳提醒记录表 - 新增
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS fatigue_alerts (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_user_time (user_id, timestamp)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)

                # 插入默认测试用户
                try:
                    cursor.execute("""
                        INSERT INTO users (user_account, user_password, user_name)
                        VALUES (%s, %s, %s)
                    """, ('test', '123456', '测试用户'))
                except pymysql.err.IntegrityError:
                    pass

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

    def save_health_record(self, user_id: int, record_date, session_duration: int = 0,
                          focus_minutes: int = 0, fatigue_alerts: int = 0,
                          avg_stress_level: float = 5.0, emotion_data: dict = None,
                          golden_hour=None) -> bool:
        """保存健康记录"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    INSERT INTO health_records (user_id, record_date, session_duration, focus_minutes,
                                              fatigue_alerts, avg_stress_level, emotion_data, golden_hour)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                        session_duration = VALUES(session_duration),
                        focus_minutes = VALUES(focus_minutes),
                        fatigue_alerts = VALUES(fatigue_alerts),
                        avg_stress_level = VALUES(avg_stress_level),
                        emotion_data = VALUES(emotion_data),
                        golden_hour = VALUES(golden_hour)
                """
                import json
                cursor.execute(sql, (
                    user_id, record_date, session_duration, focus_minutes,
                    fatigue_alerts, avg_stress_level,
                    json.dumps(emotion_data) if emotion_data else None,
                    golden_hour
                ))
                conn.commit()
                return True
        except Exception as e:
            print(f"[ERROR] 保存健康记录失败: {e}")
            return False
        finally:
            conn.close()

    def summarize_daily_data(self, user_id: int, target_date: date = None) -> bool:
        """汇总指定日期的 realtime_status 数据并保存到 health_records"""
        if target_date is None:
            target_date = date.today() - timedelta(days=1)

        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT 
                        COUNT(*) as record_count,
                        AVG(focus_level) as avg_focus,
                        AVG(fatigue_level) as avg_fatigue,
                        AVG(stress_level) as avg_stress,
                        GROUP_CONCAT(current_emotion) as emotions
                    FROM realtime_status
                    WHERE user_id = %s AND DATE(timestamp) = %s
                """
                cursor.execute(sql, (user_id, target_date))
                result = cursor.fetchone()

                if not result or result['record_count'] == 0:
                    return True

                session_duration = result['record_count']
                focus_minutes = int(result['avg_focus'] * session_duration / 10) if result['avg_focus'] else 0
                avg_stress_level = float(result['avg_stress']) if result['avg_stress'] else 5.0

                emotion_str = result['emotions'] or ''
                emotion_data = {}
                if emotion_str:
                    for em in emotion_str.split(','):
                        emotion_data[em] = emotion_data.get(em, 0) + 1

                fatigue_sql = """
                    SELECT COUNT(*) as count FROM fatigue_alerts
                    WHERE user_id = %s AND DATE(timestamp) = %s
                """
                cursor.execute(fatigue_sql, (user_id, target_date))
                fatigue_result = cursor.fetchone()
                fatigue_alerts = fatigue_result['count'] if fatigue_result else 0

                golden_hour = self._calculate_golden_hour(user_id, target_date)

                return self.save_health_record(
                    user_id=user_id,
                    record_date=target_date,
                    session_duration=session_duration,
                    focus_minutes=focus_minutes,
                    fatigue_alerts=fatigue_alerts,
                    avg_stress_level=avg_stress_level,
                    emotion_data=emotion_data,
                    golden_hour=golden_hour
                )
        except Exception as e:
            print(f"[ERROR] 汇总每日数据失败: {e}")
            return False
        finally:
            conn.close()

    def _calculate_golden_hour(self, user_id: int, target_date: date) -> str:
        """计算指定日期的黄金时段"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT HOUR(timestamp) as hour, AVG(focus_level) as avg_focus
                    FROM realtime_status
                    WHERE user_id = %s AND DATE(timestamp) = %s
                    GROUP BY HOUR(timestamp)
                    ORDER BY avg_focus DESC
                    LIMIT 1
                """
                cursor.execute(sql, (user_id, target_date))
                result = cursor.fetchone()

                if result and result['hour'] is not None:
                    best_hour = int(result['hour'])
                    # 返回 HH:MM:SS 格式
                    return f"{best_hour:02d}:00:00"
                return "09:00:00"
        except Exception as e:
            print(f"[ERROR] 计算黄金时段失败: {e}")
            return "09:00:00"
        finally:
            conn.close()

    def get_all_user_ids(self) -> List[int]:
        """获取所有用户ID"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = "SELECT DISTINCT user_id FROM realtime_status"
                cursor.execute(sql)
                results = cursor.fetchall()
                return [r['user_id'] for r in results]
        except Exception as e:
            print(f"[ERROR] 获取用户ID列表失败: {e}")
            return []
        finally:
            conn.close()

    def get_health_records(self, user_id: int, days: int = 7) -> List[Dict]:
        """获取健康记录"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT * FROM health_records
                    WHERE user_id = %s AND record_date >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
                    ORDER BY record_date DESC
                """
                cursor.execute(sql, (user_id, days))
                results = cursor.fetchall()
                for r in results:
                    if r.get('golden_hour'):
                        if isinstance(r['golden_hour'], timedelta):
                            total_seconds = int(r['golden_hour'].total_seconds())
                            r['golden_hour'] = f"{total_seconds // 3600:02d}:{(total_seconds % 3600) // 60:02d}:00"
                return results
        finally:
            conn.close()

    def save_realtime_status(self, user_id: int, fatigue_level: int, focus_level: int,
                           stress_level: int, current_emotion: str) -> bool:
        """保存实时状态"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    INSERT INTO realtime_status (user_id, fatigue_level, focus_level, stress_level, current_emotion)
                    VALUES (%s, %s, %s, %s, %s)
                """
                cursor.execute(sql, (user_id, fatigue_level, focus_level, stress_level, current_emotion))
                conn.commit()
                return True
        except Exception as e:
            print(f"[ERROR] 保存实时状态失败: {e}")
            return False
        finally:
            conn.close()

    def get_realtime_status(self, user_id: int, limit: int = 100, days: int = 0) -> List[Dict]:
        """获取实时状态"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                if days > 0:
                    sql = """
                        SELECT * FROM realtime_status
                        WHERE user_id = %s AND DATE(timestamp) = CURDATE()
                        ORDER BY timestamp DESC
                        LIMIT %s
                    """
                    cursor.execute(sql, (user_id, limit))
                else:
                    sql = """
                        SELECT * FROM realtime_status
                        WHERE user_id = %s
                        ORDER BY timestamp DESC
                        LIMIT %s
                    """
                    cursor.execute(sql, (user_id, limit))
                results = cursor.fetchall()
                for r in results:
                    if r.get('timestamp'):
                        if isinstance(r['timestamp'], timedelta):
                            total_seconds = int(r['timestamp'].total_seconds())
                            r['timestamp'] = f"{total_seconds // 3600:02d}:{(total_seconds % 3600) // 60:02d}:{total_seconds % 60:02d}"
                        else:
                            r['timestamp'] = r['timestamp'].isoformat()
                return results
        finally:
            conn.close()

    def get_week_realtime_status(self, user_id: int) -> List[Dict]:
        """获取本周实时状态（从周一开始到今天）"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT * FROM realtime_status
                    WHERE user_id = %s 
                    AND timestamp >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
                    ORDER BY timestamp ASC
                """
                cursor.execute(sql, (user_id,))
                results = cursor.fetchall()
                for r in results:
                    if r.get('timestamp'):
                        if isinstance(r['timestamp'], timedelta):
                            total_seconds = int(r['timestamp'].total_seconds())
                            r['timestamp'] = f"{total_seconds // 3600:02d}:{(total_seconds % 3600) // 60:02d}:{total_seconds % 60:02d}"
                        else:
                            r['timestamp'] = r['timestamp'].isoformat()
                return results
        finally:
            conn.close()

    def get_user_first_record_date(self, user_id: int) -> Optional[date]:
        """获取用户的首次记录日期"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT MIN(DATE(timestamp)) as first_date 
                    FROM realtime_status 
                    WHERE user_id = %s
                """
                cursor.execute(sql, (user_id,))
                result = cursor.fetchone()
                if result and result.get('first_date'):
                    return result['first_date']
                return None
        finally:
            conn.close()

    def save_fatigue_alert(self, user_id: int) -> bool:
        """保存疲劳提醒"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    INSERT INTO fatigue_alerts (user_id)
                    VALUES (%s)
                """
                cursor.execute(sql, (user_id,))
                conn.commit()
                return True
        except Exception as e:
            print(f"[ERROR] 保存疲劳提醒失败: {e}")
            return False
        finally:
            conn.close()

    def get_fatigue_alert_count_today(self, user_id: int) -> int:
        """获取今日疲劳提醒次数"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                sql = """
                    SELECT COUNT(*) as count FROM fatigue_alerts
                    WHERE user_id = %s AND DATE(timestamp) = CURDATE()
                """
                cursor.execute(sql, (user_id,))
                result = cursor.fetchone()
                return result['count'] if result else 0
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
                cursor.execute("SELECT id FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                conversation = cursor.fetchone()

                if not conversation:
                    now = datetime.now()
                    cursor.execute("""
                        INSERT INTO conversations (user_id, conversation_id, title, created_at, updated_at)
                        VALUES (%s, %s, %s, %s, %s)
                    """, (user_id, conversation_id, None, now, now))
                    conn.commit()

                now = datetime.now()
                cursor.execute("""
                    INSERT INTO messages (user_id, conversation_id, role, content, created_at)
                    VALUES (%s, %s, %s, %s, %s)
                """, (user_id, conversation_id, role, content, now))

                message_id = cursor.lastrowid

                cursor.execute("""
                    UPDATE conversations
                    SET updated_at = %s
                    WHERE user_id = %s AND conversation_id = ?
                """, (now, user_id, conversation_id))

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
        """获取对话的所有消息"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                cursor.execute("SELECT id FROM conversations WHERE user_id = %s AND conversation_id = %s", (user_id, conversation_id))
                if not cursor.fetchone():
                    return []

                sql = "SELECT * FROM messages WHERE conversation_id = %s ORDER BY created_at ASC"
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
        """删除用户自己的对话"""
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
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
        """更新对话标题"""
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
