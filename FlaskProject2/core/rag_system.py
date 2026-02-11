import os
import traceback
from datetime import datetime
from typing import List, Dict, Tuple, Optional

import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

try:
    from pypdf import PdfReader
except ImportError:
    PdfReader = None

try:
    import chromadb
except ImportError:
    chromadb = None

import tiktoken
try:
    from markitdown import MarkItDown
except ImportError:
    try:
        from markitdown._markitdown import MarkItDown
    except ImportError:
        MarkItDown = None
        print("[ERROR] 无法从 markitdown 导入 MarkItDown 类")

from config.settings import (
    CHROMA_HOST, CHROMA_PORT, CHROMA_COLLECTION, 
    CHROMA_TENANT, CHROMA_DATABASE
)

# 初始化分词器（用于精确计算 Token）
try:
    _tokenizer = tiktoken.get_encoding("cl100k_base")
except Exception:
    _tokenizer = None

def _approx_token_len(text: str) -> int:
    """计算 Token 数量"""
    if not text or not _tokenizer:
        return len(text) // 2
    return len(_tokenizer.encode(text))

class DocumentProcessor:
    """文档处理器，支持多种格式的文档解析（已迁移至 MarkItDown + 智能切块）"""

    def __init__(self):
        self.supported_formats = [
            '.txt', '.pdf', '.docx', '.xlsx', '.md',
            '.pptx', '.csv', '.html', '.htm', '.xml', '.json', '.rtf', '.epub',
            '.jpg', '.png', '.gif', '.mp3', '.wav', '.m4a'
        ]
        if MarkItDown is None:
            raise RuntimeError("MarkItDown 不可用，请检查库版本")
        self.md_instance = MarkItDown()

    def _enhanced_pdf_processing(self, path: str) -> str:
        """PDF 增强处理：优先快速抽取可复制文本，失败再回退到 MarkItDown."""
        start_time = datetime.now()
        if PdfReader is not None:
            try:
                reader = PdfReader(path)
                texts = []
                for page in reader.pages:
                    t = page.extract_text() or ""
                    if t: texts.append(t)
                merged = "\n\n".join(texts).strip()
                if merged:
                    print(f"[RAG] PDF 快速提取成功, 长度: {len(merged)}")
                    return merged
            except Exception as e:
                print(f"[RAG] PDF 快速提取失败: {e}")

        try:
            result = self.md_instance.convert(path)
            return getattr(result, "text_content", "")
        except Exception as e:
            print(f"[WARNING] PDF MarkItDown 转换失败 {path}: {e}")
            return ""

    def _convert_to_markdown(self, path: str) -> str:
        """统一文档读取：转换为 Markdown 文本"""
        if not os.path.exists(path): return ""
        ext = os.path.splitext(path)[1].lower()
        if ext == '.pdf': return self._enhanced_pdf_processing(path)
        try:
            result = self.md_instance.convert(path)
            return getattr(result, "text_content", "")
        except Exception:
            return ""

    def extract_text_from_file(self, file_path: str) -> str:
        return self._convert_to_markdown(file_path)

    def _split_paragraphs_with_headings(self, text: str) -> List[Dict]:
        """根据标题层次和空行分割段落"""
        lines = text.splitlines()
        heading_stack: List[str] = []
        paragraphs: List[Dict] = []
        buf: List[str] = []
        char_pos = 0
        
        def flush_buf(end_pos: int):
            if not buf: return
            content = "\n".join(buf).strip()
            if not content: return
            paragraphs.append({
                "content": content,
                "heading_path": " > ".join(heading_stack) if heading_stack else None,
                "start": max(0, end_pos - len(content)),
                "end": end_pos,
            })
        
        for ln in lines:
            if ln.strip().startswith("#"):
                flush_buf(char_pos)
                buf = []
                level = len(ln) - len(ln.lstrip('#'))
                title = ln.lstrip('#').strip()
                if level <= 0: level = 1
                if level <= len(heading_stack):
                    heading_stack = heading_stack[:level-1]
                heading_stack.append(title)
            elif ln.strip() == "":
                flush_buf(char_pos)
                buf = []
            else:
                buf.append(ln)
            char_pos += len(ln) + 1
        
        flush_buf(char_pos)
        return paragraphs or [{"content": text, "heading_path": None, "start": 0, "end": len(text)}]

    def chunk_text(self, text: str, chunk_tokens: int = 500, overlap_tokens: int = 50) -> List[Dict]:
        """基于 Token 数量的智能分块"""
        paragraphs = self._split_paragraphs_with_headings(text)
        chunks: List[Dict] = []
        cur: List[Dict] = []
        cur_tokens = 0
        i = 0
        
        while i < len(paragraphs):
            p = paragraphs[i]
            p_tokens = _approx_token_len(p["content"]) or 1
            if cur_tokens + p_tokens <= chunk_tokens or not cur:
                cur.append(p)
                cur_tokens += p_tokens
                i += 1
            else:
                content = "\n\n".join(x["content"] for x in cur)
                heading_path = next((x["heading_path"] for x in reversed(cur) if x.get("heading_path")), None)
                chunks.append({
                    "content": content,
                    "heading_path": heading_path,
                    "start": cur[0]["start"],
                    "end": cur[-1]["end"]
                })
                if overlap_tokens > 0 and cur:
                    kept: List[Dict] = []
                    kept_tokens = 0
                    for x in reversed(cur):
                        t = _approx_token_len(x["content"]) or 1
                        if kept_tokens + t > overlap_tokens: break
                        kept.append(x)
                        kept_tokens += t
                    cur = list(reversed(kept))
                    cur_tokens = kept_tokens
                else:
                    cur, cur_tokens = [], 0
        
        if cur:
            content = "\n\n".join(x["content"] for x in cur)
            heading_path = next((x["heading_path"] for x in reversed(cur) if x.get("heading_path")), None)
            chunks.append({"content": content, "heading_path": heading_path, "start": cur[0]["start"], "end": cur[-1]["end"]})
        return chunks

class VectorStore:
    """向量存储和检索系统（ChromaDB 本地持久化模式）"""

    def __init__(self, model_name: str = None):
        if model_name is None:
            model_name = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models")
            
        print(f"[RAG] 正在加载本地向量模型: {model_name}")
        self.model = SentenceTransformer(model_name)
        self.dimension = 768

        self._tfidf_vectorizer: Optional[TfidfVectorizer] = None
        self._tfidf_matrix = None
        self._tfidf_docs: List[str] = []
        self._tfidf_metas: List[Dict] = []

        if chromadb is None: raise RuntimeError("缺少依赖: chromadb")

        # 使用 config/settings.py 中的统一配置
        try:
            self.client = chromadb.HttpClient(
                host=CHROMA_HOST, 
                port=CHROMA_PORT, 
                tenant=CHROMA_TENANT, 
                database=CHROMA_DATABASE
            )
            self.collection = self.client.get_or_create_collection(name=CHROMA_COLLECTION)
        except Exception as e:
            print(f"[WARN] 连接 Chroma 失败: {repr(e)}")
            self.client = self.collection = None

    def add_documents(self, documents: List[str], metadata: List[Dict] = None):
        """添加文档到向量存储，并同步更新 TF-IDF 索引"""
        if self.collection is None: raise RuntimeError("Chroma 未连接")
        if metadata is None: metadata = [{} for _ in documents]

        # 1. 向量化并存入 Chroma
        embeddings = self.model.encode(documents).tolist()
        base = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        ids = [f"{base}_{i}" for i in range(len(documents))]

        self.collection.add(ids=ids, documents=documents, metadatas=metadata, embeddings=embeddings)

#         关键对应关系（必须牢记）：
# 索引位置  self.index 中向量   self.documents  self.metadata
# 0 向量 embeddings[0]  "猫喜欢吃鱼"    meta for file1
# 1 向量 embeddings[1]  "狗喜欢散步"    meta for file2
# 2 向量 embeddings[2]  "鸟会唱歌"  meta for file3
# 要点
# 任何时候索引第 i 个向量必须对应 self.documents[i] 与 self.metadata[i]，否则检索结果会错位。
# extend 而非 append 是为了支持批量添加并保持顺序。

        # 2. 更新 TF-IDF 语料库
        self._tfidf_docs.extend(documents)
        self._tfidf_metas.extend(metadata)
        self._update_tfidf_index()

    def _update_tfidf_index(self):
        """重新构建 TF-IDF 索引矩阵"""
        if not self._tfidf_docs: return
        try:
            self._tfidf_vectorizer = TfidfVectorizer(token_pattern=r"(?u)\b\w+\b", stop_words=None)
            self._tfidf_matrix = self._tfidf_vectorizer.fit_transform(self._tfidf_docs)
        except Exception as e:
            print(f"[WARN] TF-IDF 索引更新失败: {e}")

    def tfidf_search(self, query: str, user_id: int, top_k: int = 5) -> List[Tuple[str, float, Dict]]:
        """执行关键词检索（增加 user_id 过滤）"""
        if self._tfidf_matrix is None or not self._tfidf_docs: return []
        try:
            query_vec = self._tfidf_vectorizer.transform([query])
            cosine_similarities = cosine_similarity(query_vec, self._tfidf_matrix).flatten()
            related_docs_indices = cosine_similarities.argsort()[-top_k:][::-1]
            
            results = []
            for i in related_docs_indices:
                if len(results) >= top_k: break
                score = float(cosine_similarities[i])
                meta = self._tfidf_metas[i]
                if score > 0 and meta.get("user_id") == user_id:
                    results.append((self._tfidf_docs[i], score, meta))
            return results
        except Exception as e:
            print(f"[WARN] TF-IDF 检索异常: {e}")
            return []
    
    def search(self, query: str, user_id: int, top_k: int = 5) -> List[Tuple[str, float, Dict]]:
        """搜索相关文档（增加 user_id 过滤）"""
        if self.collection is None: return []
        query_embedding = self.model.encode([query]).tolist()
        res = self.collection.query(
            query_embeddings=query_embedding,
            n_results=top_k,
            where={"user_id": user_id},
            include=["documents", "metadatas", "distances"],
        )
        docs = (res.get("documents") or [[]])[0]
        metas = (res.get("metadatas") or [[]])[0]
        dists = (res.get("distances") or [[]])[0]
        results: List[Tuple[str, float, Dict]] = []
        for doc, meta, dist in zip(docs, metas, dists):
            results.append((doc, float(-dist), meta or {}))
        return results

class RAGSystem:
    """RAG系统主类"""

    def __init__(self, knowledge_base_path: str = "knowledge_base"):
        self.knowledge_base_path = knowledge_base_path
        self.doc_processor = DocumentProcessor()
        self.vector_store = VectorStore()
        os.makedirs(knowledge_base_path, exist_ok=True)

    def add_document(self, user_id: int, file_path: str, title: str = None, category: str = "general") -> bool:
        """添加文档到知识库 (已迁移至新 chunk structure)"""
        try:
            text = self.doc_processor.extract_text_from_file(file_path)
            chunk_dicts = self.doc_processor.chunk_text(text)
            
#           第三部分：向量存储 和检索系统
#             为每个 chunk 构造 metadata（元数据），标志着每一条chunk段落信息，起到一个目录作用（标志chunk段落在知识库中的精准位置）
#             通俗说明
#             目的：检索时能告诉你这段文本来自哪个文件、是第几块、属于哪个分类等。
#             举例：对 文档1 的第 0 块，元数据可能是：
#             {'file_path':'文档1','title':'文档1标题','category':'文档1格式','chunk_index':需要检索的文段在文档1之中的位置（第几个chunk段）,'total_chunks':文档1的chunk总数,'added_time':'这条元数据的生成时间'}。
#             #准备元数据
            documents, metadata = [], []
            for i, c_dict in enumerate(chunk_dicts):
                documents.append(c_dict["content"])
                metadata.append({
                    'user_id': user_id,
                    'file_path': file_path,
                    'title': title or os.path.basename(file_path),
                    'category': category,
                    'chunk_index': i,
                    'total_chunks': len(chunk_dicts),
                    'heading_path': c_dict.get("heading_path") or "正文",
                    'start_pos': c_dict.get("start", 0),
                    'end_pos': c_dict.get("end", 0),
                    'added_time': datetime.now().isoformat()
                })
            if documents: self.vector_store.add_documents(documents, metadata)
            return True
        except Exception as e:
            traceback.print_exc()
            return False

    def add_text(self, user_id: int, text: str, title: str, category: str = "general") -> bool:
        """直接添加文本到知识库 (已迁移至新 chunk structure)"""
        try:
            chunk_dicts = self.doc_processor.chunk_text(text)
            documents, metadata = [], []
            for i, c_dict in enumerate(chunk_dicts):
                documents.append(c_dict["content"])
                metadata.append({
                    'user_id': user_id,
                    'title': title,
                    'category': category,
                    'chunk_index': i,
                    'total_chunks': len(chunk_dicts),
                    'heading_path': c_dict.get("heading_path") or "正文",
                    'added_time': datetime.now().isoformat()
                })
            if documents: self.vector_store.add_documents(documents, metadata)
            return True
        except Exception:
            return False

    def search(self, user_id: int, query: str, top_k: int = 5) -> List[Dict]:
        """搜索相关知识（向量检索 + TF-IDF 关键词检索融合，按 user_id 隔离）"""
        vector_results = self.vector_store.search(query, user_id, top_k)
        keyword_results = self.vector_store.tfidf_search(query, user_id, top_k)
        merged: List[Tuple[str, float, Dict]] = []
        merged.extend(vector_results)
        merged.extend([(d, float(s) * 0.9, m) for (d, s, m) in keyword_results])

        best_by_content: Dict[str, Tuple[str, float, Dict]] = {}
        for doc, score, meta in merged:
            key = (doc or "").strip()
            if not key: continue
            prev = best_by_content.get(key)
            if prev is None or score > prev[1]:
                best_by_content[key] = (doc, score, meta or {})

        merged_unique = sorted(best_by_content.values(), key=lambda x: x[1], reverse=True)[:top_k]
        formatted_results = []
        for doc, score, metadata in merged_unique:
            formatted_results.append({
                'content': doc, 'score': float(score),
                'title': metadata.get('title', '未知'), 'category': metadata.get('category', 'general'),
                'heading_path': metadata.get('heading_path', '正文'),
                'chunk_index': metadata.get('chunk_index', 0), 'added_time': metadata.get('added_time', '')
            })
        return formatted_results

    def get_context_for_query(self, user_id: int, query: str, max_context_length: int = 2000) -> str:
        """为查询获取上下文信息 (增强版：包含章节信息，按 user_id 隔离)"""
        results = self.search(user_id, query, top_k=5)

#         第七部分：获取上下文信息
#         输入：query（用户问题），max_context_length（最大上下文长度）。
#         输出：context（上下文信息）。
#         流程：
#         1. 用 self.search 找到 top_k 个最相关的文档。
#         2. 把文档内容拼接成一个字符串，限制长度不超过 max_context_length。
#         3. 返回拼接后的字符串。
        if not results: return ""
        context_parts, current_length = [], 0
        for result in results:
            formatted_chunk = f"【来源：{result['title']} > {result['heading_path']}】\n{result['content']}"
            if current_length + len(formatted_chunk) > max_context_length: break
            context_parts.append(formatted_chunk)
            current_length += len(formatted_chunk)
        return "\n\n".join(context_parts)

    def get_knowledge_stats(self, user_id: int) -> Dict:
        """获取知识库统计信息（按 user_id 过滤）"""
        try:
            res = self.vector_store.collection.get(where={"user_id": user_id}, include=[])
            count = len(res['ids']) if res and 'ids' in res else 0
        except Exception:
            count = 0
        return {'total_documents': 0, 'total_chunks': int(count), 'categories': []}
