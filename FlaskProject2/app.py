import os
from flask import Flask, jsonify
from apscheduler.schedulers.background import BackgroundScheduler

from core.clients import db_manager, rag_system, minio_storage

from routes.auth import bp as auth_bp
from routes.chat import bp as chat_bp
from routes.knowledge import bp as knowledge_bp
from routes.conversation import bp as conversation_bp
from routes.tts import bp as tts_bp
from routes.health import health_bp

from config.settings import UPLOAD_FOLDER, MAX_CONTENT_LENGTH, FLASK_PORT, FLASK_DEBUG


app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH

app.register_blueprint(auth_bp)
app.register_blueprint(chat_bp)
app.register_blueprint(knowledge_bp)
app.register_blueprint(conversation_bp)
app.register_blueprint(tts_bp)
app.register_blueprint(health_bp)

# 确保上传文件夹存在
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


def daily_summarize():
    """每天凌晨执行的汇总任务"""
    try:
        from datetime import date, timedelta
        yesterday = date.today() - timedelta(days=1)
        
        if db_manager:
            user_ids = db_manager.get_all_user_ids()
            if not user_ids:
                user_ids = [1]
            
            for user_id in user_ids:
                db_manager.summarize_daily_data(user_id, yesterday)
            
            print(f"[定时任务] 成功汇总 {len(user_ids)} 个用户的 {yesterday} 数据")
    except Exception as e:
        print(f"[定时任务] 汇总失败: {e}")


scheduler = BackgroundScheduler()
scheduler.add_job(func=daily_summarize, trigger="cron", hour=0, minute=0)
scheduler.start()

# 确保scheduler在应用退出时正确关闭
import atexit
atexit.register(lambda: scheduler.shutdown() if scheduler.running else None)


@app.route("/health", methods=["GET"])  # 健康检查
def health():
    return jsonify({"status": "ok"})


@app.route("/api/ai-info", methods=["GET"])  # 获取AI信息
def get_ai_info():
    return jsonify({
        "ok": True,
        "ai_role": "学习专家",
        "description": "专业的大学学习顾问，提供学习建议和规划服务",
        "capabilities": [
            "学习方法推荐",
            "学习资源推荐",
            "学习技巧分享",
            "学习计划制定",
            "学习问题解答"
        ]
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=FLASK_PORT, debug=FLASK_DEBUG)


