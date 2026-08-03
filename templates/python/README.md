# Python 项目模板

## 目录结构

```
python/
├── requirements.txt
├── README.md
├── src/
│   └── main.py
└── tests/
    └── test_main.py
```

## 常用命令

```bash
# 创建虚拟环境
python -m venv .venv

# 激活虚拟环境
# Windows:
.venv\Scripts\activate
# macOS/Linux:
# source .venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 运行
python src/main.py

# 测试（需要安装 pytest: pip install pytest）
pytest
```
