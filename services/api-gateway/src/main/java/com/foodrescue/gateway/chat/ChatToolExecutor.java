package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodrescue.gateway.chat.ChatDtos.Suggestion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Executes customer-scoped and public microservice API calls on behalf of the current user (never using internal keys). */
@Component
public class ChatToolExecutor {
    private static final Duration SERVICE_TIMEOUT = Duration.ofSeconds(6);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String identityUrl;
    private final String storeUrl;
    private final String orderUrl;

    public ChatToolExecutor(
            WebClient.Builder webClientBuilder,
            ObjectMapper mapper,
            @Value("${app.health.identity-url}") String identityUrl,
            @Value("${app.health.store-url}") String storeUrl,
            @Value("${app.health.order-url}") String orderUrl) {
        this.webClient = webClientBuilder.build();
        this.mapper = mapper;
        this.identityUrl = trimSlash(identityUrl);
        this.storeUrl = trimSlash(storeUrl);
        this.orderUrl = trimSlash(orderUrl);
    }

    public Mono<UserSession> resolveSession(String authorization, Double reqLat, Double reqLon) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            return Mono.just(UserSession.anonymous(reqLat, reqLon));
        }
        return webClient.get()
                .uri(identityUrl + "/api/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .map(profile -> new UserSession(
                        true,
                        authorization,
                        profile.path("id").asLong(),
                        profile.path("role").asText(""),
                        profile.path("fullName").asText(""),
                        profile.path("email").asText(""),
                        profile.path("address").asText(null),
                        reqLat != null ? reqLat : nullableDouble(profile.get("latitude")),
                        reqLon != null ? reqLon : nullableDouble(profile.get("longitude")),
                        profile))
                .onErrorMap(ex -> {
                    if (ex instanceof WebClientResponseException responseEx) {
                        int status = responseEx.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            return new ChatException(401, "UNAUTHORIZED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
                        }
                    }
                    return new ChatException(503, "IDENTITY_UNAVAILABLE", "Không xác thực được tài khoản hiện tại");
                });
    }

    public ArrayNode buildToolDeclarations(UserSession session) {
        ArrayNode tools = mapper.createArrayNode();
        ObjectNode wrapper = tools.addObject();
        ArrayNode declarations = wrapper.putArray("functionDeclarations");

        declarations.add(declaration("search_stores",
                "Tìm danh sách cửa hàng FoodRescue đang hoạt động. Có thể lọc theo từ khóa tên/địa chỉ hoặc tọa độ gần nhất.",
                props -> {
                    props.putObject("keyword").put("type", "STRING").put("description", "Từ khóa tên cửa hàng hoặc khu vực, ví dụ: bánh mì, quận 1");
                    props.putObject("lat").put("type", "NUMBER").put("description", "Vĩ độ khách hàng (nếu có)");
                    props.putObject("lon").put("type", "NUMBER").put("description", "Kinh độ khách hàng (nếu có)");
                    props.putObject("radiusKm").put("type", "NUMBER").put("description", "Bán kính tìm kiếm tính bằng km");
                    props.putObject("limit").put("type", "INTEGER").put("description", "Số lượng kết quả tối đa (mặc định 8, tối đa 15)");
                }));

        declarations.add(declaration("get_store_detail",
                "Xem thông tin chi tiết của một cửa hàng theo storeId (mô tả, giờ mở cửa, giờ đóng cửa, số điện thoại, đánh giá).",
                props -> props.putObject("storeId").put("type", "INTEGER").put("description", "ID cửa hàng"),
                "storeId"));

        declarations.add(declaration("get_store_reviews",
                "Xem các đánh giá gần đây và điểm trung bình của một cửa hàng theo storeId.",
                props -> {
                    props.putObject("storeId").put("type", "INTEGER").put("description", "ID cửa hàng");
                    props.putObject("size").put("type", "INTEGER").put("description", "Số đánh giá muốn lấy (mặc định 10)");
                },
                "storeId"));

        if (session.isCustomer()) {
            declarations.add(declaration("get_my_profile",
                    "Xem hồ sơ cá nhân hiện tại của khách hàng đang đăng nhập (tên, email, số điện thoại, địa chỉ, tọa độ).",
                    props -> {}));

            declarations.add(declaration("analyze_my_orders",
                    "Lấy và thống kê chính xác đơn hàng, tổng chi tiêu (VNĐ), số lượng túi và cửa hàng đã đặt của chính khách hàng hiện tại. Có thể lọc theo tháng, năm, trạng thái hoặc cửa hàng.",
                    props -> {
                        props.putObject("month").put("type", "INTEGER").put("description", "Tháng (1-12)");
                        props.putObject("year").put("type", "INTEGER").put("description", "Năm (ví dụ 2026)");
                        props.putObject("status").put("type", "STRING").put("description", "Trạng thái đơn: PENDING_PAYMENT, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW");
                        props.putObject("storeId").put("type", "INTEGER").put("description", "Lọc theo ID cửa hàng nếu cần");
                    }));

            declarations.add(declaration("get_my_order_detail",
                    "Xem chi tiết đầy đủ của một đơn hàng thuộc về chính khách hàng hiện tại (bao gồm từng món, giờ nhận, mã nhận hàng nếu đã thanh toán).",
                    props -> props.putObject("orderId").put("type", "INTEGER").put("description", "ID đơn hàng"),
                    "orderId"));

            declarations.add(declaration("get_my_cart",
                    "Xem các túi đang có trong giỏ hàng hiện tại của khách hàng.",
                    props -> {}));
        }
        return tools;
    }

    public Mono<JsonNode> execute(UserSession session, String toolName, JsonNode args, List<Suggestion> suggestions) {
        return switch (toolName) {
            case "search_stores" -> searchStores(session, args, suggestions);
            case "get_store_detail" -> getStoreDetail(args, suggestions);
            case "get_store_reviews" -> getStoreReviews(args);
            case "get_my_profile" -> requireCustomer(session, () -> Mono.just(sanitizeProfile(session.profile())));
            case "analyze_my_orders" -> requireCustomer(session, () -> analyzeMyOrders(session, args, suggestions));
            case "get_my_order_detail" -> requireCustomer(session, () -> getMyOrderDetail(session, args, suggestions));
            case "get_my_cart" -> requireCustomer(session, () -> getMyCart(session));
            default -> Mono.just(errorNode("TOOL_NOT_FOUND", "Công cụ không tồn tại: " + toolName));
        };
    }

    private Mono<JsonNode> requireCustomer(UserSession session, java.util.function.Supplier<Mono<JsonNode>> action) {
        if (!session.authenticated()) {
            return Mono.just(errorNode("AUTH_REQUIRED", "Khách hàng chưa đăng nhập. Hãy mời khách đăng nhập để xem thông tin cá nhân hoặc đơn hàng."));
        }
        if (!session.isCustomer()) {
            return Mono.just(errorNode("CUSTOMER_ONLY", "Tài khoản hiện tại không phải khách hàng nên không có dữ liệu mua hàng cá nhân."));
        }
        return action.get();
    }

    private Mono<JsonNode> searchStores(UserSession session, JsonNode args, List<Suggestion> suggestions) {
        String keyword = textArg(args, "keyword");
        Double lat = doubleArg(args, "lat");
        Double lon = doubleArg(args, "lon");
        if (lat == null && lon == null) {
            lat = session.latitude();
            lon = session.longitude();
        }
        Double radiusKm = doubleArg(args, "radiusKm");
        int limit = Math.min(15, Math.max(1, intArg(args, "limit", 8)));

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(storeUrl + "/api/stores")
                .queryParam("page", 0)
                .queryParam("size", keyword != null ? 50 : limit);
        if (lat != null && lon != null) {
            uri.queryParam("lat", lat).queryParam("lon", lon);
            if (radiusKm != null && radiusKm > 0) uri.queryParam("radiusKm", radiusKm);
        }

        return webClient.get()
                .uri(uri.toUriString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .map(page -> {
                    List<JsonNode> items = new ArrayList<>();
                    for (JsonNode store : page.path("content")) {
                        if (keyword != null) {
                            String needle = keyword.toLowerCase(Locale.ROOT);
                            String hay = (store.path("name").asText("") + " " + store.path("address").asText(""))
                                    .toLowerCase(Locale.ROOT);
                            if (!hay.contains(needle)) continue;
                        }
                        items.add(store);
                        if (items.size() >= limit) break;
                    }
                    items.forEach(store -> addSuggestion(suggestions, new Suggestion(
                            "STORE",
                            store.path("id").asLong(),
                            store.path("name").asText("Cửa hàng"),
                            store.path("address").asText(""),
                            "/stores/" + store.path("id").asLong())));
                    ObjectNode res = mapper.createObjectNode();
                    res.put("matchedCount", items.size());
                    res.put("totalAvailableStores", page.path("totalElements").asLong(items.size()));
                    ArrayNode arr = res.putArray("stores");
                    items.forEach(arr::add);
                    return (JsonNode) res;
                })
                .onErrorResume(ex -> Mono.just(errorNode("STORE_SERVICE_ERROR", "Không tải được danh sách cửa hàng lúc này.")));
    }

    private Mono<JsonNode> getStoreDetail(JsonNode args, List<Suggestion> suggestions) {
        Long storeId = longArg(args, "storeId");
        if (storeId == null || storeId <= 0) {
            return Mono.just(errorNode("INVALID_STORE_ID", "Thiếu storeId hợp lệ"));
        }
        return webClient.get()
                .uri(storeUrl + "/api/stores/" + storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .doOnNext(store -> addSuggestion(suggestions, new Suggestion(
                        "STORE",
                        store.path("id").asLong(storeId),
                        store.path("name").asText("Cửa hàng #" + storeId),
                        store.path("address").asText(""),
                        "/stores/" + storeId)))
                .onErrorResume(ex -> Mono.just(errorNode("STORE_NOT_FOUND", "Không tìm thấy cửa hàng #" + storeId)));
    }

    private Mono<JsonNode> getStoreReviews(JsonNode args) {
        Long storeId = longArg(args, "storeId");
        if (storeId == null || storeId <= 0) {
            return Mono.just(errorNode("INVALID_STORE_ID", "Thiếu storeId hợp lệ"));
        }
        int size = Math.min(20, Math.max(1, intArg(args, "size", 10)));
        return webClient.get()
                .uri(storeUrl + "/api/stores/" + storeId + "/reviews?page=0&size=" + size)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .onErrorResume(ex -> Mono.just(errorNode("STORE_REVIEWS_ERROR", "Không tải được đánh giá của cửa hàng #" + storeId)));
    }

    private Mono<JsonNode> analyzeMyOrders(UserSession session, JsonNode args, List<Suggestion> suggestions) {
        Integer month = integerArg(args, "month");
        Integer year = integerArg(args, "year");
        String status = textArg(args, "status");
        Long storeId = longArg(args, "storeId");

        return fetchCustomerOrders(session.authorization())
                .flatMap(orders -> resolveStoreNames(orders).map(storeNames -> {
                    ObjectNode summary = CustomerOrderAnalyzer.analyze(
                            orders, month, year, status, storeId, storeNames, mapper);
                    for (JsonNode order : summary.path("orders")) {
                        if (suggestions.size() >= 6) break;
                        long oid = order.path("id").asLong();
                        String code = order.path("orderCode").asText("#" + oid);
                        String storeName = order.path("storeName").asText("Cửa hàng");
                        long amount = order.path("totalAmount").asLong(0);
                        addSuggestion(suggestions, new Suggestion(
                                "ORDER", oid, "Đơn " + code, storeName + " · " + amount + " đ", "/me/orders/" + oid));
                    }
                    return (JsonNode) summary;
                }))
                .onErrorResume(ex -> Mono.just(errorNode("ORDER_SERVICE_ERROR", "Không tải được đơn hàng của bạn lúc này.")));
    }

    private Mono<JsonNode> getMyOrderDetail(UserSession session, JsonNode args, List<Suggestion> suggestions) {
        Long orderId = longArg(args, "orderId");
        if (orderId == null || orderId <= 0) {
            return Mono.just(errorNode("INVALID_ORDER_ID", "Thiếu orderId hợp lệ"));
        }
        return webClient.get()
                .uri(orderUrl + "/api/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, session.authorization())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .flatMap(order -> {
                    long sid = order.path("storeId").asLong(-1);
                    Mono<String> storeNameMono = sid > 0 ? fetchStoreName(sid) : Mono.just("");
                    return storeNameMono.map(storeName -> {
                        ObjectNode clean = order.deepCopy();
                        clean.remove("commissionRate");
                        clean.remove("commissionAmount");
                        clean.remove("storeEarning");
                        if (!storeName.isBlank()) clean.put("storeName", storeName);
                        addSuggestion(suggestions, new Suggestion(
                                "ORDER",
                                order.path("id").asLong(orderId),
                                "Đơn " + order.path("orderCode").asText("#" + orderId),
                                (storeName.isBlank() ? "" : storeName + " · ") + order.path("totalAmount").asLong(0) + " đ",
                                "/me/orders/" + orderId));
                        return (JsonNode) clean;
                    });
                })
                .onErrorResume(ex -> Mono.just(errorNode("ORDER_NOT_FOUND", "Không tìm thấy đơn hàng #" + orderId)));
    }

    private Mono<JsonNode> getMyCart(UserSession session) {
        return webClient.get()
                .uri(orderUrl + "/api/cart")
                .header(HttpHeaders.AUTHORIZATION, session.authorization())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT)
                .onErrorResume(ex -> Mono.just(errorNode("CART_SERVICE_ERROR", "Không tải được giỏ hàng hiện tại.")));
    }

    private Mono<List<JsonNode>> fetchCustomerOrders(String authorization) {
        return fetchOrdersPage(authorization, 0).flatMap(firstPage -> {
            List<JsonNode> all = new ArrayList<>();
            firstPage.path("content").forEach(all::add);
            int totalPages = Math.min(5, firstPage.path("totalPages").asInt(1));
            if (totalPages <= 1) return Mono.just(all);

            List<Integer> remaining = new ArrayList<>();
            for (int p = 1; p < totalPages; p++) remaining.add(p);
            return Flux.fromIterable(remaining)
                    .flatMapSequential(p -> fetchOrdersPage(authorization, p))
                    .collectList()
                    .map(pages -> {
                        pages.forEach(page -> page.path("content").forEach(all::add));
                        return all;
                    });
        });
    }

    private Mono<JsonNode> fetchOrdersPage(String authorization, int page) {
        return webClient.get()
                .uri(orderUrl + "/api/orders?page=" + page + "&size=50")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(SERVICE_TIMEOUT);
    }

    private Mono<Map<Long, String>> resolveStoreNames(List<JsonNode> orders) {
        Set<Long> storeIds = new LinkedHashSet<>();
        for (JsonNode order : orders) {
            long sid = order.path("storeId").asLong(-1);
            if (sid > 0) storeIds.add(sid);
            if (storeIds.size() >= 20) break;
        }
        if (storeIds.isEmpty()) return Mono.just(Map.of());
        return Flux.fromIterable(storeIds)
                .flatMap(id -> fetchStoreName(id).map(name -> Map.entry(id, name)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<String> fetchStoreName(Long storeId) {
        return webClient.get()
                .uri(storeUrl + "/api/stores/" + storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(3))
                .map(node -> node.path("name").asText("Cửa hàng #" + storeId))
                .onErrorReturn("Cửa hàng #" + storeId);
    }

    private JsonNode sanitizeProfile(JsonNode profile) {
        if (profile == null) return mapper.createObjectNode();
        ObjectNode node = mapper.createObjectNode();
        node.put("id", profile.path("id").asLong());
        node.put("fullName", profile.path("fullName").asText(""));
        node.put("email", profile.path("email").asText(""));
        node.put("phone", profile.path("phone").asText(""));
        node.put("role", profile.path("role").asText(""));
        if (!profile.path("address").isNull()) node.put("address", profile.path("address").asText(""));
        if (!profile.path("latitude").isNull()) node.set("latitude", profile.path("latitude"));
        if (!profile.path("longitude").isNull()) node.set("longitude", profile.path("longitude"));
        return node;
    }

    private ObjectNode declaration(String name, String description,
                                   java.util.function.Consumer<ObjectNode> propertiesBuilder,
                                   String... required) {
        ObjectNode fn = mapper.createObjectNode();
        fn.put("name", name);
        fn.put("description", description);
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "OBJECT");
        ObjectNode props = params.putObject("properties");
        propertiesBuilder.accept(props);
        if (required.length > 0) {
            ArrayNode req = params.putArray("required");
            for (String r : required) req.add(r);
        }
        return fn;
    }

    private ObjectNode errorNode(String code, String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("errorCode", code);
        node.put("error", message);
        return node;
    }

    private static void addSuggestion(List<Suggestion> suggestions, Suggestion item) {
        if (suggestions.size() >= 6) return;
        boolean exists = suggestions.stream()
                .anyMatch(s -> s.type().equals(item.type()) && s.id().equals(item.id()));
        if (!exists) suggestions.add(item);
    }

    private static String textArg(JsonNode args, String name) {
        if (args == null || !args.has(name) || args.get(name).isNull()) return null;
        String value = args.get(name).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer integerArg(JsonNode args, String name) {
        if (args == null || !args.has(name) || args.get(name).isNull()) return null;
        JsonNode v = args.get(name);
        return v.isNumber() ? v.intValue() : null;
    }

    private static int intArg(JsonNode args, String name, int fallback) {
        Integer value = integerArg(args, name);
        return value != null ? value : fallback;
    }

    private static Long longArg(JsonNode args, String name) {
        if (args == null || !args.has(name) || args.get(name).isNull()) return null;
        JsonNode v = args.get(name);
        return v.isNumber() ? v.longValue() : null;
    }

    private static Double doubleArg(JsonNode args, String name) {
        if (args == null || !args.has(name) || args.get(name).isNull()) return null;
        JsonNode v = args.get(name);
        return v.isNumber() ? v.doubleValue() : null;
    }

    private static Double nullableDouble(JsonNode node) {
        return node != null && node.isNumber() ? node.doubleValue() : null;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record UserSession(boolean authenticated, String authorization, Long userId, String role,
                              String fullName, String email, String address,
                              Double latitude, Double longitude, JsonNode profile) {
        public static UserSession anonymous(Double lat, Double lon) {
            return new UserSession(false, null, null, "ANONYMOUS", "", "", null, lat, lon, null);
        }

        public boolean isCustomer() {
            return authenticated && "CUSTOMER".equals(role);
        }
    }
}
