"""
BPE (Byte Pair Encoding) 分词器 —— 从零手写实现
================================================
原理：统计文本中相邻 token 对的频率，贪心合并最高频对，迭代构建词表。

训练流程：
1. 准备语料 → 2. 预分词（按 Unicode 类别切分）→ 3. 统计迭代合并 → 4. 输出词表

核心设计：
- 基础词表: 字节 0x00-0xFF → ID 0-255
- 合并 token: ID 从 256 开始递增
- 所有操作在 ID 序列上进行，id_to_token 记录每个 ID 对应的字节序列

参考：CS336 Assignment 1, Sennrich et al. 2015
"""

import json
import os
import unicodedata
from collections import defaultdict
from typing import List, Dict, Tuple, Optional


class BPETokenizer:
    """
    BPE 分词器
    
    使用示例:
        tokenizer = BPETokenizer()
        tokenizer.train(["这是一段训练文本", "hello world"], vocab_size=1000)
        tokenizer.save("tokenizer.json")
        
        tokenizer = BPETokenizer.from_pretrained("tokenizer.json")
        ids = tokenizer.encode("你好世界")
        text = tokenizer.decode(ids)
    """
    
    def __init__(self):
        # id_to_token: token_id -> bytes（该 token 对应的原始字节）
        self.id_to_token: Dict[int, bytes] = {}
        # token_to_id: bytes -> token_id（反向映射）
        self.token_to_id: Dict[bytes, int] = {}
        # merges: 按顺序记录的合并规则列表 [(id_a, id_b), ...]
        self.merges: List[Tuple[int, int]] = []
        
    # ==================== 训练 ====================
    
    def train(
        self, 
        texts: List[str], 
        vocab_size: int = 5000,
        min_frequency: int = 2,
        show_progress: bool = True
    ):
        """
        训练 BPE 分词器
        
        参数:
            texts: 训练文本列表
            vocab_size: 目标词表大小
            min_frequency: 合并的最低频次阈值
            show_progress: 是否打印进度
        """
        print(f"[BPE] 训练开始 | 目标词表: {vocab_size} | 语料: {len(texts)} 篇")
        
        # Step 1: 预分词 → 将文本转为字节序列的列表
        word_seqs = self._pretokenize(texts)
        print(f"[BPE] 预分词完成，得到 {len(word_seqs)} 个片段")
        
        # Step 2: 初始化基础词表（0-255 对应 256 个字节）
        for byte_val in range(256):
            b = bytes([byte_val])
            self.id_to_token[byte_val] = b
            self.token_to_id[b] = byte_val
        
        base_vocab_used = set()
        for seq in word_seqs:
            for tid in seq:
                base_vocab_used.add(tid)
        print(f"[BPE] 基础词表: 256 个字节，实际用到 {len(base_vocab_used)} 个")
        
        # Step 3: 迭代合并
        num_merges = vocab_size - 256
        for step in range(num_merges):
            # 统计所有相邻 pair 的频次
            pair_counts = self._count_pairs_in_seqs(word_seqs)
            if not pair_counts:
                print(f"[BPE] 无可合并 pair，在第 {step} 步停止")
                break
            
            # 找最高频 pair
            best_pair = max(pair_counts, key=pair_counts.get)
            best_count = pair_counts[best_pair]
            
            if best_count < min_frequency:
                print(f"[BPE] 最高频次 {best_count} < 阈值 {min_frequency}，停止合并")
                break
            
            # 创建新 token
            new_id = len(self.id_to_token)
            new_bytes = self.id_to_token[best_pair[0]] + self.id_to_token[best_pair[1]]
            self.id_to_token[new_id] = new_bytes
            self.token_to_id[new_bytes] = new_id
            self.merges.append(best_pair)
            
            # 在所有序列中应用合并
            word_seqs = self._apply_merge_to_seqs(word_seqs, best_pair, new_id)
            
            if show_progress and (step + 1) % 100 == 0:
                a_bytes = self.id_to_token[best_pair[0]]
                b_bytes = self.id_to_token[best_pair[1]]
                print(f"  [BPE] {step + 1}/{num_merges} | "
                      f"合并 {a_bytes}+{b_bytes} | 频次={best_count} | 词表={len(self.id_to_token)}")
        
        print(f"[BPE] 训练完成！词表大小: {len(self.id_to_token)}，合并次数: {len(self.merges)}")
    
    def _pretokenize(self, texts: List[str]) -> List[List[int]]:
        """
        预分词：按 Unicode 类别切分文本，转为字节 ID 序列
        
        策略：
        - 空格：单独处理
        - 标点/符号：单独一个 token
        - 字母/数字/汉字：连续同类字符合并
        - 每个片段转为 UTF-8 字节 → 映射为 ID (0-255)
        """
        result = []
        for text in texts:
            if not text:
                continue
            
            i = 0
            while i < len(text):
                ch = text[i]
                
                # 空格：收集连续空格
                if ch.isspace():
                    j = i
                    while j < len(text) and text[j].isspace():
                        j += 1
                    result.append(self._str_to_ids(text[i:j]))
                    i = j
                    continue
                
                cat = unicodedata.category(ch)
                
                # 标点/符号：单独
                if cat.startswith("P") or cat.startswith("S"):
                    result.append(self._str_to_ids(ch))
                    i += 1
                    continue
                
                # 同类字符：连续合并
                j = i + 1
                while j < len(text):
                    next_cat = unicodedata.category(text[j])
                    if next_cat[0] == cat[0] and not text[j].isspace():
                        j += 1
                    else:
                        break
                
                result.append(self._str_to_ids(text[i:j]))
                i = j
        
        return result
    
    @staticmethod
    def _str_to_ids(s: str) -> List[int]:
        """将字符串转为字节 ID 列表 (0-255)"""
        return list(s.encode("utf-8"))
    
    def _count_pairs_in_seqs(self, seqs: List[List[int]]) -> Dict[Tuple[int, int], int]:
        """统计所有序列中相邻 token ID pair 的频次"""
        counts = defaultdict(int)
        for seq in seqs:
            for i in range(len(seq) - 1):
                counts[(seq[i], seq[i + 1])] += 1
        return dict(counts)
    
    def _apply_merge_to_seqs(
        self, 
        seqs: List[List[int]], 
        pair: Tuple[int, int], 
        new_id: int
    ) -> List[List[int]]:
        """在所有序列中应用一次合并：将 pair 替换为 new_id"""
        a, b = pair
        new_seqs = []
        for seq in seqs:
            new_seq = []
            i = 0
            while i < len(seq):
                if i < len(seq) - 1 and seq[i] == a and seq[i + 1] == b:
                    new_seq.append(new_id)
                    i += 2
                else:
                    new_seq.append(seq[i])
                    i += 1
            new_seqs.append(new_seq)
        return new_seqs
    
    # ==================== 编码 / 解码 ====================
    
    def encode(self, text: str) -> List[int]:
        """
        将文本编码为 token ID 序列
        
        流程: 文本 → UTF-8 字节 → 应用 BPE 合并规则 → token ID 列表
        """
        if not text:
            return []
        
        # 转为字节 ID 序列
        ids = self._str_to_ids(text)
        
        # 按顺序应用所有合并规则
        for pair in self.merges:
            a, b = pair
            new_ids = []
            i = 0
            while i < len(ids):
                if i < len(ids) - 1 and ids[i] == a and ids[i + 1] == b:
                    # 找到合并后 token 的 ID
                    merged_bytes = self.id_to_token[a] + self.id_to_token[b]
                    merged_id = self.token_to_id.get(merged_bytes)
                    if merged_id is not None:
                        new_ids.append(merged_id)
                    else:
                        # fallback: 保持原样
                        new_ids.append(a)
                        new_ids.append(b)
                    i += 2
                else:
                    new_ids.append(ids[i])
                    i += 1
            ids = new_ids
        
        return ids
    
    def decode(self, ids: List[int]) -> str:
        """将 token ID 序列解码为文本"""
        byte_list = bytearray()
        for tid in ids:
            if tid in self.id_to_token:
                byte_list.extend(self.id_to_token[tid])
            else:
                # 未知 ID，跳过
                pass
        return bytes(byte_list).decode("utf-8", errors="replace")
    
    # ==================== 保存 / 加载 ====================
    
    def save(self, path: str):
        """保存分词器到 JSON"""
        save_dir = os.path.dirname(path)
        if save_dir:
            os.makedirs(save_dir, exist_ok=True)
        
        data = {
            "id_to_token": {str(k): list(v) for k, v in self.id_to_token.items()},
            "merges": [list(p) for p in self.merges],
        }
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[BPE] 已保存到 {path}")
    
    @classmethod
    def from_pretrained(cls, path: str) -> "BPETokenizer":
        """从 JSON 加载分词器"""
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        
        tokenizer = cls()
        tokenizer.id_to_token = {int(k): bytes(v) for k, v in data["id_to_token"].items()}
        tokenizer.token_to_id = {v: k for k, v in tokenizer.id_to_token.items()}
        tokenizer.merges = [tuple(p) for p in data["merges"]]
        
        print(f"[BPE] 已加载 {path}，词表: {len(tokenizer.id_to_token)}")
        return tokenizer
    
    @property
    def vocab_size(self) -> int:
        return len(self.id_to_token)


# ==================== 测试 ====================

if __name__ == "__main__":
    corpus = [
        "大语言模型是人工智能的重要方向。",
        "深度学习改变了自然语言处理的方式。",
        "分词器将文本转换为模型可以理解的数字序列。",
        "BPE 算法通过统计字节对的频率来迭代合并。",
        "hello world, this is a test for BPE tokenizer.",
        "the quick brown fox jumps over the lazy dog.",
        "machine learning is transforming the world.",
        "natural language processing is a subfield of AI.",
        "大模型时代，分词器是基础组件之一。",
        "Transformer 架构彻底改变了 NLP 领域。",
        "Python 是深度学习最常用的编程语言。",
        "tokenizer converts text into numerical tokens.",
        "the cat sat on the mat and looked at the rat.",
        "人工智能正在改变我们的生活方式。",
        "data science and AI are the future of technology.",
    ]
    
    # 训练
    tokenizer = BPETokenizer()
    tokenizer.train(corpus, vocab_size=300)
    
    # 保存
    tokenizer.save("bpe_tokenizer/tokenizer.json")
    
    # 测试
    print("\n" + "=" * 60)
    print("编码/解码测试")
    print("=" * 60)
    
    test_texts = [
        "大语言模型",
        "hello world",
        "人工智能与深度学习",
        "BPE tokenizer",
        "Transformer 架构",
    ]
    
    for text in test_texts:
        ids = tokenizer.encode(text)
        decoded = tokenizer.decode(ids)
        ok = "OK" if text == decoded else "FAIL"
        print(f"  [{ok}] {text}")
        print(f"       IDs: {ids}")
        print(f"       解码: {decoded}")
        print()
    
    print(f"词表大小: {tokenizer.vocab_size}")
    orig = len("hello world".encode("utf-8"))
    encoded = len(tokenizer.encode("hello world"))
    print(f"压缩率: 'hello world' {orig} 字节 → {encoded} tokens")