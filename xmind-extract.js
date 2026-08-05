/**
 * XMind 内容提取工具
 * 从 .xmind 文件中提取纯文本树形结构，去掉所有样式元数据，节省 token。
 *
 * 用法: node xmind-extract.js <xmind文件路径>
 * 输出: 纯文本树形结构
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const os = require('os');

// 递归提取标题树
function extractTree(topic) {
  const node = { title: topic.title || '(无标题)' };
  const children = topic.children?.attached;
  if (children && children.length > 0) {
    node.children = children.map(extractTree);
  }
  return node;
}

// 将树渲染为文本
function renderTree(node, prefix = '', isLast = true, isRoot = true) {
  const lines = [];
  if (isRoot) {
    lines.push(node.title);
  } else {
    const connector = isLast ? '└── ' : '├── ';
    lines.push(prefix + connector + node.title);
  }

  if (node.children) {
    const childCount = node.children.length;
    node.children.forEach((child, i) => {
      const isLastChild = i === childCount - 1;
      const childPrefix = isRoot
        ? ''
        : prefix + (isLast ? '    ' : '│   ');
      lines.push(...renderTree(child, childPrefix, isLastChild, false));
    });
  }
  return lines;
}

// 主流程
function main() {
  const args = process.argv.slice(2);
  if (args.length === 0) {
    console.error('用法: node xmind-extract.js <xmind文件路径>');
    console.error('示例: node xmind-extract.js "../Java/数据结构/产品思维.xmind"');
    process.exit(1);
  }

  const xmindPath = path.resolve(args[0]);
  if (!fs.existsSync(xmindPath)) {
    console.error(`文件不存在: ${xmindPath}`);
    process.exit(1);
  }

  if (!xmindPath.endsWith('.xmind')) {
    console.error('请提供 .xmind 文件');
    process.exit(1);
  }

  // 创建临时目录
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'xmind-extract-'));

  try {
    // 复制为 .zip 后解压
    const zipPath = path.join(tempDir, 'temp.zip');
    fs.copyFileSync(xmindPath, zipPath);

    if (process.platform === 'win32') {
      // PowerShell 单引号字符串中，单引号需双写转义
      const escZip = zipPath.replace(/'/g, "''");
      const escDir = tempDir.replace(/'/g, "''");
      execSync(
        `powershell -Command "Expand-Archive -Path '${escZip}' -DestinationPath '${escDir}' -Force"`,
        { stdio: 'pipe' }
      );
    } else {
      execSync(`unzip -o "${zipPath}" -d "${tempDir}"`, { stdio: 'pipe' });
    }

    // 读取 content.json
    const contentPath = path.join(tempDir, 'content.json');
    if (!fs.existsSync(contentPath)) {
      console.error('content.json 未找到，文件可能已损坏');
      process.exit(1);
    }

    const raw = fs.readFileSync(contentPath, 'utf-8');
    const sheets = JSON.parse(raw);

    // 提取所有画布
    sheets.forEach((sheet, i) => {
      if (sheets.length > 1) {
        console.log(`\n=== ${sheet.title} ===\n`);
      }
      const tree = extractTree(sheet.rootTopic);
      const lines = renderTree(tree);
      console.log(lines.join('\n'));
    });
  } finally {
    // 清理临时目录
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

main();