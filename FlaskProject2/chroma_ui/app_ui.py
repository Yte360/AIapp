import os

from flask import Flask, jsonify, send_from_directory, request

try:
    import chromadb
except Exception:
    chromadb = None


def _make_client():
    if chromadb is None:
        raise RuntimeError("缺少依赖: chromadb")

    host = os.getenv("CHROMA_HOST", "localhost")
    port = int(os.getenv("CHROMA_PORT", "8000"))
    tenant = os.getenv("CHROMA_TENANT", "default_tenant")
    database = os.getenv("CHROMA_DATABASE", "default_database")

    return chromadb.HttpClient(host=host, port=port, tenant=tenant, database=database)


app = Flask(__name__)


@app.get("/")
def ui_index():
    return send_from_directory(os.path.dirname(__file__), "chroma_browser.html")


@app.get("/api/chroma/collections")
def api_list_collections():
    try:
        client = _make_client()
        cols = client.list_collections()

        out = []
        for c in cols:
            try:
                col = client.get_collection(c.name)
                count = col.count()
                out.append({"name": c.name, "count": int(count)})
            except Exception as e:
                out.append({"name": c.name, "count": 0, "error": str(e)})

        out.sort(key=lambda x: x.get("name") or "")
        return jsonify(out)
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.post("/api/chroma/collections")
def api_create_collection():
    try:
        data = request.get_json(silent=True) or {}
        name = (data.get("name") or "").strip()
        metadata = data.get("metadata")
        if not name:
            return jsonify({"error": "缺少参数: name"}), 400

        client = _make_client()
        client.get_or_create_collection(name=name, metadata=metadata)
        return jsonify({"ok": True, "name": name})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.delete("/api/chroma/collections/<collection_name>")
def api_delete_collection(collection_name: str):
    try:
        client = _make_client()
        client.delete_collection(name=collection_name)
        return jsonify({"ok": True, "name": collection_name})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.get("/api/chroma/collections/<collection_name>/documents")
def api_get_documents(collection_name: str):
    try:
        client = _make_client()
        col = client.get_collection(collection_name)

        limit = int(request.args.get("limit", 20))
        offset = int(request.args.get("offset", 0))
        query = (request.args.get("query") or "").strip()
        user_id_filter = request.args.get("user_id")

        total = int(col.count())
        where_clause = None
        if user_id_filter:
            try:
                where_clause = {"user_id": int(user_id_filter)}
            except ValueError:
                where_clause = {"user_id": user_id_filter}

        # 浏览模式：按 offset/limit 读取
        if not query:
            res = col.get(
                limit=limit, 
                offset=offset, 
                where=where_clause,
                include=["documents", "metadatas"]
            )
            ids = res.get("ids") or []
            docs = res.get("documents") or []
            metas = res.get("metadatas") or []

            items = []
            for i in range(len(ids)):
                items.append({
                    "id": ids[i],
                    "content": docs[i] if i < len(docs) else "",
                    "metadata": metas[i] if i < len(metas) else {},
                })

            return jsonify({
                "collection": collection_name,
                "total": total,
                "limit": limit,
                "offset": offset,
                "user_id_filter": user_id_filter,
                "documents": items,
            })

        # 搜索模式：用向量检索（query_texts），返回 top-k
        qres = col.query(
            query_texts=[query],
            n_results=limit,
            where=where_clause,
            include=["documents", "metadatas", "distances"],
        )

        ids = (qres.get("ids") or [[]])[0]
        docs = (qres.get("documents") or [[]])[0]
        metas = (qres.get("metadatas") or [[]])[0]
        dists = (qres.get("distances") or [[]])[0]

        items = []
        for i in range(len(ids)):
            items.append({
                "id": ids[i],
                "content": docs[i] if i < len(docs) else "",
                "metadata": metas[i] if i < len(metas) else {},
                "distance": float(dists[i]) if i < len(dists) else None,
            })

        return jsonify({
            "collection": collection_name,
            "total": total,
            "limit": limit,
            "offset": offset,
            "query": query,
            "documents": items,
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.post("/api/chroma/collections/<collection_name>/documents")
def api_upsert_documents(collection_name: str):
    try:
        data = request.get_json(silent=True) or {}
        ids = data.get("ids")
        documents = data.get("documents")
        metadatas = data.get("metadatas")
        embeddings = data.get("embeddings")

        if not isinstance(ids, list) or not ids:
            return jsonify({"error": "ids 必须是非空数组"}), 400
        if not isinstance(documents, list) or len(documents) != len(ids):
            return jsonify({"error": "documents 必须是数组且长度与 ids 一致"}), 400
        if metadatas is not None and (not isinstance(metadatas, list) or len(metadatas) != len(ids)):
            return jsonify({"error": "metadatas 必须是数组且长度与 ids 一致"}), 400
        if embeddings is not None and (not isinstance(embeddings, list) or len(embeddings) != len(ids)):
            return jsonify({"error": "embeddings 必须是数组且长度与 ids 一致"}), 400

        client = _make_client()
        col = client.get_collection(collection_name)

        # upsert: 有则更新，无则新增
        col.upsert(ids=ids, documents=documents, metadatas=metadatas, embeddings=embeddings)
        return jsonify({"ok": True, "count": len(ids)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.delete("/api/chroma/collections/<collection_name>/documents")
def api_delete_documents(collection_name: str):
    try:
        data = request.get_json(silent=True) or {}
        ids = data.get("ids")
        if not isinstance(ids, list) or not ids:
            return jsonify({"error": "ids 必须是非空数组"}), 400

        client = _make_client()
        col = client.get_collection(collection_name)
        col.delete(ids=ids)
        return jsonify({"ok": True, "count": len(ids)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.getenv("CHROMA_UI_PORT", "5001"))
    debug = os.getenv("FLASK_DEBUG", "0") == "1"
    app.run(host="0.0.0.0", port=port, debug=debug)
