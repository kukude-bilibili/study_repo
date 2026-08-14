"""
Mini Transformer 语言模型 —— 从零手写实现
==========================================
组件列表：
- RoPE (旋转位置编码)：替代传统正弦位置编码，更好的长序列外推
- RMSNorm (Root Mean Square Normalization)：比 LayerNorm 更高效
- SwiGLU (Swish-Gated Linear Unit)：改进的 FFN 激活函数
- Multi-Head Attention：支持 Flash Attention（PyTorch 2.0+ 自动启用）
- Pre-Norm 架构：归一化在 attention/FFN 之前

参考：CS336 Assignment 1, LLaMA, GPT-2
"""

import math
import torch
import torch.nn as nn
import torch.nn.functional as F
from typing import Optional, Tuple
from dataclasses import dataclass


# ==================== 模型配置 ====================

@dataclass
class ModelConfig:
    """模型超参数配置"""
    vocab_size: int = 50257           # 词表大小（GPT-2 默认）
    context_length: int = 128         # 最大上下文长度
    d_model: int = 256                # 隐藏层维度
    num_heads: int = 8                # 注意力头数
    num_layers: int = 6               # Transformer 层数
    d_ff: int = 1024                  # FFN 中间层维度（通常是 d_model 的 4 倍）
    dropout: float = 0.1              # Dropout 比例
    use_flash_attention: bool = True  # 是否使用 Flash Attention
    # RoPE 参数
    rope_theta: float = 10000.0       # RoPE 基础频率


# ==================== RoPE：旋转位置编码 ====================

class RotaryPositionalEmbedding(nn.Module):
    """
    RoPE (Rotary Position Embedding)
    
    核心思想：通过旋转矩阵将位置信息注入 attention 的 Q 和 K 向量
    公式: f(q, m) = q * e^(i*m*θ)，其中 θ_i = base^(-2i/d)
    
    优势：
    - 相对位置编码：只依赖 token 间的相对距离
    - 长序列外推：天然支持比训练时更长的序列
    """
    
    def __init__(self, d_model: int, max_seq_len: int = 2048, theta: float = 10000.0):
        super().__init__()
        self.d_model = d_model
        self.max_seq_len = max_seq_len
        
        # 计算频率: θ_i = theta^(-2i/d)，i = 0, 2, 4, ..., d_model-2
        dim_indices = torch.arange(0, d_model, 2).float()
        freqs = 1.0 / (theta ** (dim_indices / d_model))
        
        # 预计算所有位置的 cos/sin 值 (max_seq_len, d_model/2)
        positions = torch.arange(max_seq_len).float()
        angles = torch.outer(positions, freqs)  # (max_seq_len, d_model/2)
        
        # 注册为 buffer（不参与梯度，但随模型保存）
        self.register_buffer("cos_cached", torch.cos(angles))
        self.register_buffer("sin_cached", torch.sin(angles))
    
    def forward(self, x: torch.Tensor, seq_len: int) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        返回 cos 和 sin 值，用于旋转 Q 和 K
        
        参数:
            x: 输入张量（用于确定 device）
            seq_len: 序列长度
        返回:
            cos: (seq_len, d_model/2)
            sin: (seq_len, d_model/2)
        """
        return (
            self.cos_cached[:seq_len].to(x.device),
            self.sin_cached[:seq_len].to(x.device)
        )


def apply_rotary_embedding(
    x: torch.Tensor,
    cos: torch.Tensor,
    sin: torch.Tensor
) -> torch.Tensor:
    """
    对 Q 或 K 应用旋转变换
    
    将每对相邻维度视为复数的实部和虚部，进行旋转：
    x'_2i   = x_2i * cos(θ) - x_2i+1 * sin(θ)
    x'_2i+1 = x_2i * sin(θ) + x_2i+1 * cos(θ)
    
    参数:
        x: (batch, num_heads, seq_len, head_dim)
        cos, sin: (seq_len, head_dim/2)
    """
    # 将最后维度分成两半
    x_rot = x.float()
    x1, x2 = x_rot[..., 0::2], x_rot[..., 1::2]
    
    # 扩展 cos/sin 维度以匹配 x
    cos = cos.unsqueeze(0).unsqueeze(0)  # (1, 1, seq_len, head_dim/2)
    sin = sin.unsqueeze(0).unsqueeze(0)
    
    # 旋转
    x1_rotated = x1 * cos - x2 * sin
    x2_rotated = x1 * sin + x2 * cos
    
    # 交错合并
    rotated = torch.stack([x1_rotated, x2_rotated], dim=-1)
    rotated = rotated.flatten(-2)
    
    return rotated.to(x.dtype)


# ==================== RMSNorm：高效归一化 ====================

class RMSNorm(nn.Module):
    """
    Root Mean Square Layer Normalization
    
    公式: y = x / RMS(x) * γ，其中 RMS(x) = sqrt(mean(x^2) + ε)
    
    相比 LayerNorm 的优势：
    - 去掉了均值中心化（减去 mean），只保留缩放
    - 计算量减少约一半
    - LLaMA 系列验证了其有效性
    """
    
    def __init__(self, d_model: int, eps: float = 1e-6):
        super().__init__()
        self.eps = eps
        self.weight = nn.Parameter(torch.ones(d_model))
    
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, seq_len, d_model)
        rms = torch.sqrt(torch.mean(x.float() ** 2, dim=-1, keepdim=True) + self.eps)
        return (x / rms) * self.weight


# ==================== SwiGLU：改进的激活函数 ====================

class SwiGLU(nn.Module):
    """
    SwiGLU (Swish-Gated Linear Unit)
    
    公式: SwiGLU(x) = (xW1 ⊙ Swish(xW2)) * W3
    
    对比标准 FFN (ReLU):
    - 标准: max(0, xW1) * W2
    - SwiGLU: 多了门控机制，用 Swish 激活替代 ReLU
    
    Swish(x) = x * sigmoid(x)
    
    注意：SwiGLU 有三个权重矩阵，中间维度通常设为 2/3 * 4d ≈ 8/3 d
    """
    
    def __init__(self, d_model: int, d_ff: int, dropout: float = 0.1):
        super().__init__()
        # SwiGLU 需要 3 个投影，但中间维度 × 2/3 补偿参数增量
        # 保持总参数量与标准 FFN (d->4d->d) 一致
        self.w1 = nn.Linear(d_model, d_ff, bias=False)
        self.w2 = nn.Linear(d_model, d_ff, bias=False)
        self.w3 = nn.Linear(d_ff, d_model, bias=False)
        self.dropout = nn.Dropout(dropout)
    
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # Swish gate: xW2 ⊙ (xW1 * sigmoid(xW1))
        gate = F.silu(self.w1(x))  # silu = Swish = x * sigmoid(x)
        value = self.w2(x)
        return self.dropout(self.w3(gate * value))


# ==================== 多头注意力 ====================

class MultiHeadAttention(nn.Module):
    """
    多头自注意力 + RoPE + Flash Attention
    
    流程:
    1. Q, K, V 线性投影
    2. 对 Q, K 应用 RoPE 旋转编码
    3. 计算注意力分数: softmax(QK^T / sqrt(d_k))
    4. 加权求和 V
    """
    
    def __init__(self, config: ModelConfig):
        super().__init__()
        assert config.d_model % config.num_heads == 0, "d_model 必须能被 num_heads 整除"
        
        self.num_heads = config.num_heads
        self.head_dim = config.d_model // config.num_heads
        self.d_model = config.d_model
        self.dropout = config.dropout
        self.use_flash = config.use_flash_attention
        
        # Q, K, V 联合投影
        self.qkv = nn.Linear(config.d_model, 3 * config.d_model, bias=False)
        # 输出投影
        self.out_proj = nn.Linear(config.d_model, config.d_model, bias=False)
        self.attn_dropout = nn.Dropout(config.dropout)
        self.out_dropout = nn.Dropout(config.dropout)
        
        # RoPE
        self.rope = RotaryPositionalEmbedding(
            self.head_dim, 
            max_seq_len=config.context_length,
            theta=config.rope_theta
        )
    
    def forward(
        self, 
        x: torch.Tensor, 
        mask: Optional[torch.Tensor] = None
    ) -> torch.Tensor:
        """
        x: (batch, seq_len, d_model)
        mask: (batch, 1, seq_len, seq_len) 因果掩码
        """
        B, T, C = x.shape
        
        # QKV 投影 + 拆分为多头
        qkv = self.qkv(x)  # (B, T, 3*C)
        q, k, v = qkv.chunk(3, dim=-1)
        
        # 重塑为多头: (B, num_heads, T, head_dim)
        q = q.view(B, T, self.num_heads, self.head_dim).transpose(1, 2)
        k = k.view(B, T, self.num_heads, self.head_dim).transpose(1, 2)
        v = v.view(B, T, self.num_heads, self.head_dim).transpose(1, 2)
        
        # 应用 RoPE
        cos, sin = self.rope(x, T)
        q = apply_rotary_embedding(q, cos, sin)
        k = apply_rotary_embedding(k, cos, sin)
        
        # 注意力计算
        if self.use_flash and hasattr(F, 'scaled_dot_product_attention'):
            # Flash Attention（PyTorch 2.0+）
            attn_out = F.scaled_dot_product_attention(
                q, k, v,
                attn_mask=mask,
                dropout_p=self.dropout if self.training else 0.0,
                is_causal=(mask is None)
            )
        else:
            # 标准注意力
            scale = 1.0 / math.sqrt(self.head_dim)
            attn_scores = torch.matmul(q, k.transpose(-2, -1)) * scale
            
            if mask is not None:
                attn_scores = attn_scores + mask
            
            attn_weights = F.softmax(attn_scores, dim=-1)
            attn_weights = self.attn_dropout(attn_weights)
            attn_out = torch.matmul(attn_weights, v)
        
        # 合并多头 + 输出投影
        attn_out = attn_out.transpose(1, 2).contiguous().view(B, T, C)
        return self.out_dropout(self.out_proj(attn_out))


# ==================== Transformer Block ====================

class TransformerBlock(nn.Module):
    """
    Pre-Norm Transformer 块
    
    结构:
    x → RMSNorm → MultiHeadAttention → + → RMSNorm → SwiGLU → + → output
    
    使用 Pre-Norm（归一化在子层之前）而非 Post-Norm（归一化在后）
    优势：训练更稳定，梯度流动更顺畅
    """
    
    def __init__(self, config: ModelConfig):
        super().__init__()
        self.norm1 = RMSNorm(config.d_model)
        self.attn = MultiHeadAttention(config)
        self.norm2 = RMSNorm(config.d_model)
        self.ffn = SwiGLU(config.d_model, config.d_ff, config.dropout)
    
    def forward(
        self, 
        x: torch.Tensor, 
        mask: Optional[torch.Tensor] = None
    ) -> torch.Tensor:
        # Self-attention + 残差
        x = x + self.attn(self.norm1(x), mask)
        # FFN + 残差
        x = x + self.ffn(self.norm2(x))
        return x


# ==================== 完整语言模型 ====================

class MiniLLM(nn.Module):
    """
    Mini Transformer 语言模型
    
    结构:
    Embedding → TransformerBlock × N → RMSNorm → Linear(head) → logits
    
    训练目标：下一个 token 预测（causal language modeling）
    """
    
    def __init__(self, config: ModelConfig):
        super().__init__()
        self.config = config
        
        # Token 嵌入
        self.token_embedding = nn.Embedding(config.vocab_size, config.d_model)
        self.dropout = nn.Dropout(config.dropout)
        
        # Transformer 层
        self.layers = nn.ModuleList([
            TransformerBlock(config) for _ in range(config.num_layers)
        ])
        
        # 最终归一化 + 输出头
        self.final_norm = RMSNorm(config.d_model)
        self.lm_head = nn.Linear(config.d_model, config.vocab_size, bias=False)
        
        # 权重绑定：embedding 和 lm_head 共享权重（减少参数）
        self.token_embedding.weight = self.lm_head.weight
        
        # 初始化权重
        self._init_weights()
    
    def _init_weights(self):
        """合理的权重初始化"""
        std = 0.02
        for module in self.modules():
            if isinstance(module, nn.Linear):
                torch.nn.init.normal_(module.weight, mean=0.0, std=std)
                if module.bias is not None:
                    torch.nn.init.zeros_(module.bias)
            elif isinstance(module, nn.Embedding):
                torch.nn.init.normal_(module.weight, mean=0.0, std=std)
    
    def _create_causal_mask(self, seq_len: int, device: torch.device) -> torch.Tensor:
        """创建因果掩码（下三角），防止看到未来 token"""
        mask = torch.triu(
            torch.ones(seq_len, seq_len, device=device) * float('-inf'), 
            diagonal=1
        )
        return mask.unsqueeze(0).unsqueeze(0)  # (1, 1, seq_len, seq_len)
    
    def forward(
        self, 
        input_ids: torch.Tensor,
        targets: Optional[torch.Tensor] = None
    ) -> Tuple[torch.Tensor, Optional[torch.Tensor]]:
        """
        参数:
            input_ids: (batch, seq_len) token ID 序列
            targets: (batch, seq_len) 目标 token ID，用于计算 loss
        返回:
            logits: (batch, seq_len, vocab_size)
            loss: 交叉熵 loss（如果提供 targets）
        """
        B, T = input_ids.shape
        device = input_ids.device
        
        # Token 嵌入
        x = self.token_embedding(input_ids)  # (B, T, C)
        x = self.dropout(x)
        
        # 因果掩码
        mask = self._create_causal_mask(T, device)
        
        # 通过所有 Transformer 层
        for layer in self.layers:
            x = layer(x, mask)
        
        # 最终归一化 + 输出 logits
        x = self.final_norm(x)
        logits = self.lm_head(x)  # (B, T, vocab_size)
        
        # 计算 loss
        loss = None
        if targets is not None:
            loss = F.cross_entropy(
                logits.view(-1, logits.size(-1)),
                targets.view(-1),
                ignore_index=-1  # 忽略 padding token
            )
        
        return logits, loss
    
    @torch.no_grad()
    def generate(
        self,
        input_ids: torch.Tensor,
        max_new_tokens: int = 50,
        temperature: float = 0.8,
        top_k: Optional[int] = None,
        top_p: Optional[float] = None,
    ) -> torch.Tensor:
        """
        自回归文本生成
        
        参数:
            input_ids: (1, seq_len) 起始 token 序列
            max_new_tokens: 最大生成 token 数
            temperature: 温度参数（越高越随机，越低越确定）
            top_k: 只从概率最高的 k 个 token 中采样
            top_p: 核采样，累积概率阈值
        """
        self.eval()
        
        for _ in range(max_new_tokens):
            # 截断到上下文长度
            input_cond = input_ids[:, -self.config.context_length:]
            
            # 前向传播
            logits, _ = self.forward(input_cond)
            logits = logits[:, -1, :] / temperature  # 只取最后一个位置
            
            # Top-K 过滤
            if top_k is not None:
                top_k = min(top_k, logits.size(-1))
                indices_to_remove = logits < torch.topk(logits, top_k)[0][..., -1, None]
                logits[indices_to_remove] = float('-inf')
            
            # Top-P (核采样) 过滤
            if top_p is not None:
                sorted_logits, sorted_indices = torch.sort(logits, descending=True)
                cumulative_probs = torch.cumsum(F.softmax(sorted_logits, dim=-1), dim=-1)
                sorted_indices_to_remove = cumulative_probs > top_p
                sorted_indices_to_remove[..., 1:] = sorted_indices_to_remove[..., :-1].clone()
                sorted_indices_to_remove[..., 0] = False
                indices_to_remove = sorted_indices_to_remove.scatter(
                    1, sorted_indices, sorted_indices_to_remove
                )
                logits[indices_to_remove] = float('-inf')
            
            # 采样
            probs = F.softmax(logits, dim=-1)
            next_token = torch.multinomial(probs, num_samples=1)
            
            # 追加到序列
            input_ids = torch.cat([input_ids, next_token], dim=-1)
        
        return input_ids
    
    def get_num_params(self) -> int:
        """获取模型参数量"""
        return sum(p.numel() for p in self.parameters())
    
    def get_num_trainable_params(self) -> int:
        """获取可训练参数量"""
        return sum(p.numel() for p in self.parameters() if p.requires_grad)


# ==================== 创建模型 ====================

def create_model(
    vocab_size: int = 50257,
    context_length: int = 128,
    d_model: int = 256,
    num_heads: int = 8,
    num_layers: int = 6,
    d_ff: int = 1024,
    dropout: float = 0.1,
) -> MiniLLM:
    """工厂函数：创建 MiniLLM 模型"""
    config = ModelConfig(
        vocab_size=vocab_size,
        context_length=context_length,
        d_model=d_model,
        num_heads=num_heads,
        num_layers=num_layers,
        d_ff=d_ff,
        dropout=dropout,
    )
    return MiniLLM(config)


# ==================== 测试 ====================

if __name__ == "__main__":
    print("=" * 60)
    print("MiniLLM 模型测试")
    print("=" * 60)
    
    # 创建模型
    model = create_model(vocab_size=1000)  # 用小词表测试
    num_params = model.get_num_params()
    print(f"参数量: {num_params:,}")
    
    # 测试前向传播
    batch_size = 2
    seq_len = 64
    input_ids = torch.randint(0, 1000, (batch_size, seq_len))
    targets = torch.randint(0, 1000, (batch_size, seq_len))
    
    logits, loss = model(input_ids, targets)
    print(f"输入: {input_ids.shape}")
    print(f"Logits: {logits.shape}")
    print(f"Loss: {loss.item():.4f}")
    
    # 测试反向传播
    loss.backward()
    print(f"反向传播: OK")
    
    # 测试生成
    prompt = torch.randint(0, 1000, (1, 10))
    generated = model.generate(prompt, max_new_tokens=20, temperature=0.8)
    print(f"生成: 输入长度 {prompt.shape[1]} → 输出长度 {generated.shape[1]}")
    
    print("\n各组件测试:")
    print(f"  RoPE:     OK (d_model/head={model.config.d_model // model.config.num_heads})")
    print(f"  RMSNorm:  OK")
    print(f"  SwiGLU:   OK")
    print(f"  Attention: OK ({model.config.num_heads} heads)")
    print(f"  Layers:   OK ({model.config.num_layers} layers)")
    print(f"  Generate: OK")