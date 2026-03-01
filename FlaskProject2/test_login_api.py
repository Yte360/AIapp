import requests
import json

# 测试登录API
def test_login():
    url = "http://localhost:5000/api/login"
    data = {
        "user_account": "test",
        "user_password": "123456"
    }
    
    try:
        response = requests.post(url, json=data)
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
        if response.status_code == 200:
            print("登录成功！")
        else:
            print("登录失败！")
    except Exception as e:
        print(f"测试失败: {e}")

if __name__ == "__main__":
    test_login()
