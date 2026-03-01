# 测试数据库初始化
import sys
sys.path.append('FlaskProject2')

try:
    from core.database import DatabaseManager
    print("成功导入 DatabaseManager")
    
    try:
        db = DatabaseManager()
        print("成功创建 DatabaseManager 实例")
        
        # 测试验证用户方法
        user = db.verify_user('test', '123456')
        print(f"验证用户结果: {user}")
        
        if user:
            print("登录测试成功！")
        else:
            print("登录测试失败，用户不存在或密码错误")
        
        db._init_tables()  # 确保表结构已创建
        print("数据库表结构初始化成功")
        
    except Exception as e:
        print(f"创建 DatabaseManager 实例失败: {e}")
        import traceback
        traceback.print_exc()
        
except Exception as e:
    print(f"导入 DatabaseManager 失败: {e}")
    import traceback
    traceback.print_exc()
