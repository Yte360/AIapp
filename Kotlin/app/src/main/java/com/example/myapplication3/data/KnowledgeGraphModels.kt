package com.example.myapplication3.data

import org.json.JSONObject

data class KnowledgeGraph(
    val nodes: List<KgNode>,
    val edges: List<KgEdge>
)

data class KgNode(
    val id: String,
    val label: String
)

data class KgEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

object KnowledgeGraphParser {
    fun fromJson(obj: JSONObject): KnowledgeGraph {
        val nodesArr = obj.optJSONArray("nodes")
        val edgesArr = obj.optJSONArray("edges")

        val nodes = buildList {
            if (nodesArr != null) {
                for (i in 0 until nodesArr.length()) {
                    val n = nodesArr.optJSONObject(i) ?: continue
                    val id = n.optString("id", "")
                    val label = n.optString("label", id)
                    if (id.isNotEmpty()) add(KgNode(id = id, label = label))
                }
            }
        }

        val edges = buildList {
            if (edgesArr != null) {
                for (i in 0 until edgesArr.length()) {
                    val e = edgesArr.optJSONObject(i) ?: continue
                    val from = e.optString("from", "")
                    val to = e.optString("to", "")
                    val label = e.optString("label", "")
                    if (from.isNotEmpty() && to.isNotEmpty()) add(KgEdge(from = from, to = to, label = label))
                }
            }
        }

        return KnowledgeGraph(nodes = nodes, edges = edges)
    }
}
