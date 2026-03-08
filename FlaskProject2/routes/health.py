from flask import Blueprint, request, jsonify
from datetime import datetime, date, timedelta
import json
import os
from core.clients import db_manager

health_bp = Blueprint('health', __name__, url_prefix='/api/health')

EMOTION_TEXT = {
    'HAPPY': '开心',
    'SAD': '难过',
    'ANGRY': '生气',
    'SURPRISED': '惊讶',
    'FEARFUL': '害怕',
    'DISGUSTED': '厌恶',
    'NEUTRAL': '平静'
}


def calculate_golden_hour(user_id: int, mode: str = "daily") -> str:
    """计算黄金时段
    mode: "daily" - 今日黄金时段，和每日概览一致
          "weekly" - 本周黄金时段，统计本周已过去天数的黄金时段
    """
    try:
        if not db_manager:
            return "09:00-11:00"
        
        if mode == "daily":
            return _calculate_daily_golden_hour(user_id)
        else:
            return _calculate_weekly_golden_hour(user_id)
            
    except Exception as e:
        print(f"[ERROR] 计算黄金时段失败: {e}")
        return "09:00-11:00"


def _calculate_daily_golden_hour(user_id: int) -> str:
    """计算今日黄金时段"""
    statuses = db_manager.get_realtime_status(user_id, 1000, 1)
    
    if not statuses:
        return "09:00-11:00"
    
    hourly_focus = {}
    for s in statuses:
        ts = s.get('timestamp')
        if isinstance(ts, datetime):
            hour = ts.hour
            focus = s.get('focus_level', 5)
            if hour not in hourly_focus:
                hourly_focus[hour] = []
            hourly_focus[hour].append(focus)
    
    if not hourly_focus:
        return "09:00-11:00"
    
    hourly_avg = {hour: sum(focus_list)/len(focus_list) for hour, focus_list in hourly_focus.items()}
    best_hour = max(hourly_avg.keys(), key=lambda h: hourly_avg[h])
    
    return _format_golden_hour(best_hour)


def _calculate_weekly_golden_hour(user_id: int) -> str:
    """计算本周黄金时段：统计本周已过去天数的golden_hour"""
    today = date.today()
    weekday = today.weekday()
    monday = today - timedelta(days=weekday)
    
    records = db_manager.get_health_records(user_id, days=weekday + 1)
    
    golden_hours = []
    for record in records:
        record_date = record.get('record_date')
        if isinstance(record_date, date) and record_date >= monday and record_date < today:
            gh = record.get('golden_hour')
            if gh:
                if isinstance(gh, timedelta):
                    total_seconds = int(gh.total_seconds())
                    gh = f"{total_seconds // 3600:02d}:{(total_seconds % 3600) // 60:02d}:00"
                golden_hours.append(gh)
    
    if not golden_hours:
        return _calculate_daily_golden_hour(user_id)
    
    hour_counts = {}
    hour_focus = {}
    for gh in golden_hours:
        if gh not in hour_counts:
            hour_counts[gh] = 0
            hour_focus[gh] = []
        hour_counts[gh] += 1
    
    max_count = max(hour_counts.values())
    top_hours = [h for h, c in hour_counts.items() if c == max_count]
    
    if len(top_hours) == 1:
        return top_hours[0]
    else:
        hour_focus_avg = {}
        for gh in top_hours:
            try:
                start_hour = int(gh.split(':')[0])
                hour_focus_avg[gh] = start_hour
            except:
                hour_focus_avg[gh] = 0
        return max(hour_focus_avg.keys(), key=lambda h: hour_focus_avg[h])


def _format_golden_hour(hour: int) -> str:
    """格式化黄金时段"""
    next_hour = (hour + 1) % 24
    if hour < 12:
        return f"上午 {hour:02d}:00-{next_hour:02d}:00"
    elif hour < 18:
        return f"下午 {hour-12:02d}:00-{next_hour-12:02d}:00"
    else:
        return f"晚上 {hour-12:02d}:00-{next_hour-12:02d}:00"


def call_qwen_api_async(prompt: str, callback):
    """异步调用通义千问API"""
    try:
        import dashscope
        api_key = os.getenv("DASHSCOPE_API_KEY")
        if not api_key:
            print("[WARN] 未配置 DASHSCOPE_API_KEY 环境变量")
            callback(None)
            return

        dashscope.api_key = api_key

        from dashscope import Generation
        response = Generation.call(
            model='qwen-plus',
            prompt=prompt
        )

        if response.status_code == 200:
            callback(response.output['text'])
        else:
            print(f"[ERROR] 通义千问API错误: {response.code} - {response.message}")
            callback(None)
    except Exception as e:
        print(f"[ERROR] 通义千问API调用失败: {e}")
        callback(None)


def call_qwen_api(prompt: str, timeout: int = 3) -> str:
    """调用通义千问API（同步版本，带超时）"""
    try:
        import dashscope
        api_key = os.getenv("DASHSCOPE_API_KEY")
        if not api_key:
            print("[WARN] 未配置 DASHSCOPE_API_KEY 环境变量")
            return None

        dashscope.api_key = api_key

        from dashscope import Generation
        response = Generation.call(
            model='qwen-plus',
            prompt=prompt
        )

        if response.status_code == 200:
            return response.output['text']
        else:
            print(f"[ERROR] 通义千问API错误: {response.code} - {response.message}")
            return None
    except Exception as e:
        print(f"[ERROR] 通义千问API调用失败: {e}")
        return None


@health_bp.route('/save-status', methods=['POST'])
def save_realtime_status():
    """保存实时状态"""
    try:
        data = request.json
        user_id = data.get('user_id', 1)
        fatigue_level = data.get('fatigue_level', 5)
        focus_level = data.get('focus_level', 5)
        stress_level = data.get('stress_level', 5)
        current_emotion = data.get('current_emotion', 'NEUTRAL')

        if db_manager:
            db_manager.save_realtime_status(
                user_id=user_id,
                fatigue_level=fatigue_level,
                focus_level=focus_level,
                stress_level=stress_level,
                current_emotion=current_emotion
            )

        return jsonify({'ok': True, 'message': '状态保存成功'})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/realtime-status', methods=['GET'])
def get_realtime_status():
    """获取实时状态列表"""
    try:
        user_id = int(request.args.get('user_id', 1))
        limit = int(request.args.get('limit', 100))
        days = int(request.args.get('days', 0))

        if db_manager:
            statuses = db_manager.get_realtime_status(user_id, limit, days)
            return jsonify({
                'ok': True,
                'data': [
                    {
                        'id': s['id'],
                        'user_id': s['user_id'],
                        'timestamp': s['timestamp'].timestamp() if isinstance(s['timestamp'], datetime) else 0,
                        'fatigue_level': s['fatigue_level'],
                        'focus_level': s['focus_level'],
                        'stress_level': s['stress_level'],
                        'current_emotion': s['current_emotion']
                    }
                    for s in statuses
                ]
            })
        return jsonify({'ok': True, 'data': []})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/save-fatigue-alert', methods=['POST'])
def save_fatigue_alert():
    """保存疲劳提醒"""
    try:
        data = request.json
        user_id = data.get('user_id', 1)

        if db_manager:
            db_manager.save_fatigue_alert(user_id)

        return jsonify({'ok': True, 'message': '疲劳提醒保存成功'})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/fatigue-alerts-today', methods=['GET'])
def get_fatigue_alerts_today():
    """获取今日疲劳提醒次数"""
    try:
        user_id = int(request.args.get('user_id', 1))

        count = 0
        if db_manager:
            count = db_manager.get_fatigue_alert_count_today(user_id)

        return jsonify({'ok': True, 'count': count})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/week-trend', methods=['GET'])
def get_week_trend():
    """获取周趋势 - 过去7天统计"""
    try:
        user_id = int(request.args.get('user_id', 1))

        if not db_manager:
            return jsonify({'ok': False, 'error': '数据库未连接'}), 500

        records = db_manager.get_health_records(user_id, days=7)

        daily_statuses = []
        total_study_minutes = 0
        total_focus = 0
        total_fatigue = 0
        total_stress = 0
        valid_days = 0

        emotion_counts_map = {}

        for record in records:
            record_date = record['record_date']
            if isinstance(record_date, date):
                date_str = record_date.strftime('%Y-%m-%d')

                focus_score = min(100, (record.get('focus_minutes', 0) / 10) * 10)
                fatigue_score = min(100, record.get('fatigue_alerts', 0) * 20)
                stress_score = int(record.get('avg_stress_level', 5) * 10)

                emotion_data = record.get('emotion_data')
                if isinstance(emotion_data, str):
                    try:
                        emotion_data = json.loads(emotion_data)
                    except:
                        emotion_data = {}

                daily_statuses.append({
                    'date': date_str,
                    'focus_score': focus_score,
                    'fatigue_score': fatigue_score,
                    'stress_score': stress_score,
                    'emotion_counts': emotion_data or {}
                })

                total_study_minutes += record.get('session_duration', 0) // 60
                total_focus += focus_score
                total_fatigue += fatigue_score
                total_stress += stress_score
                valid_days += 1

                for em, count in (emotion_data or {}).items():
                    emotion_counts_map[em] = emotion_counts_map.get(em, 0) + count

        if valid_days > 0:
            average_focus = total_focus / valid_days
            average_fatigue = total_fatigue / valid_days
            average_stress = total_stress / valid_days
        else:
            average_focus = average_fatigue = average_stress = 50

        golden_hour = calculate_golden_hour(user_id, mode="weekly")

        trend_analysis = get_fallback_trend_analysis(average_focus, average_fatigue, average_stress, valid_days)

        end_date = date.today()
        start_date = end_date - timedelta(days=6)

        result = {
            'ok': True,
            'data': {
                'start_date': start_date.strftime('%Y-%m-%d'),
                'end_date': end_date.strftime('%Y-%m-%d'),
                'daily_statuses': daily_statuses,
                'average_focus': round(average_focus, 1),
                'average_fatigue': round(average_fatigue, 1),
                'average_stress': round(average_stress, 1),
                'total_study_minutes': total_study_minutes,
                'golden_hour': golden_hour,
                'trend_analysis': trend_analysis
            }
        }
        return jsonify(result)
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/weekly-report', methods=['GET'])
def get_weekly_report():
    """获取周报 - 自然周统计"""
    try:
        user_id = int(request.args.get('user_id', 1))

        if not db_manager:
            return jsonify({'ok': False, 'error': '数据库未连接'}), 500

        today = date.today()
        weekday = today.weekday()
        monday = today - timedelta(days=weekday)

        historical_records = {}
        if db_manager:
            week_records = db_manager.get_health_records(user_id, days=7)
            for record in week_records:
                record_date = record.get('record_date')
                if isinstance(record_date, date) and record_date >= monday and record_date < today:
                    date_str = record_date.strftime('%Y-%m-%d')
                    historical_records[date_str] = record

        today_statuses = []
        if db_manager:
            today_statuses = db_manager.get_realtime_status(user_id, 1000, 1)

        today_record = None
        if db_manager:
            today_records = db_manager.get_health_records(user_id, days=1)
            for r in today_records:
                if r.get('record_date') == today:
                    today_record = r
                    break

        today_data = {
            'focus_levels': [],
            'fatigue_levels': [],
            'stress_levels': [],
            'emotions': []
        }
        for status in today_statuses:
            if isinstance(status.get('timestamp'), datetime):
                today_data['focus_levels'].append(status.get('focus_level', 5))
                today_data['fatigue_levels'].append(status.get('fatigue_level', 5))
                today_data['stress_levels'].append(status.get('stress_level', 5))
                today_data['emotions'].append(status.get('current_emotion', 'NEUTRAL'))

        daily_statuses = []
        total_study_minutes = 0
        total_focus = 0
        total_fatigue = 0
        total_stress = 0
        valid_days = 0
        emotion_counts_map = {}

        current = monday
        while current <= today:
            date_str = current.strftime('%Y-%m-%d')
            
            if current == today:
                if today_record:
                    focus_score = min(100, (today_record.get('focus_minutes', 0) / 10) * 10)
                    fatigue_score = min(100, today_record.get('fatigue_alerts', 0) * 20)
                    stress_score = int(today_record.get('avg_stress_level', 5) * 10)

                    emotion_data = today_record.get('emotion_data')
                    if isinstance(emotion_data, str):
                        try:
                            emotion_data = json.loads(emotion_data)
                        except:
                            emotion_data = {}

                    daily_statuses.append({
                        'date': date_str,
                        'focus_score': focus_score,
                        'fatigue_score': fatigue_score,
                        'stress_score': stress_score,
                        'emotion_counts': emotion_data or {}
                    })

                    total_study_minutes += today_record.get('session_duration', 0) // 60
                    total_focus += focus_score
                    total_fatigue += fatigue_score
                    total_stress += stress_score
                    valid_days += 1

                    for em, count in (emotion_data or {}).items():
                        emotion_counts_map[em] = emotion_counts_map.get(em, 0) + count
                elif today_data['focus_levels']:
                    focus_avg = sum(today_data['focus_levels']) / len(today_data['focus_levels'])
                    fatigue_avg = sum(today_data['fatigue_levels']) / len(today_data['fatigue_levels'])
                    stress_avg = sum(today_data['stress_levels']) / len(today_data['stress_levels'])

                    focus_score = int(focus_avg * 10)
                    fatigue_score = int(fatigue_avg * 10)
                    stress_score = int(stress_avg * 10)

                    emotion_counts = {}
                    for em in today_data['emotions']:
                        emotion_counts[em] = emotion_counts.get(em, 0) + 1

                    daily_statuses.append({
                        'date': date_str,
                        'focus_score': focus_score,
                        'fatigue_score': fatigue_score,
                        'stress_score': stress_score,
                        'emotion_counts': emotion_counts
                    })

                    total_study_minutes += 0  # 实时数据无法准确计算学习时长
                    total_focus += focus_score
                    total_fatigue += fatigue_score
                    total_stress += stress_score
                    valid_days += 1

                    for em, count in emotion_counts.items():
                        emotion_counts_map[em] = emotion_counts_map.get(em, 0) + count
                else:
                    daily_statuses.append({
                        'date': date_str,
                        'focus_score': 0,
                        'fatigue_score': 0,
                        'stress_score': 0,
                        'emotion_counts': {}
                    })
            elif date_str in historical_records:
                record = historical_records[date_str]
                focus_score = min(100, (record.get('focus_minutes', 0) / 10) * 10)
                fatigue_score = min(100, record.get('fatigue_alerts', 0) * 20)
                stress_score = int(record.get('avg_stress_level', 5) * 10)

                emotion_data = record.get('emotion_data')
                if isinstance(emotion_data, str):
                    try:
                        emotion_data = json.loads(emotion_data)
                    except:
                        emotion_data = {}

                daily_statuses.append({
                    'date': date_str,
                    'focus_score': focus_score,
                    'fatigue_score': fatigue_score,
                    'stress_score': stress_score,
                    'emotion_counts': emotion_data or {}
                })

                total_study_minutes += record.get('session_duration', 0) // 60
                total_focus += focus_score
                total_fatigue += fatigue_score
                total_stress += stress_score
                valid_days += 1

                for em, count in (emotion_data or {}).items():
                    emotion_counts_map[em] = emotion_counts_map.get(em, 0) + count
            else:
                daily_statuses.append({
                    'date': date_str,
                    'focus_score': 0,
                    'fatigue_score': 0,
                    'stress_score': 0,
                    'emotion_counts': {}
                })

            current += timedelta(days=1)

        if valid_days > 0:
            average_focus = total_focus / valid_days
            average_fatigue = total_fatigue / valid_days
            average_stress = total_stress / valid_days
        else:
            average_focus = average_fatigue = average_stress = 50

        golden_hour = calculate_golden_hour(user_id, mode="weekly")

        trend_analysis = get_fallback_trend_analysis(average_focus, average_fatigue, average_stress, valid_days)
        recommendations = get_fallback_recommendations(average_focus, average_fatigue, average_stress)

        result = {
            'ok': True,
            'data': {
                'start_date': monday.strftime('%Y-%m-%d'),
                'end_date': today.strftime('%Y-%m-%d'),
                'daily_statuses': daily_statuses,
                'average_focus': round(average_focus, 1),
                'average_fatigue': round(average_fatigue, 1),
                'average_stress': round(average_stress, 1),
                'total_study_minutes': total_study_minutes,
                'golden_hour': golden_hour,
                'trend_analysis': trend_analysis,
                'recommendations': recommendations
            }
        }
        return jsonify(result)
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'ok': False, 'error': str(e)}), 500


def generate_trend_analysis(avg_focus, avg_fatigue, avg_stress, days, daily_statuses=None):
    """生成趋势分析 - 调用AI"""
    if days == 0:
        return "暂无数据，请开始学习以获得个性化分析。"

    daily_summary = ""
    if daily_statuses:
        for status in daily_statuses:
            emotions = status.get('emotion_counts', {})
            emotion_str = ", ".join([f"{EMOTION_TEXT.get(em, em)}:{count}次" for em, count in emotions.items()])
            daily_summary += f"""
- {status.get('date')}: 专注力{status.get('focus_score')}分, 疲劳{status.get('fatigue_score')}分, 压力{status.get('stress_score')}分, 情绪({emotion_str})"""

    prompt = f"""
你是一名专为考研学生服务的智能心理教练。请根据以下本周学习数据，生成个性化趋势分析：

【本周数据概览】
- 统计天数：{days} 天
- 平均专注力：{avg_focus:.1f}/100
- 平均疲劳度：{avg_fatigue:.1f}/100
- 平均压力值：{avg_stress:.1f}/100
{daily_summary}

请生成一段趋势分析（不超过150字），要求：
1. 基于真实数据分析本周学习状态
2. 指出专注力、疲劳、压力的变化趋势
3. 给出具体改进建议
4. 语言温暖，专业
"""

    ai_analysis = call_qwen_api(prompt)
    
    if ai_analysis:
        return ai_analysis
    else:
        return get_fallback_trend_analysis(avg_focus, avg_fatigue, avg_stress, days)


def get_fallback_trend_analysis(avg_focus, avg_fatigue, avg_stress, days):
    """备用趋势分析（当AI不可用时）"""
    if days == 0:
        return "暂无数据，请开始学习以获得个性化分析。"

    analysis_parts = []

    if avg_focus >= 70:
        analysis_parts.append("本周您的专注力表现优异，学习效率较高。")
    elif avg_focus >= 50:
        analysis_parts.append("本周专注力一般，建议合理安排休息时间。")
    else:
        analysis_parts.append("本周专注力有待提升，注意避免分心。")

    if avg_fatigue >= 60:
        analysis_parts.append("疲劳指数偏高，建议增加休息频率，保证充足睡眠。")
    else:
        analysis_parts.append("精力管理良好，保持当前的学习节奏。")

    if avg_stress >= 60:
        analysis_parts.append("压力较大，可通过运动或冥想缓解。")
    elif avg_stress >= 40:
        analysis_parts.append("压力适中，保持良好的心态。")
    else:
        analysis_parts.append("心态平和，继续保持！")

    if avg_focus > avg_stress:
        analysis_parts.append("建议将高难度学习任务安排在精力充沛的时段。")

    return " ".join(analysis_parts)


def generate_weekly_recommendations(avg_focus, avg_fatigue, avg_stress, days, daily_statuses=None):
    """生成个性化建议 - 调用AI"""
    if days == 0:
        return "暂无建议，请开始学习以获得个性化建议。"

    daily_summary = ""
    if daily_statuses:
        for status in daily_statuses:
            emotions = status.get('emotion_counts', {})
            emotion_str = ", ".join([f"{EMOTION_TEXT.get(em, em)}:{count}次" for em, count in emotions.items()])
            daily_summary += f"""
- {status.get('date')}: 专注力{status.get('focus_score')}分, 疲劳{status.get('fatigue_score')}分, 压力{status.get('stress_score')}分, 情绪({emotion_str})"""

    prompt = f"""
你是一名专为考研学生服务的智能心理教练。请根据以下本周学习数据，生成个性化改进建议：

【本周数据概览】
- 统计天数：{days} 天
- 平均专注力：{avg_focus:.1f}/100
- 平均疲劳度：{avg_fatigue:.1f}/100
- 平均压力值：{avg_stress:.1f}/100
{daily_summary}

请生成3-5条具体可执行的改进建议，要求：
1. 针对本周存在的问题给出具体解决方案
2. 建议要具体、可执行（如：具体时间、具体方法）
3. 格式：每条建议一行，用数字开头
4. 总字数不超过200字
5. 语言温暖，专业
"""

    ai_recommendations = call_qwen_api(prompt)
    
    if ai_recommendations:
        return ai_recommendations
    else:
        return get_fallback_recommendations(avg_focus, avg_fatigue, avg_stress)


def get_fallback_recommendations(avg_focus, avg_fatigue, avg_stress):
    """备用建议（当AI不可用时）"""
    recommendations = [
        "1. 保持规律的作息时间，有助于提高学习效率",
        "2. 每学习45分钟后休息10分钟",
        "3. 注意用眼健康，适当做眼保健操",
        "4. 保持良好的心态，适当运动放松"
    ]
    return "\n".join(recommendations)


@health_bp.route('/record', methods=['POST'])
def record_health():
    """记录每日健康数据"""
    try:
        data = request.json
        user_id = data.get('user_id', 1)
        session_duration = data.get('session_duration', 0)
        focus_minutes = data.get('focus_minutes', 0)
        fatigue_alerts = data.get('fatigue_alerts', 0)
        avg_stress_level = data.get('avg_stress_level', 5.0)
        emotion_data = data.get('emotion_data', {})
        golden_hour = data.get('golden_hour')

        if db_manager:
            db_manager.save_health_record(
                user_id=user_id,
                record_date=date.today(),
                session_duration=session_duration,
                focus_minutes=focus_minutes,
                fatigue_alerts=fatigue_alerts,
                avg_stress_level=avg_stress_level,
                emotion_data=emotion_data,
                golden_hour=golden_hour
            )

        return jsonify({'ok': True, 'message': '记录保存成功'})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500


@health_bp.route('/analyze', methods=['POST'])
def analyze_health():
    """AI健康分析 - 调用通义千问"""
    try:
        data = request.json
        current_emotion = data.get('current_emotion', 'NEUTRAL')
        fatigue_level = data.get('fatigue_level', 5)
        focus_level = data.get('focus_level', 5)
        stress_level = data.get('stress_level', 5)
        session_duration = data.get('session_duration', 0)
        fatigue_alerts = data.get('fatigue_alerts', 0)
        emotion_history = data.get('emotion_history', [])

        emotion_name = EMOTION_TEXT.get(current_emotion, current_emotion)

        prompt = f"""
你是一名专为考研学生服务的智能心理教练。请根据以下数据，为用户提供个性化调节建议：

【当前状态】
- 连续学习时长：{session_duration // 60} 分钟
- 疲劳提醒次数：{fatigue_alerts} 次
- 当前情绪：{emotion_name}
- 疲劳程度：{fatigue_level}/10
- 专注程度：{focus_level}/10
- 压力程度：{stress_level}/10
- 近期情绪记录：{emotion_history[-5:] if emotion_history else '暂无'}

请按以下格式给出建议：
1. 状态评估（不超过30字）
2. 具体行动建议（不超过100字，必须包含具体时间和动作）

要求：
- 语言温暖、专业、符合考研场景
- 建议要具体可执行
- 如果检测到疲劳或压力大，要给出强制休息的建议
"""

        ai_suggestion = call_qwen_api(prompt)

        if ai_suggestion:
            return jsonify({
                'ok': True,
                'suggestion': ai_suggestion,
                'current_state': {
                    'emotion': emotion_name,
                    'fatigue_level': fatigue_level,
                    'focus_level': focus_level,
                    'stress_level': stress_level
                }
            })
        else:
            return jsonify({
                'ok': True,
                'suggestion': get_fallback_suggestion(fatigue_level, stress_level, session_duration),
                'current_state': {
                    'emotion': emotion_name,
                    'fatigue_level': fatigue_level,
                    'focus_level': focus_level,
                    'stress_level': stress_level
                }
            })
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'ok': False, 'error': str(e)}), 500


def get_fallback_suggestion(fatigue_level: int, stress_level: int, session_duration: int) -> str:
    """备用建议（当通义千问API不可用时）"""
    suggestions = []

    if fatigue_level >= 7:
        suggestions.append("⚠️ 检测到严重疲劳！请立即停止学习，休息10-15分钟")

    if session_duration > 90:
        suggestions.append(f"⏰ 已学习 {session_duration // 60} 分钟，建议休息5-10分钟")

    if stress_level >= 7:
        suggestions.append("🧘 压力较大，建议深呼吸或短暂散步")

    if fatigue_level < 5 and stress_level < 5:
        suggestions.append("✨ 状态良好，继续保持高效学习！")

    if not suggestions:
        suggestions.append("📚 劳逸结合，保持最佳学习状态")

    return "\n".join(suggestions)
