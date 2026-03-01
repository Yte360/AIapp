import pymysql

# 替换成你的实际配置（密码、数据库名）
CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "123456",  # 确保和登录MySQL的密码完全一致
    "database": "data_app",  # 必须是已创建的数据库（先手动CREATE DATABASE）
    "port": 3306,
    "charset": "utf8mb4"
}

try:
    # 第一步：不指定database，测试基础登录（仅验证账号密码）
    conn1 = pymysql.connect(
        host=CONFIG["host"],
        user=CONFIG["user"],
        password=CONFIG["password"],
        port=CONFIG["port"],
        charset=CONFIG["charset"]
    )
    print("✅ 基础登录成功（无数据库），账号密码/插件无问题")
    conn1.close()

    # 第二步：指定database，测试数据库访问权限
    conn2 = pymysql.connect(**CONFIG)
    print(f"✅ 数据库 {CONFIG['database']} 连接成功！")
    conn2.close()

except pymysql.err.OperationalError as e:
    err_code, err_msg = e.args
    print(f"❌ 连接失败：错误码 {err_code}，原因 {err_msg}")
    if err_code == 1045:
        print("→ 排查方向：1.数据库名不存在 2.root无该数据库权限 3.端口/host错误")
    elif err_code == 1049:
        print("→ 排查方向：数据库名错误或未创建，执行 CREATE DATABASE 你的数据库名;")