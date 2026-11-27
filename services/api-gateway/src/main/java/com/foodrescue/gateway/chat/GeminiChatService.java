package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodrescue.gateway.chat.ChatDtos.ChatRequest;
import com.foodrescue.gateway.chat.ChatDtos.ChatResponse;
import com.foodrescue.gateway.chat.ChatDtos.ChatTurn;
import com.foodrescue.gateway.chat.ChatDtos.Suggestion;
import com.foodrescue.gateway.chat.ChatToolExecutor.UserSession;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Orchestrates multi-turn Gemini function calling against customer-allowed FoodRescue APIs. */
@Service
public class GeminiChatService {
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GeminiClient geminiClient;
    private final ChatToolExecutor toolExecutor;
    private final ObjectMapper mapper;

    public GeminiChatService(GeminiClient geminiClient, ChatToolExecutor toolExecutor, ObjectMapper mapper) {
        this.geminiClient = geminiClient;
        this.toolExecutor = toolExecutor;
        this.mapper = mapper;
    }

    public Mono<ChatResponse> chat(String authorization, ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return Mono.error(new ChatException(400, "VALIDATION_ERROR", "Vui lòng nhập nội dung câu hỏi"));
        }
        String userMessage = request.message().trim();
        if (userMessage.length() > MAX_MESSAGE_LENGTH) {
            return Mono.error(new ChatException(400, "VALIDATION_ERROR",
                    "Tin nhắn tối đa " + MAX_MESSAGE_LENGTH + " ký tự"));
        }

        return toolExecutor.resolveSession(authorization, request.latitude(), request.longitude())
                .flatMap(session -> {
                    ArrayNode contents = buildInitialContents(request.history(), userMessage);
                    ArrayNode tools = toolExecutor.buildToolDeclarations(session);
                    ObjectNode systemInstruction = buildSystemInstruction(session);
                    Set<String> toolsUsed = Collections.synchronizedSet(new LinkedHashSet<>());
                    List<Suggestion> suggestions = Collections.synchronizedList(new ArrayList<>());

                    return runTurn(session, systemInstruction, contents, tools, toolsUsed, suggestions, 0);
                });
    }

    private Mono<ChatResponse> runTurn(UserSession session, ObjectNode systemInstruction, ArrayNode contents,
                                       ArrayNode tools, Set<String> toolsUsed, List<Suggestion> suggestions, int round) {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("systemInstruction", systemInstruction);
        payload.set("contents", contents);
        if (round < MAX_TOOL_ROUNDS) {
            payload.set("tools", tools);
        }
        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("temperature", 0.3);

        return geminiClient.generateContent(payload).flatMap(reply -> {
            JsonNode candidate = reply.body().path("candidates").path(0);
            JsonNode modelContent = candidate.path("content");
            List<JsonNode> functionCalls = extractFunctionCalls(modelContent.path("parts"));

            if (functionCalls.isEmpty() || round >= MAX_TOOL_ROUNDS) {
                String text = extractText(modelContent.path("parts"));
                if (text.isBlank()) {
                    text = "Xin lỗi, tôi chưa tổng hợp được câu trả lời phù hợp lúc này. Bạn hãy thử hỏi lại cụ thể hơn nhé.";
                }
                return Mono.just(new ChatResponse(
                        text,
                        new ArrayList<>(toolsUsed),
                        reply.model(),
                        session.isCustomer(),
                        new ArrayList<>(suggestions)));
            }

            // Preserve modelContent verbatim (including thoughtSignature & call id) as required by Gemini 3.
            contents.add(modelContent);

            return Flux.fromIterable(functionCalls)
                    .flatMapSequential(call -> {
                        String toolName = call.path("name").asText("");
                        toolsUsed.add(toolName);
                        return toolExecutor.execute(session, toolName, call.path("args"), suggestions)
                                .map(result -> buildFunctionResponsePart(toolName, call.path("id").asText(null), result));
                    })
                    .collectList()
                    .flatMap(responseParts -> {
                        ObjectNode toolResponseTurn = mapper.createObjectNode();
                        toolResponseTurn.put("role", "user");
                        ArrayNode partsArray = toolResponseTurn.putArray("parts");
                        responseParts.forEach(partsArray::add);
                        contents.add(toolResponseTurn);
                        return runTurn(session, systemInstruction, contents, tools, toolsUsed, suggestions, round + 1);
                    });
        });
    }

    private ObjectNode buildFunctionResponsePart(String toolName, String callId, JsonNode result) {
        ObjectNode part = mapper.createObjectNode();
        ObjectNode fnResp = part.putObject("functionResponse");
        fnResp.put("name", toolName);
        if (callId != null && !callId.isBlank()) {
            fnResp.put("id", callId);
        }
        ObjectNode response = fnResp.putObject("response");
        response.set("result", result);
        return part;
    }

    private ObjectNode buildSystemInstruction(UserSession session) {
        String nowText = LocalDateTime.now(VIETNAM_ZONE).format(DATE_FORMAT);
        String userContext = session.isCustomer()
                ? String.format("Khách hàng đã đăng nhập (tên: %s, tọa độ: %s, %s). Có thể tra cứu đơn hàng, chi tiêu, giỏ hàng và hồ sơ của chính khách này.",
                session.fullName().isBlank() ? "Khách hàng" : session.fullName(),
                session.latitude() != null ? session.latitude() : "chưa có",
                session.longitude() != null ? session.longitude() : "chưa có")
                : session.authenticated()
                ? "Người dùng đang đăng nhập với vai trò " + session.role() + " (không phải tài khoản mua hàng CUSTOMER; chỉ dùng được công cụ công khai về cửa hàng)."
                : "Khách chưa đăng nhập. Chỉ xem được thông tin cửa hàng công khai; nếu khách hỏi đơn hàng, giỏ hàng hoặc chi tiêu cá nhân, hãy mời khách đăng nhập.";

        String prompt = """
                Bạn là trợ lý AI chính thức của FoodRescue — nền tảng giải cứu thực phẩm cuối ngày tại Việt Nam.
                Thời gian hiện tại (giờ Việt Nam): %s.
                Trạng thái phiên: %s

                Phạm vi hỗ trợ (BẮT BUỘC):
                - CHỈ hỗ trợ các chủ đề liên quan đến FoodRescue: tìm cửa hàng, xem thông tin/đánh giá cửa hàng, tra cứu đơn hàng, giỏ hàng, thống kê chi tiêu cá nhân, cách sử dụng nền tảng (đăng ký, quên mật khẩu OTP, đặt túi, thanh toán, nhận hàng bằng mã) và thông tin giảm lãng phí thực phẩm trên FoodRescue.
                - Nếu người dùng hỏi bất kỳ chủ đề nào NGOÀI phạm vi FoodRescue (ví dụ: toán học, định lý, lập trình, giải bài tập, dịch thuật, lịch sử, thời tiết, tin tức, tư vấn chung không liên quan...), hãy TỪ CHỐI lịch sự trong 1–2 câu ngắn gọn (không giải thích hay trả lời nội dung ngoài lề đó), rồi gợi ý các việc bạn hỗ trợ được trên FoodRescue.

                Quy tắc trả lời:
                1. Trả lời bằng tiếng Việt tự nhiên, ngắn gọn, thân thiện, định dạng số tiền theo VNĐ (ví dụ 95.000 đ).
                2. Chỉ dùng văn bản thông thường (viết hoa đầu câu đúng chính tả), gạch đầu dòng (`-`) và in đậm (`**...**`). Tuyệt đối không dùng LaTeX (`$...$`, `$$...$$`), code block hay bảng Markdown.
                3. Chỉ sử dụng số liệu, tên cửa hàng, mã đơn và trạng thái từ dữ liệu công cụ (tools) trả về. Tuyệt đối không tự bịa số liệu hoặc đoán thông tin không có.
                4. Với câu hỏi về tổng chi tiêu/số đơn theo tháng hoặc năm, hãy gọi `analyze_my_orders` (truyền đúng `month`, `year` theo thời gian hiện tại nếu khách hỏi "tháng này", "năm nay") và dùng đúng con số tổng hợp do công cụ trả về.
                5. Giải thích rõ sự khác biệt nếu có giữa đơn đã hoàn thành (`COMPLETED`), đơn đang chờ nhận (`CONFIRMED`), đơn chờ thanh toán (`PENDING_PAYMENT`) và đơn đã hủy (`CANCELLED`).
                6. Nếu khách hỏi tìm túi giải cứu/món ăn cụ thể còn tồn kho theo thời gian thực mà chưa có tool tìm túi công khai, hãy trung thực báo hiện chatbot tra cứu được danh sách cửa hàng, đánh giá quán và đơn hàng cá nhân, đồng thời gợi ý khách xem mục "Túi giải cứu" trên website.
                """.formatted(nowText, userContext);

        ObjectNode sys = mapper.createObjectNode();
        sys.putArray("parts").addObject().put("text", prompt);
        return sys;
    }

    private ArrayNode buildInitialContents(List<ChatTurn> history, String userMessage) {
        ArrayNode contents = mapper.createArrayNode();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                ChatTurn turn = history.get(i);
                if (turn == null || turn.text() == null || turn.text().isBlank()) continue;
                String role = "model".equalsIgnoreCase(turn.role()) ? "model" : "user";
                String text = turn.text().trim();
                if (text.length() > MAX_MESSAGE_LENGTH) {
                    text = text.substring(0, MAX_MESSAGE_LENGTH);
                }
                ObjectNode msg = contents.addObject();
                msg.put("role", role);
                msg.putArray("parts").addObject().put("text", text);
            }
        }
        ObjectNode latest = contents.addObject();
        latest.put("role", "user");
        latest.putArray("parts").addObject().put("text", userMessage);
        return contents;
    }

    private List<JsonNode> extractFunctionCalls(JsonNode parts) {
        List<JsonNode> calls = new ArrayList<>();
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("functionCall") && !part.get("functionCall").isNull()) {
                    calls.add(part.get("functionCall"));
                }
            }
        }
        return calls;
    }

    private String extractText(JsonNode parts) {
        if (parts == null || !parts.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) continue;
            if (part.has("text")) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(part.get("text").asText(""));
            }
        }
        return sb.toString().trim();
    }
}
