# SortViz — 排序算法可视化

面向初学者的排序动画教学软件。**Java 编写排序算法**，前端 Canvas 渲染动画，前后端通过 REST API 协作。

---

## 项目结构

```
SortViz/
├── index.html             # 主页面
├── css/
│   └── style.css          # 暗色主题 + 响应式布局
├── js/
│   ├── visualizer.js      # Canvas 渲染引擎 + 动画播放器
│   └── main.js            # UI 控制器 + API 调用
├── java/                  # Java 源代码
│   ├── SortServer.java    # HTTP 服务器（端口 8080）
│   ├── SortEngine.java    # 排序算法 + 自定义代码编译
│   └── CustomSort.java    # 用户自定义算法（运行时生成）
├── out/                   # 编译产物（.class 文件）
├── .gitignore
├── README.md
└── SortViz.iml            # IDEA 项目配置文件
```

**零依赖** — 纯 JDK + 浏览器即可运行，无需安装任何第三方库或框架。

---

## 阅读顺序

| 顺序 | 文件 | 内容 |
|------|------|------|
| 1 | `index.html` | 页面 UI 元素和整体布局结构 |
| 2 | `css/style.css` | CSS 变量 → 布局 → 响应式断点 |
| 3 | `java/SortServer.java` | 静态文件服务 + REST API 路由 |
| 4 | `java/SortEngine.java` | `SortStep` → `StepRecorder` → 排序算法实现 |
| 5 | `js/visualizer.js` | `draw()` 绘图 → `playSteps()` 动画循环 |
| 6 | `js/main.js` | `bindEvents()` 事件绑定 → `startSort()` 主链路 |

---

## 快速开始

### 环境要求

- **JDK 11+**（`javax.tools.JavaCompiler` 需要 JDK 而非 JRE）

### 启动步骤

```bash
# 1. 进入项目目录
cd SortViz

# 2. 编译（.class 输出到 out/）
javac -encoding UTF-8 -d out java/SortEngine.java java/SortServer.java

# 3. 启动服务器
java -cp out SortServer
```

启动成功后，控制台显示：

```
╔══════════════════════════════════════╗
║   SortViz Server                     ║
║   http://localhost:8080              ║
║   已注册 7 个排序算法                 ║
╚══════════════════════════════════════╝
```

打开浏览器访问 **http://localhost:8080**

### 停止服务器

按 `Ctrl + C` 或执行 `pkill -f SortServer`

---

## 操作指南

| 操作 | 方式 |
|------|------|
| 选择算法 | 下拉菜单切换（内置 7 种 + 自定义） |
| 设置数组长度 | 拖动滑块 或 直接键入数字 |
| 自定义数组 | 输入框键入空格分隔数字 → 点击 ✅ 应用 |
| 开始排序 | 点击 ▶ 开始 |
| 暂停 / 继续 | 点击 ⏸ 暂停，或按**空格键** |
| 单步执行 | 点击 ⏭ 单步（每点一次前进一步） |
| 重置 | 点击 ↺ 重置 |
| 调整速度 | 点击 慢/中/快/极快/⚡（排序中可实时切换） |
| 自定义算法 | 点击代码预览区 → 展开全屏编辑器 → 编写 Java 代码 → 💾 保存 |
| 加载模板 | 点击 📋 模板循环切换预置示例 |
| 折叠侧边栏 | 点击 ☰ 按钮（小屏自动显示）|

---

## 柱状图颜色

| 颜色 | 状态 | 含义 |
|------|------|------|
| 🔵 蓝色 | 默认 | 未排序 |
| 🟡 黄色 | 比较中 | 正在比较两个元素 |
| 🔴 红色 | 交换中 | 正在交换两个元素 |
| 🟢 绿色 | 已排序 | 元素已到达最终位置 |

---

## 模块协作流程

```
用户点击 ▶ 开始
  │
  ├─→ main.js 收集算法名 + 数组
  │     │
  │     ├─→ 内置算法: POST /api/sort ────→ SortServer.java
  │     └─→ 自定义算法: POST /api/custom ──→  │
  │                                             ↓
  │                                       SortEngine.java
  │                                          │ 执行排序
  │                                          │ 记录每一步 (compare/swap/mark)
  │                                          │ 自定义: javac 编译 + 反射执行
  │     ←─────── JSON {steps: [...]} ────────┘
  │     │
  │     └─→ Visualizer.playSteps(steps, speed)
  │           │ 逐帧: 更新数组 → 设置柱子颜色 → 延时 → 下一帧
  │           │ 支持: 播放 / 暂停 / 单步 / 停止 / 速度动态调整
  │           └─→ Canvas 渲染动画
```

---

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/algorithms` | 返回内置算法名称列表 |
| `POST` | `/api/sort` | 执行内置排序，返回所有步骤 |
| `POST` | `/api/custom` | 编译并执行用户自定义 Java 代码 |
| `GET` | `/api/custom/list` | 获取已保存的自定义算法 |
| `POST` | `/api/custom/save` | 保存自定义算法到 `java/CustomSort.java` |
| `DELETE` | `/api/custom/delete` | 删除自定义算法文件 |

### POST /api/sort — 执行内置排序

**请求：**
```json
{"algorithm": "bubble", "array": [5, 3, 8, 1, 9]}
```

**响应：**
```json
{"steps": [
  {"type": "compare", "i": 0, "j": 1, "array": [5, 3, 8, 1, 9]},
  {"type": "swap",    "i": 0, "j": 1, "array": [3, 5, 8, 1, 9]},
  {"type": "mark",    "i": 4, "j":-1, "array": [1, 3, 5, 8, 9]}
]}
```

### POST /api/custom — 执行自定义排序

**请求：**
```json
{
  "code": "public class CustomSort { public static void sort(int[] array, SortEngine.StepRecorder rec) { ... } }",
  "array": [5, 3, 8, 1, 9]
}
```

**响应：** 格式同 `/api/sort`

### POST /api/custom/save — 保存自定义算法

**请求：**
```json
{"name": "MySort", "code": "public class CustomSort { ... }"}
```

**响应：**
```json
{"ok": true}
```

代码固定保存至 `java/CustomSort.java`，首行包含 `// @sortviz-name MySort` 元数据注释。

### GET /api/custom/list — 获取已保存的算法

**响应：**
```json
{"algorithms": [
  {"id": "custom", "name": "MySort", "code": "public class CustomSort { ... }"}
]}
```

如 `CustomSort.java` 不存在则返回空数组 `{"algorithms":[]}`。

### DELETE /api/custom/delete — 删除自定义算法

**请求：** 无参数

**响应：**
```json
{"ok": true}
```

---

## 自定义算法指南

### 编写规范

用户在浏览器中编写 Java 代码片段，服务端自动包装为完整类后编译执行。代码中可访问两个对象：

| 对象 | 类型 | 说明 |
|------|------|------|
| `array` | `int[]` | 待排序的数组（直接读写） |
| `rec` | `StepRecorder` | 步骤记录器，用于记录每一步操作 |

### `rec` 的可用方法

```java
rec.compare(i, j);   // 比较 array[i] 和 array[j]，前端高亮黄色
rec.swap(i, j);      // 交换 array[i] 和 array[j]，前端高亮红色
rec.mark(i);         // 标记 array[i] 已排序，前端变绿色
```

### 示例 — 冒泡排序

```java
int n = array.length;
for (int i = 0; i < n - 1; i++) {
    boolean swapped = false;
    for (int j = 0; j < n - 1 - i; j++) {
        rec.compare(j, j + 1);
        if (array[j] > array[j + 1]) {
            rec.swap(j, j + 1);
            swapped = true;
        }
    }
    rec.mark(n - 1 - i);
    if (!swapped) break;
}
```

### 使用流程

1. 点击代码预览区 → 展开全屏编辑器
2. 编写 Java 排序代码（参考上方示例）
3. 点击「📋 模板」查看预置示例
4. 点击「💾 保存」→ 代码持久化为 `java/CustomSort.java`
5. 下拉菜单中出现「⭐ 自定义算法」
6. 选择后点击「▶ 开始」执行

---

## 自定义算法处理机制

```
前端点击"保存"
    ↓
SortServer.handleSaveCustom()
    ↓
写入 java/CustomSort.java（持久化存储）
    ↓
前端点击"运行"（选择自定义算法）
    ↓
SortServer.handleCustom()
    ↓
读取编辑器中的代码（或从 CustomSort.java 加载）
    ↓
SortEngine.executeCustom()
    ↓
CompilerHelper.compile()
    ├── 在 /tmp/ 创建临时目录
    ├── 写入 .java 文件
    ├── 调用 javax.tools.JavaCompiler 编译
    └── 读取 .class 字节码 → 清理临时文件
    ↓
CompilerHelper.loadAndInvoke()
    ├── 自定义 ClassLoader 加载字节码
    └── 反射调用 sort() 方法
    ↓
返回排序步骤 → 前端播放动画
```

**两套机制分离：**
- **持久化存储**：`java/CustomSort.java` 保存用户代码，服务器重启后依然存在
- **运行时编译**：`/tmp/sortviz_xxx/` 临时文件，编译执行后自动清理

---

## 内置排序算法（7 种）

| 算法 | 时间复杂度 | 稳定性 |
|------|-----------|--------|
| 冒泡排序 | O(n²) | 稳定 |
| 选择排序 | O(n²) | 不稳定 |
| 直接插入排序 | O(n²) | 稳定 |
| 快速排序 | O(n log n) | 不稳定 |
| 归并排序 | O(n log n) | 稳定 |
| 堆排序 | O(n log n) | 不稳定 |
| 希尔排序 | O(n log n) | 不稳定 |

---

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 结构 | HTML5 | 语义化标签 |
| 样式 | CSS3 | 变量 + Grid + Flexbox + 响应式 + 暗色主题 |
| 前端逻辑 | ES6 JavaScript | IIFE 模块化 |
| 动画渲染 | Canvas API + requestAnimationFrame | 60fps 流畅绘制 |
| 后端服务 | `com.sun.net.httpserver` | JDK 内置 HTTP 服务器 |
| 动态编译 | `javax.tools.JavaCompiler` | JDK 内置，运行时编译 Java 代码 |
| 代码加载 | 自定义 ClassLoader + 反射 | 加载动态编译的字节码 |

---

## 故障排除

### 端口被占用

```
错误：端口 8080 已被占用
```

**解决方案：**
```bash
# 查找并关闭占用端口的进程
pkill -f SortServer
# 或修改 SortServer.java 中的 PORT 常量
```

### 自定义算法编译失败

```
错误：编译失败，请检查代码
```

**常见原因：**
- 缺少 `import java.util.*;`
- 类名不是 `CustomSort`
- 方法签名与 `sort(int[], SortEngine.StepRecorder)` 不匹配
- 代码中有语法错误

**提示：** 点击「📋 模板」加载正确示例作为参考。

### 需要 JDK 而非 JRE

```
错误：需要 JDK 环境。请用 JDK 运行，而非 JRE。
```

**原因：** `javax.tools.JavaCompiler` 需要完整的 JDK 环境，JRE 不包含编译工具。

**解决方案：** 安装 JDK 11+ 并用 `java -cp out SortServer` 启动。

---
