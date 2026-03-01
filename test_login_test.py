import requests

# 测试新的登录API端点
def test_login_test():
    url = "http://localhost:5000/api/login-test"
    headers = {"Content-Type": "application/json"}
    data = {
        "user_account": "test",
        "user_password": "123456"
    }
    
    try:
        response = requests.post(url, json=data, headers=headers)
        print(f"状态码: {response.status_code}")
        print(f"响应内容: {response.json()}")
        
        if response.status_code == 200:
            result = response.json()
            if result.get("ok"):
                print("登录成功！")
                print(f"用户信息: {result.get('user')}")
            else:
                print(f"登录失败: {result.get('error')}")
        else:
            print(f"请求失败: {response.status_code}")
    except Exception as e:
        print(f"测试失败: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    print("测试新的登录API端点...")
    test_login_test()
