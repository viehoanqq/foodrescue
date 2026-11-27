package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ICategoryRepository {
    List<Category> findByStore(Long storeId);
    List<Category> findByStores(Collection<Long> storeIds);
    List<Category> findAllByStore(Long storeId);

    /** Tìm trong đúng cửa hàng và khóa dòng để sửa/xóa an toàn khi đồng thời. */
    Optional<Category> findForUpdate(Long storeId, Long id);

    /** Tên trùng (không phân biệt hoa thường) trong cùng cửa hàng; excludeId bỏ qua chính nó. */
    boolean nameTaken(Long storeId, String name, Long excludeId);

    boolean hasBatches(Long categoryId);

    /** Thêm mới khi id null, ngược lại cập nhật; trả bản đã lưu. */
    Category save(Category category);

    /** Lưu thời điểm archive, không xóa hàng dữ liệu được batch và lịch sử tham chiếu. */
    void delete(Category category);
}
