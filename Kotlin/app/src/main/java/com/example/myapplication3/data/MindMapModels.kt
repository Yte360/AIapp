package com.example.myapplication3.data

import org.json.JSONObject

data class MindMapNode(
    val topic: String,
    val children: List<MindMapNode> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject): MindMapNode {
            val topic = obj.optString("topic", obj.optString("title", ""))
            val arr = obj.optJSONArray("children")
            val kids = if (arr != null) {
                buildList {
                    for (i in 0 until arr.length()) {
                        val child = arr.optJSONObject(i) ?: continue
                        add(fromJson(child))
                    }
                }
            } else emptyList()
            return MindMapNode(topic = topic.ifEmpty { "节点" }, children = kids)
        }
    }
}
