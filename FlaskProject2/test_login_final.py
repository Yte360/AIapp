import requests

r = requests.post('http://127.0.0.1:5000/api/login', json={'user_account': 'test', 'user_password': '123456'})
print(f"Status: {r.status_code}")
print(f"Response: {r.text}")
