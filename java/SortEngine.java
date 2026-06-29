import java.util.*;

/**
 * ============================================================
 * SortEngine — 排序引擎（项目核心）
 * ============================================================
 * <p>
 * 【职责】
 *   1. 实现所有排序算法（冒泡、选择、插入、快速、归并...）
 *   2. 记录排序过程中的每一步操作（比较、交换、标记）
 *   3. 编译并执行用户提交的自定义 Java 排序算法
 * <p>
 * 【模块协作】
 *   SortEngine ← 被 SortServer 调用
 *     ↓ 产生
 *   List<SortStep> → 转成 JSON → 前端 Canvas 逐帧动画
 * <p>
 * 【对于自定义算法】
 *   用户在浏览器中编写 Java 代码 → 前端将代码 POST 到服务器
 *   → SortEngine 使用 javax.tools.JavaCompiler 动态编译
 *   → 反射加载并执行 → 返回步骤列表给前端
 * <p>
 *   用户代码需要访问两个对象：
 *     array  (int[])   — 待排序的数组
 *     rec    (StepRecorder) — 记录操作，提供 compare/swap/mark 方法
 * ============================================================
 */
public class SortEngine {

    // ========================================================
    // 1. 数据结构：排序步骤
    // ========================================================

    /**
     * SortStep — 排序过程中的一次原子操作
     * 前端根据 type 来决定如何渲染动画：
     *   "compare" → 黄色高亮两个柱子
     *   "swap"    → 红色高亮并交换两个柱子
     *   "mark"    → 绿色标记一个柱子为已排序
     */
    public static class SortStep {
        public String type;      // 操作类型："compare" / "swap" / "mark"
        public int i;            // 第一个索引（mark 时只用这一个）
        public int j;            // 第二个索引（compare/swap 用，mark 时为 -1）
        public int[] array;      // 当前数组快照（本次操作之后的数组状态）

        public SortStep(String type, int i, int j, int[] array) {
            this.type = type;
            this.i = i;
            this.j = j;
            this.array = array.clone(); // 防御性拷贝，避免外部修改
        }
    }

    // ========================================================
    // 2. 步骤记录器 — 算法通过它来"汇报"每一步
    // ========================================================

    /**
     * StepRecorder — 排序算法与可视化之间的桥梁
     * 构造List<SortStep>
     * 每个排序算法在执行过程中，通过调用 rec.compare()、
     * rec.swap()、rec.mark() 来记录操作。这些调用产生的
     * SortStep 对象最终会通过网络发送给前端 Canvas 渲染。
     * 这也是自定义算法中用户需要使用的 rec 对象的类型。
     */
    public static class StepRecorder {
        private final List<SortStep> steps = new ArrayList<>();
        private final int[] array; // 指向正在被排序的数组的引用

        public StepRecorder(int[] array) {
            this.array = array;
        }

        /** 记录一次比较操作（前端会用黄色高亮 i 和 j） */
        public void compare(int i, int j) {
            steps.add(new SortStep("compare", i, j, array));
        }

        /** 执行交换并记录（前端会用红色高亮并交换两个柱子） */
        public void swap(int i, int j) {
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
            steps.add(new SortStep("swap", i, j, array));
        }

        /** 标记索引 i 为已排序（前端会用绿色标记该柱子） */
        public void mark(int i) {
            steps.add(new SortStep("mark", i, -1, array));
        }

        public List<SortStep> getSteps() { return steps; }
    }

    // ========================================================
    // 3. 内置排序算法
    // ========================================================

    // 算法名称列表（供前端下拉菜单使用）
    public static final String[] ALGORITHM_NAMES = {
        "bubble", "selection", "insertion",
        "quick", "merge", "heap", "shell"
    };

    /**
     * 根据算法名称执行排序，返回所有步骤
     * @param name   算法名称，如 "bubble", "quick" 等
     * @param array  待排序的数组
     * @return       排序过程中的所有步骤，如果算法不存在则返回 null
     */
    public static List<SortStep> execute(String name, int[] array) {
        int[] arr = array.clone(); // 不修改原始数组
        StepRecorder rec = new StepRecorder(arr);

        switch (name) {
            case "bubble":    bubbleSort(arr, rec);    break;
            case "selection": selectionSort(arr, rec); break;
            case "insertion": insertionSort(arr, rec); break;
            case "quick":     quickSort(arr, rec);     break;
            case "merge":     mergeSort(arr, rec);     break;
            case "heap":      heapSort(arr, rec);      break;
            case "shell":     shellSort(arr, rec);     break;
            default: return null;
        }
        return rec.getSteps();
    }

    // ---- 冒泡排序 ----
    // 思想：重复遍历数组，比较相邻元素，把大的往后"冒泡"
    static void bubbleSort(int[] a, StepRecorder rec) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - 1 - i; j++) {
                rec.compare(j, j + 1);         // 比较相邻两个
                if (a[j] > a[j + 1]) {
                    rec.swap(j, j + 1);        // 大的往后换
                    swapped = true;
                }
            }
            rec.mark(n - 1 - i);               // 末尾元素已就位
            if (!swapped) {                    // 本轮没有交换 → 全部有序
                for (int k = 0; k < n - 1 - i; k++) rec.mark(k);
                break;
            }
        }
    }

    // ---- 选择排序 ----
    // 思想：每轮从未排序部分选出最小值，放到已排序末尾
    static void selectionSort(int[] a, StepRecorder rec) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                rec.compare(j, minIdx);         // 找最小值
                if (a[j] < a[minIdx]) minIdx = j;
            }
            if (minIdx != i) rec.swap(i, minIdx); // 把最小值换到前面
            rec.mark(i);                          // i 位置已完成
        }
        rec.mark(n - 1);
    }

    // ---- 直接插入排序 ----
    // 思想：像整理扑克牌，把每个元素插入到前面已排序部分的正确位置
    static void insertionSort(int[] a, StepRecorder rec) {
        int n = a.length;
        rec.mark(0);                              // 第一个元素视为已排序
        for (int i = 1; i < n; i++) {
            int j = i;
            while (j > 0) {
                rec.compare(j, j - 1);            // 和前一个比较
                if (a[j] < a[j - 1]) {
                    rec.swap(j, j - 1);           // 比前一个小就往前换
                    j--;
                } else break;
            }
            rec.mark(i);                          // i 插入完成
        }
    }

    // ---- 快速排序 ----
    // 思想：选基准值，小的放左边，大的放右边，递归处理两半
    static void quickSort(int[] a, StepRecorder rec) {
        qSort(a, rec, 0, a.length - 1);
    }
    private static void qSort(int[] a, StepRecorder rec, int lo, int hi) {
        if (lo >= hi) { if (lo == hi) rec.mark(lo); return; }
        int p = partition(a, rec, lo, hi);        // 分区
        rec.mark(p);                              // 基准值就位
        qSort(a, rec, lo, p - 1);           // 递归左边
        qSort(a, rec, p + 1, hi);           // 递归右边
    }

    static int partition(int[] a, StepRecorder rec, int lo, int hi) {
        int pivot = a[hi];                        // 选最后一个元素为基准
        int i = lo;
        for (int j = lo; j < hi; j++) {
            rec.compare(j, hi);                   // 和基准比较
            if (a[j] <= pivot) {
                if (i != j) rec.swap(i, j);       // 比基准小的换到左边
                i++;
            }
        }
        if (i != hi) rec.swap(i, hi);             // 基准值放到正确位置
        return i;
    }

    // ---- 归并排序 ----
    // 思想：把数组分成两半，分别排序，再合并两个有序子数组
    static void mergeSort(int[] a, StepRecorder rec) {
        _mergeSort(a, rec, 0, a.length - 1);
    }
    private static void _mergeSort(int[] a, StepRecorder rec, int lo, int hi) {
        if (lo >= hi) { if (lo == hi) rec.mark(lo); return; }
        int mid = (lo + hi) / 2;
        _mergeSort(a, rec, lo, mid);              // 排左边
        _mergeSort(a, rec, mid + 1, hi);          // 排右边
        merge(a, rec, lo, mid, hi);               // 合并
    }

    static void merge(int[] a, StepRecorder rec, int lo, int mid, int hi) {
        int[] left  = Arrays.copyOfRange(a, lo, mid + 1);
        int[] right = Arrays.copyOfRange(a, mid + 1, hi + 1);
        int i = 0, j = 0, k = lo;
        while (i < left.length && j < right.length) {
            rec.compare(lo + i, mid + 1 + j);     // 比较左右两端
            a[k++] = (left[i] <= right[j]) ? left[i++] : right[j++];
            rec.mark(k - 1);                      // 该位置已填入正确值
        }
        while (i < left.length)  { a[k++] = left[i++];  rec.mark(k - 1); }
        while (j < right.length) { a[k++] = right[j++]; rec.mark(k - 1); }
    }

    // ---- 堆排序 ----
    // 思想：利用最大堆数据结构，反复取出堆顶（最大值）放到末尾
    static void heapSort(int[] a, StepRecorder rec) {
        int n = a.length;
        for (int i = n / 2 - 1; i >= 0; i--) heapify(a, rec, n, i); // 建堆
        for (int i = n - 1; i > 0; i--) {
            rec.swap(0, i);                       // 堆顶（最大）换到末尾
            rec.mark(i);
            heapify(a, rec, i, 0);                // 调整剩余元素成堆
        }
        rec.mark(0);
    }

    static void heapify(int[] a, StepRecorder rec, int size, int root) {
        int largest = root;
        int left  = 2 * root + 1;
        int right = 2 * root + 2;
        if (left  < size) { rec.compare(left,  largest); if (a[left]  > a[largest]) largest = left;  }
        if (right < size) { rec.compare(right, largest); if (a[right] > a[largest]) largest = right; }
        if (largest != root) {
            rec.swap(root, largest);              // 把大的浮上去
            heapify(a, rec, size, largest);       // 递归调整子树
        }
    }

    // ---- 希尔排序 ----
    // 思想：插入排序的改进版，先比较远距离元素，逐步缩小间隔
    static void shellSort(int[] a, StepRecorder rec) {
        int n = a.length;
        for (int gap = n / 2; gap > 0; gap /= 2) {
            for (int i = gap; i < n; i++) {
                for (int j = i; j >= gap; j -= gap) {
                    rec.compare(j, j - gap);
                    if (a[j] < a[j - gap]) rec.swap(j, j - gap);
                    else break;
                }
            }
        }
        for (int i = 0; i < n; i++) rec.mark(i); // 全部标记为有序
    }

    // ========================================================
    // 4. 自定义算法编译 & 执行
    // ========================================================

    /**
     * 编译并执行用户提交的自定义 Java 排序代码
     * 工作流程：
     *   1. 将用户代码包装成完整 Java 类
     *   2. 使用 javax.tools.JavaCompiler 编译
     *   3. 用自定义 ClassLoader 加载字节码
     *   4. 反射调用 sort() 方法
     *   5. 返回记录的步骤
     * 用户代码中可以使用的变量：
     *   array  (int[])        — 待排序的数组
     *   rec    (StepRecorder) — 步骤记录器
     *     rec.compare(i, j)   — 比较 array[i] 和 array[j]
     *     rec.swap(i, j)      — 交换 array[i] 和 array[j]
     *     rec.mark(i)         — 标记 array[i] 为已排序
     *
     * @param userCode  用户编写的 sort 方法体代码
     * @param array     待排序的数组
     * @return          排序步骤列表，编译失败则返回 null
     */
    public static List<SortStep> executeCustom(String userCode, int[] array) {
        try {
            // 1. 将用户代码包装成完整类
            String fullSource = wrapUserCode(userCode);

            // 2. 从源码中提取类名，编译失败或提取不到时回退 "CustomSort"
            String className = CompilerHelper.extractClassName(fullSource);

            // 3. 编译
            byte[] bytecode = CompilerHelper.compile(fullSource, className);
            if (bytecode == null) return null;

            // 4. 加载并执行
            int[] arr = array.clone();
            StepRecorder rec = new StepRecorder(arr);
            CompilerHelper.loadAndInvoke(bytecode, className, arr, rec);

            return rec.getSteps();
        } catch (Exception e) {
            System.err.println("自定义算法执行失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 把用户写的代码片段包装成完整的 Java 类
     */
    private static String wrapUserCode(String userCode) {
        return userCode;
    }
}

/**
 * CompilerHelper — 运行时Java代码编译器
 * 使用 JDK 自带的 javax.tools.JavaCompiler 在内存中编译源代码，
 * 然后用自定义 ClassLoader 加载编译后的字节码。
 * 注意：这需要 JDK 环境（非 JRE），因为需要 tools.jar / java.compiler 模块。
 */
class CompilerHelper {

    /**
     * 从 Java 源码中提取 public class 的类名
     * 匹配不到时回退 "CustomSort"
     */
    static String extractClassName(String source) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "public\\s+class\\s+(\\w+)");
        java.util.regex.Matcher m = p.matcher(source);
        if (m.find()) return m.group(1);
        return "CustomSort";
    }

    /**
     * 编译 Java 源代码（写到临时目录，用标准方式编译）
     *
     * @param source    完整的 Java 源代码字符串
     * @param className 类名（用于命名临时文件）
     * @return          编译后的字节码，失败返回 null
     */
    static byte[] compile(String source, String className) {
        try {
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                System.err.println("错误：需要 JDK 环境。请用 JDK 运行，而非 JRE。");
                return null;
            }

            // 创建临时目录
            java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("sortviz_");
            java.nio.file.Path srcFile = tmpDir.resolve(className + ".java");

            // 写入源文件
            java.nio.file.Files.writeString(srcFile, source, java.nio.charset.StandardCharsets.UTF_8);

            // 编译
            javax.tools.StandardJavaFileManager fm =
                compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8);
            fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT,
                java.util.Collections.singletonList(tmpDir.toFile()));

            Iterable<? extends javax.tools.JavaFileObject> units =
                fm.getJavaFileObjectsFromFiles(java.util.Collections.singletonList(srcFile.toFile()));

            javax.tools.JavaCompiler.CompilationTask task =
                compiler.getTask(null, fm, null, null, null, units);
            boolean ok = task.call();
            fm.close();

            if (ok) {
                // 读取编译出的 .class 文件
                java.nio.file.Path classFile = tmpDir.resolve(className + ".class");
                byte[] bytes = java.nio.file.Files.readAllBytes(classFile);

                // 清理临时文件
                java.nio.file.Files.walk(tmpDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignored) {} });

                return bytes;
            }

            // 清理
            java.nio.file.Files.walk(tmpDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignored) {} });
            return null;
        } catch (Exception e) {
            System.err.println("编译异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载编译好的字节码，通过反射调用 sort() 方法
     *
     * @param bytecode  编译后的 .class 字节码
     * @param array     待排序数组
     * @param rec       步骤记录器
     */
    static void loadAndInvoke(byte[] bytecode, String className, int[] array, SortEngine.StepRecorder rec) throws Exception {
        // 用自定义 ClassLoader 加载字节码
        Class<?> clazz = new ClassLoader(CompilerHelper.class.getClassLoader()) {
            public Class<?> define(byte[] bytes, String name) {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define(bytecode, className);

        // 反射调用 <className>.sort(array, rec)
        java.lang.reflect.Method method = clazz.getMethod("sort", int[].class, SortEngine.StepRecorder.class);
        method.invoke(null, array, rec);
    }
}
