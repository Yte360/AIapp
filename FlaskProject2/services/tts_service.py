import io
from typing import Optional
from config.settings import COSYVOICE_API_KEY, COSYVOICE_MODEL, COSYVOICE_VOICE

def cosyvoice_api_tts(text: str, voice: Optional[str] = None) -> bytes:    
    """
    调用官方 CosyVoice WebSocket API（tts_v2），返回二进制音频（mp3）。
    """
    if not COSYVOICE_API_KEY:
        raise RuntimeError("未配置 COSYVOICE_API_KEY")

    try:
        import dashscope
        from dashscope.audio.tts_v2 import SpeechSynthesizer
    except Exception as e:
        raise RuntimeError(f"未安装 dashscope 库，请执行 pip install dashscope。错误: {e}")

    dashscope.api_key = COSYVOICE_API_KEY

    use_voice = (voice or COSYVOICE_VOICE).strip()
    synthesizer = SpeechSynthesizer(model=COSYVOICE_MODEL, voice=use_voice)

    # 发送待合成文本，获取二进制音频
    audio = synthesizer.call(text)

    if not audio:
        raise RuntimeError("CosyVoice API 返回空音频")

    try:
        req_id = synthesizer.get_last_request_id()
        first_delay = synthesizer.get_first_package_delay()
        print(f"[CosyVoice] requestId={req_id}, first_packet_delay={first_delay}ms")
    except Exception:
        pass

    return audio
