/**
 * ============================================================
 * Main — UI 控制器 & API 交互
 * ============================================================
 *
 * 【职责】
 *   1. 初始化页面，绑定按钮事件
 *   2. 通过 fetch() 调用 Java 后端的 REST API
 *   3. 将后端返回的排序步骤交给 Visualizer 播放动画
 *   4. 管理自定义算法编辑器的展开/收起
 *   5. 管理侧边栏折叠
 *
 * 【模块协作概览】
 *
 *   用户点击 "开始" 按钮
 *     │
 *     ├─→ main.js 收集当前算法名 + 数组
 *     │     │
 *     │     ├─→ 内置算法: POST /api/sort
 *     │     └─→ 自定义算法: POST /api/custom
 *     │
 *     ├─→ 后端 SortServer.java 收到请求
 *     │     └─→ SortEngine.java 执行排序，返回步骤列表
 *     │
 *     ├─→ main.js 收到 JSON 响应
 *     │     └─→ Visualizer.playSteps(steps, speed, onDone)
 *     │
 *     └─→ Visualizer（visualizer.js）逐帧绘制 Canvas 动画
 *
 *   custom 算法流程：
 *     用户在浏览器中写 Java 代码
 *       → 点击"保存并运行"
 *       → 代码通过 POST /api/custom 发送到后端
 *       → SortEngine 用 javax.tools.JavaCompiler 编译
 *       → 反射加载并执行
 *       → 返回步骤 → 前端播放动画
 * ============================================================
 */

const App = (() => {

  // ---- 当前状态 ----
  let currentAlgo = 'bubble';     // 当前选中的算法名称
  let currentSpeed = 80;          // 每步动画延迟（毫秒），越小越快
  let originalArray = [];         // 排序前的初始数组（重置时恢复）
  let isSorting = false;          // 是否正在排序
  let isPaused = false;           // 是否已暂停
  let customAlgoCache = {};       // 自定义算法缓存 {id: {name, code}}，从服务端加载

  // ---- 默认自定义算法代码 ----
  const DEFAULT_CODE = `import java.util.*;

public class CustomSort {
    public static void sort(int[] array, SortEngine.StepRecorder rec) {
        //在此编写你的算法
    }
}`;

  /**
   * 页面加载完成后初始化
   */
  function init() {
    // 1. 初始化画布
    Visualizer.init('sortCanvas');

    // 2. 初始化编辑器默认代码
    document.getElementById('modalEditor').value = DEFAULT_CODE;
    document.getElementById('codePreview').textContent = DEFAULT_CODE;

    // 3. 生成初始随机数组
    generateArray();

    // 4. 绑定所有 UI 事件
    bindEvents();

    // 5. 更新算法标签
    updateAlgoBadge();
  }

  // ============================================================
  // 数组操作
  // ============================================================

  /** 随机生成数组 */
  function generateArray() {
    const size = parseInt(document.getElementById('sizeInput').value);
    const arr = [];
    for (let i = 0; i < size; i++) {
      arr.push(Math.floor(Math.random() * 500) + 5);
    }
    originalArray = arr;
    Visualizer.setValues(arr);
    hidePlaceholder();
    setStatus('ready', '就绪 — 点击 ▶ 开始排序');
  }

  /** 应用用户输入的自定义数组 */
  function applyCustomArray() {
    const input = document.getElementById('customArrayInput').value.trim();
    if (!input) { toast('请输入空格分隔的数字', 'err'); return; }

    const parts = input.split(/\s+/).filter(Boolean);
    const arr = [];
    for (const p of parts) {
      const n = parseInt(p, 10);
      if (isNaN(n)) { toast(`无效数字: "${p}"`, 'err'); return; }
      if (n < 1 || n > 9999) { toast('值范围: 1 ~ 9999', 'err'); return; }
      arr.push(n);
    }
    if (arr.length < 2)  { toast('至少需要 2 个元素', 'err'); return; }
    if (arr.length > 500) { toast('最多 500 个元素', 'err'); return; }

    // 同步滑块和数字输入
    document.getElementById('sizeSlider').value = arr.length;
    document.getElementById('sizeInput').value = arr.length;

    originalArray = arr;
    Visualizer.setValues(arr);
    setStatus('ready', '已应用自定义数组');
    hidePlaceholder();
    toast(`已应用 ${arr.length} 个元素`, 'ok');
  }

  // ============================================================
  // 排序控制
  // ============================================================

  /** 开始排序（调用后端 API → 获取步骤 → 播放动画） */
  async function startSort() {
    if (isSorting && isPaused) {
      // 继续
      resumeSort();
      return;
    }

    // 重置为初始数组
    Visualizer.setValues(originalArray.slice());
    Visualizer.clearStates();
    setStatus('running', '排序中...');
    hidePlaceholder();
    setButtonsRunning(true);
    isSorting = true;
    isPaused = false;

    // 判断是内置算法还是自定义算法
    const useCustom = currentAlgo === 'custom';
    const url = useCustom ? '/api/custom' : '/api/sort';

    // 构建请求体
    const body = { algorithm: currentAlgo, array: originalArray.slice() };
    if (useCustom) {
      body.code = document.getElementById('modalEditor').value;
    }

    try {
      // 调用 Java 后端
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();

      if (data.error) {
        toast(data.error, 'err');
        setStatus('error', data.error);
        setButtonsRunning(false);
        isSorting = false;
        return;
      }

      // 播放步骤动画
      await Visualizer.playSteps(data.steps, currentSpeed, () => {
        setStatus('done', '排序完成!');
        setButtonsRunning(false);
        document.getElementById('btnPlay').textContent = '▶ 开始';
        isSorting = false;
      });

      // 如果被中途停止
      if (!Visualizer.isRunning()) {
        setStatus('ready', '已停止');
        setButtonsRunning(false);
        document.getElementById('btnPlay').textContent = '▶ 开始';
        isSorting = false;
      }
    } catch (err) {
      toast('请求失败: ' + err.message, 'err');
      setStatus('error', '网络错误');
      setButtonsRunning(false);
      isSorting = false;
    }
  }

  /** 暂停排序 */
  function pauseSort() {
    isPaused = true;
    Visualizer.pause();
    setStatus('paused', '已暂停');
    document.getElementById('btnPause').disabled = true;
    document.getElementById('btnPlay').disabled = false;
    document.getElementById('btnPlay').textContent = '▶ 继续';
  }

  /** 继续排序 */
  function resumeSort() {
    isPaused = false;
    Visualizer.resume();
    setStatus('running', '排序中...');
    document.getElementById('btnPause').disabled = false;
    document.getElementById('btnPlay').disabled = true;
  }

  /** 单步执行（每点击一次前进一步） */
  async function stepSort() {
    // 如果已经在单步模式中，直接前进一步
    if (Visualizer.isStepMode()) {
      Visualizer.advanceStep();
      return;
    }

    // 如果正在普通播放中，不能切换为单步
    if (isSorting && !Visualizer.isStepMode()) return;

    // 第一步：获取步骤数据，进入单步模式
    Visualizer.setValues(originalArray.slice());
    Visualizer.clearStates();
    setStatus('running', '单步模式 — 点击 ⏭ 前进');
    hidePlaceholder();
    setButtonsRunning(true);
    document.getElementById('btnStep').disabled = false; // 单步模式下保持可点击
    document.getElementById('btnPause').disabled = true;  // 单步模式不需要暂停
    isSorting = true;
    isPaused = false;

    const useCustom = currentAlgo === 'custom';
    const url = useCustom ? '/api/custom' : '/api/sort';
    const body = { algorithm: currentAlgo, array: originalArray.slice() };
    if (useCustom) body.code = document.getElementById('modalEditor').value;

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (data.error) { toast(data.error, 'err'); setButtonsRunning(false); isSorting = false; return; }

      // 以单步模式播放：每步等待用户点击 ⏭
      await Visualizer.playSteps(data.steps, 100, () => {
        setStatus('done', '单步完成');
        setButtonsRunning(false);
        isSorting = false;
      }, true); // ← true = stepByStep 模式
    } catch (err) {
      toast('请求失败: ' + err.message, 'err');
      setButtonsRunning(false);
      isSorting = false;
    }
  }

  /** 重置 */
  function resetSort() {
    Visualizer.stop();
    isSorting = false;
    isPaused = false;
    Visualizer.setValues(originalArray.slice());
    Visualizer.clearStates();
    setStatus('ready', '已重置');
    setButtonsRunning(false);
    document.getElementById('btnPlay').textContent = '▶ 开始';
    showPlaceholder();
  }

  // ============================================================
  // 自定义算法编辑器
  // ============================================================

  /** 打开编辑器模态框 */
  function openEditor() {
    document.getElementById('modalOverlay').classList.add('open');
    document.getElementById('modalEditor').focus();
  }

  /** 关闭编辑器模态框 */
  function closeEditor() {
    document.getElementById('modalOverlay').classList.remove('open');
    // 同步代码到预览区
    document.getElementById('codePreview').textContent =
      document.getElementById('modalEditor').value;
  }

  /** 重置编辑器代码为默认模板 */
  function resetToDefaultCode() {
    document.getElementById('modalEditor').value = DEFAULT_CODE;
    document.getElementById('customAlgoName').value = 'MySort';
    document.getElementById('codePreview').textContent = DEFAULT_CODE;
    toast('已恢复为默认代码', 'info');
  }

  /** 保存自定义算法（POST 到服务端，持久化为 CustomSort.java） */
  async function saveCustomAlgo() {
    const name = document.getElementById('customAlgoName').value.trim() || 'CustomSort';
    const code = document.getElementById('modalEditor').value.trim();

    if (!code) { toast('请先编写排序代码', 'err'); return; }

    try {
      const res = await fetch('/api/custom/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, code }),
      });
      const data = await res.json();
      if (data.error) { toast(data.error, 'err'); return; }

      // 更新本地缓存
      customAlgoCache['custom'] = { name, code };

      rebuildCustomOptions();
      document.getElementById('algorithmSelect').value = 'custom';
      currentAlgo = 'custom';
      updateAlgoBadge();
      closeEditor();
      toast(`已保存: ${name}`, 'ok');
    } catch (err) {
      toast('保存失败: ' + err.message, 'err');
    }
  }

  /** 加载示例模板 */
  function loadTemplate() {
    const templates = [
      {
        name: 'BubbleTemplate',
        code: `// 冒泡排序 — 自定义 Java 实现
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
    if (!swapped) {
        for (int k = 0; k < n - 1 - i; k++) rec.mark(k);
        break;
    }
}`,
      },
      {
        name: 'SelectionTemplate',
        code: `// 选择排序 — 自定义 Java 实现
int n = array.length;
for (int i = 0; i < n - 1; i++) {
    int minIdx = i;
    for (int j = i + 1; j < n; j++) {
        rec.compare(j, minIdx);
        if (array[j] < array[minIdx]) minIdx = j;
    }
    if (minIdx != i) rec.swap(i, minIdx);
    rec.mark(i);
}
rec.mark(n - 1);`,
      },
      {
        name: 'InsertionTemplate',
        code: `// 直接插入排序 — 自定义 Java 实现
int n = array.length;
rec.mark(0);
for (int i = 1; i < n; i++) {
    int j = i;
    while (j > 0) {
        rec.compare(j, j - 1);
        if (array[j] < array[j - 1]) {
            rec.swap(j, j - 1);
            j--;
        } else break;
    }
    rec.mark(i);
}`,
      },
    ];

    // 循环切换模板
    const current = document.getElementById('modalEditor').value;
    let idx = templates.findIndex(t => current.includes(t.name));
    idx = (idx + 1) % templates.length;
    const t = templates[idx];

    document.getElementById('customAlgoName').value = t.name;
    document.getElementById('modalEditor').value = t.code;
    document.getElementById('codePreview').textContent = t.code;
    toast(`已加载模板: ${t.name}`, 'info');
  }

  /** 重建下拉菜单中的自定义算法选项（从服务端加载 CustomSort.java） */
  async function rebuildCustomOptions() {
    const group = document.getElementById('customAlgoGroup');
    group.innerHTML = '';

    try {
      const res = await fetch('/api/custom/list');
      const data = await res.json();
      const entries = data.algorithms || [];

      // 重建缓存
      customAlgoCache = {};
      for (const algo of entries) {
        customAlgoCache[algo.id] = { name: algo.name, code: algo.code };
      }

      if (entries.length === 0) {
        const opt = document.createElement('option');
        opt.disabled = true;
        opt.textContent = '（无自定义算法）';
        group.appendChild(opt);
      } else {
        for (const algo of entries) {
          const opt = document.createElement('option');
          opt.value = algo.id;
          opt.textContent = '⭐ ' + algo.name;
          group.appendChild(opt);
        }
      }
    } catch (err) {
      const opt = document.createElement('option');
      opt.disabled = true;
      opt.textContent = '（加载失败）';
      group.appendChild(opt);
      console.error('加载自定义算法失败:', err);
    }
  }

  // ============================================================
  // UI 辅助
  // ============================================================

  function setButtonsRunning(running) {
    document.getElementById('btnPlay').disabled = running;
    document.getElementById('btnPause').disabled = !running;
    document.getElementById('btnStep').disabled = running;
  }

  function setStatus(state, text) {
    document.getElementById('statusText').textContent = text;
    const dot = document.getElementById('statusDot');
    dot.className = 'status-dot';
    if (state) dot.classList.add(state);
  }

  function updateAlgoBadge() {
    const sel = document.getElementById('algorithmSelect');
    const text = sel.options[sel.selectedIndex]?.text || currentAlgo;
    document.getElementById('algoBadge').textContent = text;
  }

  function hidePlaceholder() {
    document.getElementById('placeholder').classList.add('hidden');
  }
  function showPlaceholder() {
    document.getElementById('placeholder').classList.remove('hidden');
  }

  /** Toast 消息提示 */
  function toast(msg, type = 'info') {
    const container = document.getElementById('toastContainer');
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.textContent = msg;
    container.appendChild(el);
    setTimeout(() => el.remove(), 2500);
  }

  // ============================================================
  // 事件绑定
  // ============================================================

  function bindEvents() {
    // 算法选择
    document.getElementById('algorithmSelect').addEventListener('change', (e) => {
      resetSort();
      currentAlgo = e.target.value;
      updateAlgoBadge();
      // 如果是自定义算法，从缓存加载代码到编辑器
      const cached = customAlgoCache[currentAlgo];
      if (cached) {
        document.getElementById('customAlgoName').value = cached.name;
        document.getElementById('modalEditor').value = cached.code;
        document.getElementById('codePreview').textContent = cached.code;
      }
    });

    // 数组大小 — 滑块 ↔ 数字输入 双向同步
    document.getElementById('sizeSlider').addEventListener('input', (e) => {
      document.getElementById('sizeInput').value = e.target.value;
    });
    document.getElementById('sizeSlider').addEventListener('change', () => {
      resetSort();
      generateArray();
    });
    document.getElementById('sizeInput').addEventListener('change', () => {
      let val = parseInt(document.getElementById('sizeInput').value);
      if (isNaN(val) || val < 2) val = 2;
      if (val > 500) val = 500;
      document.getElementById('sizeInput').value = val;
      document.getElementById('sizeSlider').value = val;
      resetSort();
      generateArray();
    });

    // 随机生成按钮
    document.getElementById('btnGenerate').addEventListener('click', () => {
      resetSort();
      generateArray();
    });

    // 自定义数组按钮
    document.getElementById('btnCustomApply').addEventListener('click', () => {
      resetSort();
      applyCustomArray();
    });

    // 速度按钮
    document.querySelectorAll('.speed-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.speed-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        // speed-btn 的 data-speed 单位是毫秒/步，越小越快
        currentSpeed = parseInt(btn.dataset.speed);
      });
    });

    // 播放/暂停/单步/重置
    document.getElementById('btnPlay').addEventListener('click', startSort);
    document.getElementById('btnPause').addEventListener('click', pauseSort);
    document.getElementById('btnStep').addEventListener('click', stepSort);
    document.getElementById('btnReset').addEventListener('click', resetSort);

    // 侧边栏折叠按钮
    document.getElementById('btnToggleSidebar').addEventListener('click', () => {
      const panel = document.querySelector('.control-panel');
      const btn = document.getElementById('btnToggleSidebar');
      panel.classList.toggle('collapsed');
      btn.textContent = panel.classList.contains('collapsed') ? '⏴' : '☰';
      setTimeout(() => Visualizer.resizeCanvas(), 300);
    });

    // 代码编辑器 — 点击预览展开模态框
    document.getElementById('codePreview').addEventListener('click', openEditor);

    // 模态框
    document.getElementById('modalClose').addEventListener('click', closeEditor);
    document.getElementById('modalOverlay').addEventListener('click', (e) => {
      if (e.target === e.currentTarget) closeEditor(); // 点背景关闭
    });
    document.getElementById('btnSaveAlgo').addEventListener('click', saveCustomAlgo);
    document.getElementById('btnTemplate').addEventListener('click', loadTemplate);
    document.getElementById('btnResetCode').addEventListener('click', resetToDefaultCode);

    // 键盘快捷键
    document.addEventListener('keydown', (e) => {
      // ESC — 关闭模态框
      if (e.key === 'Escape') { closeEditor(); return; }

      // 空格 — 暂停/继续（焦点不在输入框或编辑器时生效）
      if (e.key === ' ' || e.code === 'Space') {
        const tag = document.activeElement?.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
        e.preventDefault(); // 阻止页面滚动
        if (isSorting && !isPaused) {
          pauseSort();
        } else if (isSorting && isPaused) {
          resumeSort();
        }
      }
    });

    // 加载已保存的自定义算法
    rebuildCustomOptions();
  }

  // 公开初始化入口
  return { init };
})();

// ---- 页面加载完成后启动 ----
document.addEventListener('DOMContentLoaded', () => App.init());
