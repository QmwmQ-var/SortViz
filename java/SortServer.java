import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * SortServer — HTTP 服务器
 * ============================================================
 * <p>
 * 【职责】
 * 1. 提供静态文件服务（HTML、CSS、JS）
 * 2. 提供 REST API 供前端调用
 * <p>
 * 【模块协作】
 * 浏览器 ←HTTP→ SortServer ←调用→ SortEngine
 * ↑ 静态文件
 * ↑ index.html / css / js
 * <p>
 * 【API 一览】
 * GET  /api/algorithms              — 返回算法名称列表
 * POST /api/sort                    — 执行内置排序算法
 * 请求体: {"algorithm":"bubble", "array":[5,3,8,1,9]}
 * 响应:   {"steps":[...]}
 * POST /api/custom                  — 编译并执行用户的自定义算法
 * 请求体: {"code":"...", "array":[5,3,8,1,9]}
 * 响应:   {"steps":[...], "error":"..."}
 * <p>
 * 【启动方式】
 * javac -encoding UTF-8 java/*.java
 * java SortServer
 * 然后打开 http://localhost:8080
 * ============================================================
 */
public class SortServer {

    private static final int PORT = 8080;

    // 自定义算法文件存放目录（项目根目录下的 java/）
    private static final Path CUSTOM_DIR = Paths.get("java").toAbsolutePath().normalize();

    // MIME 类型映射(/maɪm/)，告诉浏览器是什么格式的文件
    private static final Map<String, String> MIME = new HashMap<>();

    static {//节省内存
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("js", "application/javascript; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("svg", "image/svg+xml");
        MIME.put("ico", "image/x-icon");
    }

    //——+——+——+——+——+——+——运行主函数——+——+——+——+——+——+——
    public static void main(String[] args) throws IOException {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (BindException e) {
            System.err.println("错误：端口 " + PORT + " 已被占用");
            System.err.println("请先关闭已运行的 SortServer 实例，或执行:");
            System.err.println("  pkill -f SortServer");
            System.exit(1);
            return;
        }
        server.setExecutor(Executors.newFixedThreadPool(4));

        // 注册路由
        //getRequestURI().getPath()	完整的请求路径 /api/sort?debug=true 获取用户请求的具体资源
        //getHttpContext().getPath() 路由的匹配路径 /api/ 获取哪个路由接住了请求
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new FileHandler());

        server.start();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   SortViz Server                     ║");
        System.out.println("║   http://localhost:" + PORT + "              ║");
        System.out.println("║   已注册 " + SortEngine.ALGORITHM_NAMES.length + " 个排序算法                   ║");
        System.out.println("╚══════════════════════════════════════╝");

        // 正常退出时释放端口
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            System.out.println("服务器已关闭");
        }));
    }

    // ========================================================
    // API 处理器
    // ========================================================
    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            // CORS 头
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            //告诉浏览器：请求时，允许发送 Content-Type 这个头
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            //当 JavaScript 发送跨域请求时，浏览器会先发一个 OPTIONS 请求，
            //询问服务器"你支持哪些方法？允许哪些头？"，服务器回复后，浏览器才发送真正的请求
            if ("OPTIONS".equalsIgnoreCase(method)) { //比较&不区分大小写
                ex.sendResponseHeaders(204, -1);
                return;
            }

            try {
                switch (path) {
                    case "/api/algorithms" -> handleListAlgorithms(ex);
                    case "/api/sort" -> handleSort(ex);
                    case "/api/custom" -> handleCustom(ex);
                    case "/api/custom/list"   -> handleListCustom(ex);
                    case "/api/custom/save"   -> handleSaveCustom(ex);
                    case "/api/custom/delete" -> handleDeleteCustom(ex);
                    default -> sendJson(ex, 404, "{\"error\":\"Not Found\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                //escapeJson:转义错误消息中的特殊字符（如双引号）
                //500 Internal Server Error（服务器内部错误）
                sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        /**
         * GET /api/algorithms — 返回所有算法名称
         * 动态返回，前端不需要硬编码算法名称，添加新算法时只改后端即可
         */
        private void handleListAlgorithms(HttpExchange ex) throws IOException {
            StringBuilder json = new StringBuilder("{\"algorithms\":[");
            for (int i = 0; i < SortEngine.ALGORITHM_NAMES.length; i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(SortEngine.ALGORITHM_NAMES[i]).append("\"");
            }
            json.append("]}");
            sendJson(ex, 200, json.toString());
        }

        /**
         * POST /api/sort — 执行内置排序算法
         */
        private void handleSort(HttpExchange ex) throws IOException {
            Map<String, Object> body = parseBody(ex);

            String algo = (String) body.getOrDefault("algorithm", "bubble");
            @SuppressWarnings("unchecked") //抑制编译器警告
            List<Number> raw = (List<Number>) body.get("array");
            if (raw == null || raw.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"array is required\"}");
                return;
            }

            //:: 方法引用操作符（表示"调用这个类的方法"）
            //等价于.mapToInt(n -> n.intValue())...
            int[] array = raw.stream().mapToInt(Number::intValue).toArray();
            List<SortEngine.SortStep> steps = SortEngine.execute(algo, array);

            if (steps == null) {
                sendJson(ex, 404, "{\"error\":\"algorithm not found: " + algo + "\"}");
            } else {
                sendJson(ex, 200, stepsToJson(steps));
            }
        }

        /**
         * POST /api/custom — 编译并执行用户自定义算法
         */
        private void handleCustom(HttpExchange ex) throws IOException {
            Map<String, Object> body = parseBody(ex);

            String code = (String) body.get("code");
            if (code == null || code.trim().isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"code is required\"}");
                return;
            }

            @SuppressWarnings("unchecked") //抑制编译器警告
            List<Number> raw = (List<Number>) body.get("array");
            if (raw == null || raw.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"array is required\"}");
                return;
            }

            int[] array = raw.stream().mapToInt(Number::intValue).toArray();
            List<SortEngine.SortStep> steps = SortEngine.executeCustom(code.trim(), array);

            if (steps == null) {
                //422 Unprocessable Entity(请求格式正确（语法无误），但服务器无法处理其中的语义内容)
                sendJson(ex, 422, "{\"error\":\"编译失败，请检查代码\"}");
            } else {
                sendJson(ex, 200, stepsToJson(steps));
            }
        }

        // ========================================================
        // 自定义算法文件管理 — 固定文件 CustomSort.java
        // ========================================================

        private static final String CUSTOM_FILE = "CustomSort.java";

        /**
         * GET /api/custom/list — 返回 CustomSort.java 的内容（如存在）
         */
        private void handleListCustom(HttpExchange ex) throws IOException {
            Path file = CUSTOM_DIR.resolve(CUSTOM_FILE).normalize();
            if (!file.startsWith(CUSTOM_DIR) || !Files.exists(file)) {
                sendJson(ex, 200, "{\"algorithms\":[]}");
                return;
            }

            String content = Files.readString(file, StandardCharsets.UTF_8);
            String displayName = "CustomSort";
            String code = content;

            // 解析第一行元数据: // @sortviz-name <显示名称>
            if (content.startsWith("// @sortviz-name ")) {
                int nl = content.indexOf('\n');
                if (nl > 0) {
                    displayName = content.substring("// @sortviz-name ".length(), nl).trim();
                    code = content.substring(nl + 1);
                }
            }

            String json = "{\"algorithms\":[{\"id\":\"custom\",\"name\":\""
                    + escapeJson(displayName) + "\",\"code\":\""
                    + escapeJson(code) + "\"}]}";
            sendJson(ex, 200, json);
        }

        /**
         * POST /api/custom/save — 保存代码到 CustomSort.java
         */
        private void handleSaveCustom(HttpExchange ex) throws IOException {
            Map<String, Object> body = parseBody(ex);

            String name = (String) body.get("name");
            String code = (String) body.get("code");

            if (code == null || code.trim().isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"code is required\"}");
                return;
            }

            Path file = CUSTOM_DIR.resolve(CUSTOM_FILE).normalize();
            if (!file.startsWith(CUSTOM_DIR)) {
                sendJson(ex, 400, "{\"error\":\"Invalid path\"}");
                return;
            }

            String displayName = (name != null && !name.trim().isEmpty()) ? name.trim() : "CustomSort";
            String fileContent = "// @sortviz-name " + displayName + "\n" + code;
            Files.createDirectories(CUSTOM_DIR);
            Files.writeString(file, fileContent, StandardCharsets.UTF_8);

            sendJson(ex, 200, "{\"ok\":true}");
        }

        /**
         * DELETE /api/custom/delete — 删除 CustomSort.java
         */
        private void handleDeleteCustom(HttpExchange ex) throws IOException {
            Path file = CUSTOM_DIR.resolve(CUSTOM_FILE).normalize();
            if (!file.startsWith(CUSTOM_DIR) || !Files.exists(file)) {
                sendJson(ex, 404, "{\"error\":\"File not found\"}");
                return;
            }

            Files.delete(file);
            sendJson(ex, 200, "{\"ok\":true}");
        }

        // ---- 工具方法 ----
        /**
         * 将步骤列表转为 JSON
         */
        private String stepsToJson(List<SortEngine.SortStep> steps) {
            StringBuilder sb = new StringBuilder("{\"steps\":[");
            for (int s = 0; s < steps.size(); s++) {
                if (s > 0) sb.append(",");
                SortEngine.SortStep step = steps.get(s);
                sb.append("{\"type\":\"").append(step.type)
                        .append("\",\"i\":").append(step.i)
                        .append(",\"j\":").append(step.j)
                        .append(",\"array\":[");
                for (int k = 0; k < step.array.length; k++) {
                    if (k > 0) sb.append(",");
                    sb.append(step.array[k]);
                }
                sb.append("]}");
            }
            sb.append("]}");
            return sb.toString();
        }

        /**
         * 解析请求体
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> parseBody(HttpExchange ex) throws IOException {
            byte[] bytes = ex.getRequestBody().readAllBytes();
            //将字节数组 bytes 按照 UTF-8 字符集解码成字符串
            String body = new String(bytes, StandardCharsets.UTF_8).trim();
            if (body.isEmpty()) return new HashMap<>();
            return (Map<String, Object>) parseJson(body);
        }

        /**
         * 发送 JSON 响应
         */
        private void sendJson(HttpExchange ex, int code, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        // 简易 JSON 解析（不依赖第三方库），把 JSON 字符串转换成 Java 对象
        private Object parseJson(String s) {
            s = s.trim();
            if (s.startsWith("{")) return parseObj(s);//对象
            if (s.startsWith("[")) return parseArr(s);//数组
            if (s.startsWith("\"")) return unescapeJson(s.substring(1, s.length() - 1));//字符串
            if (s.equals("true")) return true;//布尔真
            if (s.equals("false")) return false;//布尔假
            if (s.equals("null")) return null;//空
            try {//Number
                return s.contains(".") ? Double.parseDouble(s) : Long.parseLong(s);
            } catch (NumberFormatException e) {
                return s;
            }
        }
        // 解析对象
        private Map<String, Object> parseObj(String s) {
            Map<String, Object> map = new LinkedHashMap<>(); //按照插入顺序保存
            s = s.substring(1, s.length() - 1).trim();
            if (s.isEmpty()) return map;
            for (String pair : split(s, ',')) {
                int ci = pair.indexOf(':');
                if (ci < 0) continue;
                String key = pair.substring(0, ci).trim();
                // JSON语法上：Key必须是简单字符串，而Value可以是任何合法的JSON数据类型
                if (key.startsWith("\"")) key = key.substring(1, key.length() - 1);
                map.put(key, parseJson(pair.substring(ci + 1).trim()));
            }
            return map;
        }
        // 解析数组
        private List<Object> parseArr(String s) {
            List<Object> list = new ArrayList<>();
            s = s.substring(1, s.length() - 1).trim();
            if (s.isEmpty()) return list;
            for (String item : split(s, ',')) list.add(parseJson(item.trim()));
            return list;
        }
        // 分割JSON
        private List<String> split(String s, char delimiter) {
            List<String> parts = new ArrayList<>();
            int depth = 0, start = 0; //嵌套深度; 在字符串中的起始位置
            boolean inStr = false; //初始化：不在字符串内部
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                // 检测字符串边界
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\'))
                    inStr = !inStr;

                if (!inStr) {
                    if (c == '{' || c == '[') depth++;
                    else if (c == '}' || c == ']') depth--;
                }
                if (c == delimiter && depth == 0 && !inStr) {
                    parts.add(s.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(s.substring(start));
            return parts;
        }

        /**
         * 转义(可能出现的) JSON 特殊字符
         */
        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }

        /**
         * 将 JSON 字符串中的转义序列还原为真实字符
         */
        private String unescapeJson(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                // c 是普通字符 \ 时...
                if (c == '\\' && i + 1 < s.length()) {
                    char next = s.charAt(++i);
                    switch (next) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        default -> sb.append('\\').append(next);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    // ========================================================
    // 静态文件处理器
    // ========================================================

    static class FileHandler implements HttpHandler {
        //定义服务器的根目录(绝对路径)
        //. -> D:\Code\sort demo\. -> D:\Code\sort demo == root
        private final Path root = Paths.get(".").toAbsolutePath().normalize();

        @Override
        public void handle(HttpExchange ex) throws IOException {
            // 将根路径 / 映射到 /index.html
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";

            // 移除路径中可能存在的危险字符（去除开头的 / 后）
            String cleanPath = path.substring(1)
                    .replace('\\', '/')          // 统一将 \ 替换为 /，防止 Windows 绕过
                    .replaceAll("/{2,}", "/");   // 将多个 / 合并为一个，防止 // 绕过

            // 再次检查路径中是否包含 ".."（双重保险）
            if (cleanPath.contains("..")) {
                String msg = "404 Not Found";
                ex.sendResponseHeaders(404, msg.length());
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(msg.getBytes());
                }
                return;
            }

            Path file = root.resolve(cleanPath).normalize();
            if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) {
                String msg = "404 Not Found";
                ex.sendResponseHeaders(404, msg.length());
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(msg.getBytes());
                }
                return;
            }

            String ext = getExt(path);
            String mime = MIME.getOrDefault(ext, "application/octet-stream");
            byte[] data = Files.readAllBytes(file);

            ex.getResponseHeaders().set("Content-Type", mime);
            //开发时修改了 CSS/JS,刷新浏览器立即生效,不需要手动清空浏览器缓存
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }

        private String getExt(String path) {
            int dot = path.lastIndexOf('.');
            return dot > 0 ? path.substring(dot + 1).toLowerCase() : "";
        }
    }
}
