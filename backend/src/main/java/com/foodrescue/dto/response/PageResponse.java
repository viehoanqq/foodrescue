package com.foodrescue.dto.response;

import java.util.List;

/**
 * Danh sách phân trang (Quy ước chung mục 5.3).
 * <pre>
 * { "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }
 * </pre>
 * LƯU Ý: file này do TV1 sở hữu. TV2 thêm bản tối thiểu vì H2 cần dùng; nếu TV1 đã có bản riêng thì giữ bản của TV1.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /** Cắt 1 trang từ danh sách đầy đủ (dùng cho controller dữ liệu giả). */
    public static <T> PageResponse<T> ofList(List<T> all, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return of(List.copyOf(all.subList(from, to)), safePage, safeSize, all.size());
    }
}
