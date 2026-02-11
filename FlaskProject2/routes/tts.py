import io
from flask import Blueprint, jsonify, request as flask_request, send_file
from services.tts_service import cosyvoice_api_tts
from config.settings import COSYVOICE_VOICE

bp = Blueprint("tts", __name__)

@bp.route("/api/tts", methods=["POST"])
def api_tts():
    """
    CosyVoice 官方 API 模式（tts_v2）：输入 text，返回音频二进制（mp3）。
    Request JSON:
      - text: str (required)
      - voice: str (optional)
    """
    data = flask_request.get_json(silent=True) or {}
    text = (data.get("text") or "").strip()
    print("[/api/tts] text =", repr(text))
    voice = (data.get("voice") or COSYVOICE_VOICE).strip()
    
    if not text:
        return jsonify({"ok": False, "error": "missing field: text"}), 400
        
    try:
        audio_bytes = cosyvoice_api_tts(text, voice=voice)
        buf = io.BytesIO(audio_bytes)
        buf.seek(0)
        return send_file(buf, mimetype="audio/mpeg", as_attachment=False)
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
