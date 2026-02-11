import json
import os
import time
import traceback

try:
    import requests
except ImportError:
    print("[ERROR] 请安装 requests 库: pip install requests")
    raise

from utils.text_clean import clean_text
from config.settings import ARK_API_KEY, ARK_API_URL



def ark_chat(messages: list) -> dict:
    """
    调用阿里云 DashScope 文本生成接口，使用 requests 库（更稳定的 SSL/TLS 处理）
    """
    payload = {
        "model": "qwen3-max",
        "input": {
            "messages": messages
        },
    }

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {ARK_API_KEY}",
    }

    # 重试机制：最多重试3次
    max_retries = 3
    retry_delay = 2  # 重试延迟（秒）
    
    for attempt in range(max_retries):
        try:
            # 使用 requests 库，它对 SSL/TLS 处理更好
            response = requests.post(
                ARK_API_URL,
                json=payload,
                headers=headers,
                timeout=60,
                verify=True  # 验证 SSL 证书
            )
            
            # 检查 HTTP 状态码
            response.raise_for_status()
            
            # 解析 JSON 响应
            obj = response.json()
            return obj
                
        except requests.exceptions.HTTPError as e:
            # HTTP 4xx/5xx 错误
            status_code = e.response.status_code if e.response else "未知"
            try:
                error_msg = e.response.json() if e.response else str(e)
            except:
                error_msg = e.response.text if e.response else str(e)
            
            # 429 Too Many Requests 可以重试
            if status_code == 429 and attempt < max_retries - 1:
                delay = retry_delay * (attempt + 1)
                print(f"[WARN] 请求过于频繁 (HTTP {status_code})，等待 {delay} 秒后重试...")
                time.sleep(delay)
                continue
            
            raise RuntimeError(f"HTTPError {status_code}: {error_msg}")
            
        except (requests.exceptions.SSLError, requests.exceptions.ConnectionError, 
                requests.exceptions.Timeout) as e:
            error_reason = str(e)
            if attempt < max_retries - 1:
                print(f"[WARN] 网络连接错误 (尝试 {attempt + 1}/{max_retries}): {type(e).__name__} - {error_reason}")
                print(f"[WARN] 等待 {retry_delay} 秒后重试...")
                time.sleep(retry_delay)
                continue
            else:
                raise RuntimeError(f"网络错误 (重试{max_retries}次后仍失败): {type(e).__name__} - {error_reason}")
        
        except requests.exceptions.RequestException as e:
            # 其他 requests 相关错误
            error_reason = str(e)
            if attempt < max_retries - 1:
                print(f"[WARN] 请求错误 (尝试 {attempt + 1}/{max_retries}): {error_reason}")
                print(f"[WARN] 等待 {retry_delay} 秒后重试...")
                time.sleep(retry_delay)
                continue
            else:
                raise RuntimeError(f"请求错误 (重试{max_retries}次后仍失败): {error_reason}")
        
        except Exception as e:
            # 其他未知错误
            if attempt < max_retries - 1:
                print(f"[WARN] 未知错误 (尝试 {attempt + 1}/{max_retries}): {type(e).__name__} - {e}")
                print(f"[WARN] 等待 {retry_delay} 秒后重试...")
                time.sleep(retry_delay)
                continue
            else:
                raise RuntimeError(f"未知错误: {type(e).__name__} - {str(e)}")
    
    # 理论上不会到达这里，但为了安全起见
    raise RuntimeError("请求失败：已达到最大重试次数")



def extract_content(obj: dict) -> str:
    try:
        print(f"[DEBUG] API返回对象keys: {list(obj.keys())}")

        if "output" in obj:
            output = obj["output"]
            if isinstance(output, dict) and "choices" in output and len(output["choices"]) > 0:
                choice = output["choices"][0]
                if "message" in choice and "content" in choice["message"]:
                    content = choice["message"]["content"]
                    print(f"[DEBUG] 从output.choices[0].message.content提取内容，长度: {len(content)}")
                    return clean_text(content) if content else ""
                elif "text" in choice:
                    content = choice["text"]
                    print(f"[DEBUG] 从output.choices[0].text提取内容，长度: {len(content)}")
                    return clean_text(content) if content else ""

        if "choices" in obj and len(obj["choices"]) > 0:
            choice = obj["choices"][0]
            if "message" in choice and "content" in choice["message"]:
                content = choice["message"]["content"]
                print(f"[DEBUG] 从choices[0].message.content提取内容，长度: {len(content)}")
                return clean_text(content) if content else ""
            elif "text" in choice:
                content = choice.get("text", "")
                print(f"[DEBUG] 从choices[0].text提取内容，长度: {len(content)}")
                return clean_text(content) if content else ""

        print(f"[DEBUG] 无法提取内容，返回完整对象")
        return json.dumps(obj, ensure_ascii=False)
    except Exception as e:
        print(f"[ERROR] extract_content异常: {e}")
        traceback.print_exc()
        return json.dumps(obj, ensure_ascii=False)
