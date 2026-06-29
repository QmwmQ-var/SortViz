# SortViz — 排序算法可视化

面向初学者的排序动画教学软件。**Java 编写排序算法**，前端 Canvas 渲染动画，前后端通过 REST API 协作。

---

## 项目结构

```
sort demo/
├── index.html             # 主页面
├── css/
│   └── style.css          # 暗色主题 + 响应式布局
├── js/
│   ├── visualizer.js      # Canvas 渲染引擎 + 动画播放器
│   └── main.js            # UI 控制器 + API 调用
├── java/                  # 源代码
│   ├── SortServer.java    # HTTP 服务器（端口 8080）
│   └── SortEngine.java    # 排序算法 + 自定义代码编译
├── build/                 # 编译产物（.class 文件）
└── README.md
```

**零依赖** — 纯 JDK + 浏览器即可运行。

---

## 阅读顺序（初学者推荐）

| 顺序 | 文件 | 看什么 |
|------|------|--------|
| 1 | `index.html` | 页面上有哪些 UI 元素 |
| 2 | `css/style.css` | 变量定义 → `.main-content` 布局 → `@media` 断点 |
| 3 | `java/SortServer.java` | 静态文件服务 + 三个 API 路由 |
| 4 | `java/SortEngine.java` | `SortStep` → `StepRecorder` → 挑 `selectionSort` 看 |
| 5 | `js/visualizer.js` | `draw()` 绘图 → `playSteps()` 动画循环 |
| 6 | `js/main.js` | `bindEvents()` 事件绑定 → `startSort()` 主链路 |

---

## 快速开始

### 环境要求

- **JDK 11+**（`javax.tools.JavaCompiler` 需要 JDK 而非 JRE）

### 启动

```bash
# 1. 编译（.class 输出到 build/，与源码隔离）
javac -encoding UTF-8 -d build java/SortEngine.java java/SortServer.java

# 2. 启动
java -cp build SortServer
```

打开 **http://localhost:8080**

---

## 操作指南

| 操作 | 方式 |
|------|------|
| 选择算法 | 下拉菜单切换 |
| 设置数组长度 | 拖动滑块 或 直接键入数字 |
| 自定义数组 | 输入框键入逗号分隔数字 → 点击 ✅ 应用 |
| 开始排序 | 点击 ▶ 开始 |
| 暂停 / 继续 | 点击 ⏸ 暂停，或按**空格键** |
| 单步执行 | 点击 ⏭ 单步（每点一次前进一步）|
| 重置 | 点击 ↺ 重置 |
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
  │                                          │ 记录每一步
  │                                          │ 自定义: javac 编译 + 反射执行
  │     ←─────── JSON {steps: [...]} ────────┘
  │     │
  │     └─→ Visualizer.playSteps(steps, speed)
  │           │ 逐帧: 更新数组 → 设柱子颜色 → delay → 下一帧
  │           │ 支持: 播放 / 暂停 / 单步 / 停止
  │           └─→ Canvas 渲染动画
```

---

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/algorithms` | 返回内置算法名称列表 |
| `POST` | `/api/sort` | 执行内置排序，返回所有步骤 |
| `POST` | `/api/custom` | 编译并执行用户自定义 Java 代码 |
| `GET` | `/api/custom/list` | 列出所有已保存的自定义算法 |
| `POST` | `/api/custom/save` | 保存自定义算法为 .java 文件 |
| `DELETE` | `/api/custom/delete` | 删除自定义算法文件 |

### POST /api/sort

```json
请求 → {"algorithm": "bubble", "array": [5, 3, 8, 1, 9]}

响应 → {"steps": [
  {"type": "compare", "i": 0, "j": 1, "array": [5, 3, 8, 1, 9]},
  {"type": "swap",    "i": 0, "j": 1, "array": [3, 5, 8, 1, 9]},
  ...
  {"type": "mark",    "i": 4, "j":-1, "array": [1, 3, 5, 8, 9]}
]}
```

### POST /api/custom

```json
请求 → {"code": "public class CustomSort { ... }", "array": [5, 3, 8, 1, 9]}

响应 → {"steps": [...]}  // 格式同 POST /api/sort
```

### POST /api/custom/save

```json
请求 → {"name": "My Sort", "code": "public class CustomSort { ... }"}

响应 → {"ok": true}
```
代码固定保存至 `java/CustomSort.java`（类名与文件名统一为 `CustomSort`），首行包含 `// @sortviz-name My Sort`。

### GET /api/custom/list

```json
响应 → {"algorithms": [
  {"id": "custom", "name": "My Sort", "code": "public class CustomSort { ... }"}
]}
```
如 `CustomSort.java` 不存在则返回空数组。

### DELETE /api/custom/delete

```json
请求 → （无需参数）

响应 → {"ok": true}
```

---

## 自定义算法指南

用户编写 Java 代码片段，服务端自动包装为完整类后编译执行。代码中可访问两个对象：

| 对象 | 类型 | 说明 |
|------|------|------|
| `array` | `int[]` | 待排序的数组（直接读写）|
| `rec` | `StepRecorder` | 步骤记录器 |

**`rec` 的方法：**

```java
rec.compare(i, j);   // 比较 array[i] 和 array[j]，前端高亮黄色
rec.swap(i, j);      // 交换 array[i] 和 array[j]，前端高亮红色
rec.mark(i);         // 标记 array[i] 已排序，前端变绿色
```

**示例 — 冒泡排序：**

```java
int n = array.length;
for (int i = 0; i < n - 1; i++) {
    for (int j = 0; j < n - 1 - i; j++) {
        rec.compare(j, j + 1);
        if (array[j] > array[j + 1]) {
            rec.swap(j, j + 1);
        }
    }
    rec.mark(n - 1 - i);
}
```

点击代码预览区可**展开全屏编辑器**，点击「📋 模板」查看预置示例。保存后算法持久化为 `java/CustomSort.java` 文件（类名与文件名一致），出现在下拉菜单中。文件首行包含 `// @sortviz-name <显示名>` 元数据注释，可直接用 IDE 编辑或纳入版本控制。

---

## 内置排序算法（7 种）

| 算法     | 时间复杂度 |
|--------|-----------|
| 冒泡排序   | O(n²) |
| 选择排序   | O(n²) |
| 直接插入排序 | O(n²) |
| 快速排序   | O(n log n) |
| 归并排序   | O(n log n) |
| 堆排序    | O(n log n) |
| 希尔排序   | O(n log n) |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 结构 | HTML5 |
| 样式 | CSS3（变量 + Grid + Flexbox + 响应式）|
| 前端逻辑 | ES6 JavaScript（IIFE 模块）|
| 动画 | Canvas API + requestAnimationFrame |
| 后端 | `com.sun.net.httpserver`（JDK 内置）|
| 动态编译 | `javax.tools.JavaCompiler`（JDK 内置）|