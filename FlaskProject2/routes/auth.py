from flask import Blueprint, jsonify, request as flask_request

from core.clients import db_manager


bp = Blueprint("auth", __name__)


@bp.route("/api/login", methods=["POST"])  # 登录接口
def api_login():
    try:
        data = flask_request.get_json(silent=True) or {}
        account = data.get("user_account")
        password = data.get("user_password")

        if not account or not password:
            return jsonify({"ok": False, "error": "请输入账号和密码"}), 400

        user = db_manager.verify_user(account, password)

        if user:
            return jsonify({
                "ok": True,
                "message": "登录成功",
                "user": {
                    "user_id": user["user_id"],
                    "user_account": user["user_account"],
                    "user_name": user["user_name"],
                },
            })

        return jsonify({"ok": False, "error": "账号或密码错误"}), 401

    except Exception as ex:
        import traceback

        traceback.print_exc()
        return jsonify({"ok": False, "error": str(ex)}), 500
