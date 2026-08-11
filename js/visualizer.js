/**
 * ============================================================
 * Visualizer — Canvas 渲染 & 动画引擎
 * ============================================================
 *
 * 【职责】
 *   1. 在 Canvas 上绘制柱状图
 *   2. 执行排序步骤的动画（逐步播放后端返回的 steps）
 *   3. 暴露出 SORT_STATE 供算法模块设置状态
 *
 * 【模块协作】
 *   main.js（控制器）
 *     │ 调用 playSteps(steps, speed)
 *     ↓
 *   visualizer.js（本文件）
 *     │ 按时间间隔逐帧播放步骤
 *     │ 每帧调用 draw() 重绘 Canvas
 *     ↓
 *   用户在屏幕上看到排序动画
 *
 * 【柱状图颜色规则】
 *   默认色 (default)  → 蓝色  #5b8def
 *   比较中 (comparing)→ 黄色  #facc15
 *   交换中 (swapping) → 红色  #f87171
 *   已排序 (sorted)   → 绿色  #4ade80
 * ============================================================
 */

const Visualizer = (() => {

  // ---- 柱子状态常量 ----
  const STATE = {
    DEFAULT:   'default',
    COMPARING: 'comparing',
    SWAPPING:  'swapping',
    SORTED:    'sorted',
  };

  // ---- 每根柱子的颜色 ----
  const COLORS = {
    [STATE.DEFAULT]:   '#5b8def',
    [STATE.COMPARING]: '#facc15',
    [STATE.SWAPPING]:  '#f87171',
    [STATE.SORTED]:    '#4ade80',
  };

  // ---- 内部状态 ----
  let canvas, ctx, wrapper;
  let values = [];          // 当前数组值
  let states = [];          // 每根柱子的状态
  let animFrameId = null;   // requestAnimationFrame ID，存储动画帧ID
  let needsDraw = true;     // 脏标记

  /**
   * 初始化：绑定 Canvas，监听容器大小变化
   * 由 main.js 在页面加载时调用一次
   */
  function init(canvasId) {
    canvas = document.getElementById(canvasId);
    ctx = canvas.getContext('2d');
    wrapper = document.getElementById('canvasWrapper');

    // 监听wrapper容器大小变化 → 自动调整 Canvas 分辨率，resizeCanvas()是自定义
    new ResizeObserver(() => resizeCanvas()).observe(wrapper);
    // 手动调用一次，初始化画布
    resizeCanvas();

    // 启动渲染循环（60fps，只在 needsDraw=true 时绘制）
    function loop() {
      if (needsDraw) { draw(); needsDraw = false; }
      animFrameId = requestAnimationFrame(loop); //屏幕刷新时，执行 loop 函数
    }
    loop();
  }

  /** 根据容器实际大小调整 Canvas 宽高 */
  function resizeCanvas() {
    if (!canvas || !wrapper) return;
    const rect = wrapper.getBoundingClientRect();
    //1个CSSpx = dpr个屏幕物理像素点
    const dpr = window.devicePixelRatio || 1;
    canvas.width  = rect.width  * dpr; //画布像素尺寸，解决高清屏模糊
    canvas.height = rect.height * dpr;
    canvas.style.width  = rect.width  + 'px'; //CSS 显示尺寸，决定视觉大小
    canvas.style.height = rect.height + 'px';
    //把绘图坐标系放大dpr倍，之后绘图直接用css像素，不用处处乘dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    needsDraw = true;
  }

  /**
   * 设置要显示的数据
   * @param {number[]} vals — 数组值
   */
  function setValues(vals) {
    values = vals.slice();
    states = new Array(vals.length).fill(STATE.DEFAULT);
    needsDraw = true;
  }

  /**
   * 更新某根柱子的状态
   * @param {number} i     — 索引
   * @param {string} state — 'default' | 'comparing' | 'swapping' | 'sorted'
   */
  function setState(i, state) {
    states[i] = state;
    needsDraw = true;
  }

  /** 清除所有状态，全部恢复默认 */
  function clearStates() {
    states = new Array(values.length).fill(STATE.DEFAULT);
    needsDraw = true;
  }

  /** 触发立即重绘 */
  function redraw() { needsDraw = true; }

  // ---- Canvas 绘制 ----

  function draw() {
    if (!ctx) return;
    const w = canvas.width  / (window.devicePixelRatio || 1);
    const h = canvas.height / (window.devicePixelRatio || 1);
    if (w <= 1 || h <= 1) return;

    const n = values.length;
    if (n === 0) return;

    // 清空画布
    ctx.clearRect(0, 0, w, h);

    // 布局参数（留边距）
    // padTop 需要足够大，以便在柱子上方完整显示数值标签
    const padX    = Math.max(4,  w * 0.015);
    const padTop  = Math.max(28, h * 0.10);
    const padBot  = Math.max(28, h * 0.12);
    const availW  = w - padX * 2;
    const availH  = h - padTop - padBot;
    const barW    = Math.max(1, Math.min(availW / n * 0.82, availW / n));
    const gap     = n > 1 ? (availW - barW * n) / (n - 1) : 0;
    const maxVal  = Math.max(...values, 1);
    const scaleY  = availH / maxVal;

    // 逐根绘制
    for (let i = 0; i < n; i++) {
      const valH = Math.max(2, values[i] * scaleY);
      const x    = padX + i * (barW + gap);
      const y    = padTop + availH - valH;
      const color = COLORS[states[i]] || COLORS[STATE.DEFAULT];

      // 渐变填充
      const grad = ctx.createLinearGradient(x, y, x, y + valH);
      grad.addColorStop(0, color);
      grad.addColorStop(1, darken(color, 0.35));
      ctx.fillStyle = grad;

      // 圆角矩形，x轴水平向右，y轴竖直向下，逆时针绘制
      const r = Math.min(barW / 2, 3);
      ctx.beginPath();
      ctx.moveTo(x + r, y); //路径起点，顶部左圆角结束点
      ctx.lineTo(x + barW - r, y);
      ctx.quadraticCurveTo(x + barW, y, x + barW, y + r); //(cpx,cpy)为控制点，此即原直角点
      ctx.lineTo(x + barW, y + valH);
      ctx.lineTo(x, y + valH);
      ctx.lineTo(x, y + r);
      ctx.quadraticCurveTo(x, y, x + r, y);
      ctx.closePath();
      ctx.fill();

      // 数组 ≤ 50 时在柱子底部显示数值
      if (n <= 50) {
        const fontSize = Math.min(60, barW * 0.65);
        ctx.fillStyle = 'rgba(255,255,255,0.85)';
        ctx.font = `${fontSize}px "JetBrains Mono", monospace`;
        ctx.textAlign = 'center';
        // 统一绘制在柱子底部下方
        const labelY = y + valH + fontSize + 4;
        ctx.fillText(values[i], x + barW / 2, labelY);
      }
    }
  }

  /** 颜色加暗 */
  function darken(hex, amount) {
    const r = parseInt(hex.slice(1,3), 16);
    const g = parseInt(hex.slice(3,5), 16);
    const b = parseInt(hex.slice(5,7), 16);
    const f = 1 - amount;
    const h = (c) => Math.round(c * f).toString(16).padStart(2, '0');
    return `#${h(r)}${h(g)}${h(b)}`;
  }

  // ============================================================
  // 动画播放引擎
  // ============================================================

  let playbackTimer = null;   // setTimeout ID
  let isPaused = false;       // 是否暂停,暂时停止
  let resumeCallback = null;  // 暂停时 resolve 的函数
  let isStopped = false;      // 是否被终止,永久停止
  let stepMode = false;       // 单步模式
  let stepResolve = null;     // 单步模式下等待用户点击的 resolve，存储 Promise 的 resolve 函数的变量

  /**
   * 逐步播放排序步骤（动画循环的核心）
   *
   * 工作流程：
   *   1. 遍历后端返回的 steps 数组
   *   2. 每一步：更新数组值 → 设置柱子状态 → 等待 speed 毫秒
   *   3. 全部播完后恢复柱子颜色并回调 onDone
   *
   * @param {Array}  steps  — 步骤数组 [{type, i, j, array}, ...]
   * @param {number} speedFn  — 每步之间的延迟（毫秒），步进模式下忽略
   * @param {Function} onDone — 播放完毕回调
   * @param {boolean} stepByStep — 是否启用单步模式
   */
  async function playSteps(steps, speedFn, onDone, stepByStep) {
    stop(); // 终止之前的动画
    isStopped = false;
    isPaused = false;
    stepMode = !!stepByStep; //把任何值强制转换为布尔值

    for (const step of steps) {
      if (isStopped) break;

      // 动态读取当前速度, 等式一定为真，但为了健壮性：万一以后有其他调用方式传入了数字，它也能正常工作
      const speed = typeof speedFn === 'function' ? speedFn() : speedFn;

      // 更新数组数据
      values = step.array.slice();

      // 根据步骤类型设置柱子高亮
      if (step.type === 'compare') {
        // 重置非已排序的柱子，保留绿色 SORTED 柱子
        for (let k = 0; k < states.length; k++) {
          if (states[k] !== STATE.SORTED) states[k] = STATE.DEFAULT;
        }
        states[step.i] = STATE.COMPARING;
        states[step.j] = STATE.COMPARING;
        needsDraw = true;
        await delay(speed);
        if (states[step.i] === STATE.COMPARING) states[step.i] = STATE.DEFAULT;
        if (states[step.j] === STATE.COMPARING) states[step.j] = STATE.DEFAULT;
        needsDraw = true;
      } else if (step.type === 'swap') {
        // 重置非已排序的柱子，保留绿色 SORTED 柱子
        for (let k = 0; k < states.length; k++) {
          if (states[k] !== STATE.SORTED) states[k] = STATE.DEFAULT;
        }
        states[step.i] = STATE.SWAPPING;
        states[step.j] = STATE.SWAPPING;
        needsDraw = true;
        await delay(speed);
        if (states[step.i] === STATE.SWAPPING) states[step.i] = STATE.DEFAULT;
        if (states[step.j] === STATE.SWAPPING) states[step.j] = STATE.DEFAULT;
        needsDraw = true;
      } else if (step.type === 'mark') {
        // 标记为已排序（持续绿色）
        states[step.i] = STATE.SORTED;
        needsDraw = true;
        await delay(Math.max(1, speed / 4));
      }
    }

    // 全部标记为已排序
    if (!isStopped) {
      states = new Array(values.length).fill(STATE.SORTED);
      needsDraw = true;
    }

    stepMode = false;
    if (onDone && !isStopped) onDone();
  }

  /** 暂停动画 */
  function pause() {
    isPaused = true;
  }

  /** 继续动画 */
  function resume() {
    isPaused = false;
    if (resumeCallback) { //检查变量里有无函数
      resumeCallback(); //调用函数
      resumeCallback = null; //再次清空(双重保险)
    }
  }

  /** 单步模式下前进一步 */
  function advanceStep() {
    if (stepResolve) {
      stepResolve();
      stepResolve = null;
    }
  }

  /** 是否处于单步模式 */
  function isStepMode() { return stepMode; }

  /** 终止动画 */
  function stop() { //把所有 状态、定时器、异步 全部清理干净，解决阻塞点
    isStopped = true;
    stepMode = false;
    if (isPaused) resume(); // 先解除暂停再终止
    if (stepResolve) { stepResolve(); stepResolve = null; }
    if (playbackTimer) {
      clearTimeout(playbackTimer);
      playbackTimer = null;
    }
  }

  function isRunning() { return !isStopped && (playbackTimer !== null || stepMode); }
  function is_paused() { return isPaused; }

  /**
   * 延时函数
   *
   * - 普通模式：等待 speed ms
   * - 暂停模式：等待 resume()
   * - 单步模式：等待 advanceStep() 被调用
   * - 终止时立即 resolve
   */
  function delay(ms) {
    return new Promise((resolve) => {
      const go = () => {
        if (isStopped) { resolve(); return; }
        if (stepMode) { //单步模式：等待用户点击"单步"按钮，Promise卡住
          stepResolve = () => {
            stepResolve = null;
            //时间到 → resolve 执行 → Promise 结束 → await 继续跑后面代码
            //返回值是一个正整数，表示由 setTimeout() 调用创建的定时器的标识符
            playbackTimer = setTimeout(resolve, ms);
          };
        } else if (isPaused) { //暂停模式，Promise卡住
          //将箭头函数赋值全局变量，resume()执行时回调
          resumeCallback = () => {
            //避免用户连续点 “恢复” 按钮，多次调用 resumeCallback()
            resumeCallback = null;
            //如果直接 setTimeout (resolve, ms)，就做不到 “恢复之后还能再次暂停”
            playbackTimer = setTimeout(go, ms);
          };
        } else { //普通模式
          playbackTimer = setTimeout(resolve, ms);
        }
      };
      go();
    });
  }

  // 公开 API
  return { init, setValues, setState, clearStates, redraw, resizeCanvas,
           playSteps, pause, resume, advanceStep, isStepMode, stop, isRunning, is_paused };
})();
