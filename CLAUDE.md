# CLAUDE.md — SortViz 排序算法可视化

> 面向初学者的排序动画教学软件。**Java 后端 + Web 前端，零第三方依赖。**

## 项目架构

```
浏览器 (index.html)
  │
  ├─ js/main.js          ← UI 控制器、API 调用、事件绑定
  ├─ js/visualizer.js    ← Canvas 渲染引擎、动画播放器
  └─ css/style.css       ← 深色主题 + 响应式布局
       │
       ▼ HTTP (fetch)
Java 后端 (端口 8080)
  ├─ SortServer.java     ← HTTP 服务器 (com.sun.net.httpserver)
  └─ SortEngine.java     ← 排序算法 + 动态编译自定义代码
```

**加载顺序**: `visualizer.js` 先加载（提供渲染能力），`main.js` 后加载（控制器调用 visualizer）。

## 编译 & 启动

```bash
# 编译（输出到 out/ 目录）
javac -encoding UTF-8 -d out java/SortEngine.java java/SortServer.java

# 启动服务器
java -cp out SortServer
```

然后打开 http://localhost:8080 。需要 **JDK 11+**（`javax.tools.JavaCompiler` 需要 JDK 而非 JRE）。

## 关键架构细节

### 数据流
```
用户点击开始 → main.js 收集算法名+数组 → POST /api/sort 或 /api/custom
→ SortServer 路由到对应 handler → SortEngine.execute() 执行排序
→ 返回 List<SortStep> → 序列化为 JSON → 前端 Visualizer.playSteps() 播放动画
```

### SortStep 三步操作类型
| type | 含义 | 前端颜色 |
|------|------|----------|
| `compare` | 比较两个元素 | 黄色 |
| `swap` | 交换两个元素 | 红色 |
| `mark` | 标记为已排序 | 绿色 |

### 自定义算法机制
用户写 Java 代码片段 → `SortEngine.executeCustom()` 用 `javax.tools.JavaCompiler` 编译到临时目录 → 自定义 ClassLoader 加载字节码 → 反射调用 `sort(int[] array, StepRecorder rec)` → 返回步骤。

用户代码可访问: `array` (int[]), `rec.compare(i,j)`, `rec.swap(i,j)`, `rec.mark(i)`

### API 路由
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/algorithms` | 返回内置算法名列表 |
| POST | `/api/sort` | 执行内置排序 |
| POST | `/api/custom` | 编译执行自定义代码 |
| GET | `/api/custom/list` | 列出所有自定义算法文件 |
| POST | `/api/custom/save` | 保存自定义算法为 .java 文件 |
| DELETE | `/api/custom/delete` | 删除自定义算法文件 |
| GET | `/` | 静态文件服务 |

### 前端模块模式
两个 JS 文件都使用 **IIFE 模式** (`const App = (() => { ... })())`，各自暴露公共 API 对象。没有使用 ES modules。

## 项目文件清单

| 文件 | 职责 |
|------|------|
| `index.html` | 主页面，完整布局（顶栏 + Canvas + 控制面板 + 底栏 + 模态框编辑器） |
| `css/style.css` | 默认深蓝主题，变量在 `:root`，响应式断点 1400/1100/768/480px |
| `js/visualizer.js` | Canvas 绑到 `#sortCanvas`，用 ResizeObserver 自适应，60fps 渲染循环 |
| `js/main.js` | 所有 DOM 事件绑定、fetch 调用、自定义算法 localStorage 持久化 |
| `java/SortEngine.java` | 7 种内置算法 + `CompilerHelper` 内部类（编译 & 反射加载） |
| `java/SortServer.java` | HTTP 服务，自带简易 JSON 解析器，无第三方依赖 |
| `README.md` | 面向用户的操作指南和技术文档 |

## 内置排序算法

`SortEngine.ALGORITHM_NAMES` 数组定义了 7 种算法名（也是 API 中使用的标识符）:
`bubble`, `selection`, `insertion`, `quick`, `merge`, `heap`, `shell`

## 注意事项

- `SortServer.java` 自带简易 JSON 解析器 (`parseJson`, `parseObj`, `parseArr`)，不依赖 Gson/Jackson
- `SortEngine.wrapUserCode()` 目前直接返回用户代码（未做包装），用户需要自行写完整的类定义
- 自定义算法固定保存为 `java/CustomSort.java`，类名与文件名统一为 `CustomSort`
- 文件首行为 `// @sortviz-name <显示名>` 元数据注释，前端 ID 固定为 `"custom"`
- 前端通过 API 管理自定义算法（不再使用 localStorage）
- 静态文件服务有路径穿越防护（`..` 检查 + normalize + startsWith 校验）
- `.iml` 文件是 IntelliJ IDEA 项目配置，源码目录设为 `java/`