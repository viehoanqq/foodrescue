package com.foodrescue.catalog.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.catalog.application.usecase.CategoryUseCase;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.CategoryDraft;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Danh mục", description = "Danh mục túi thuộc từng cửa hàng")
public class CategoryController {
    private final CategoryUseCase categories;

    public CategoryController(CategoryUseCase categories) {
        this.categories = categories;
    }

    /** Công khai: danh mục của một cửa hàng đã được duyệt. */
    @GetMapping("/api/categories")
    public List<CategoryResponse> listPublic(@RequestParam Long storeId) {
        return categories.listPublic(storeId).stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/api/store/categories")
    @SecurityRequirement(name = "bearerAuth")
    public List<CategoryResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return categories.listMine(actor(user)).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/api/store/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categories.create(actor(user), request.toDraft()));
    }

    @PutMapping("/api/store/categories/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                   @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categories.update(actor(user), id, request.toDraft()));
    }

    @DeleteMapping("/api/store/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        categories.delete(actor(user), id);
    }

    @GetMapping("/api/admin/categories")
    @SecurityRequirement(name = "bearerAuth")
    public List<CategoryResponse> listAdmin(@RequestParam Long storeId) {
        return categories.listAdmin(storeId).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse createAdmin(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam Long storeId,
                                        @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categories.createAdmin(actor(user), storeId, request.toDraft()));
    }

    @PutMapping("/api/admin/categories/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public CategoryResponse updateAdmin(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam Long storeId, @PathVariable Long id,
                                        @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categories.updateAdmin(actor(user), storeId, id, request.toDraft()));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void deleteAdmin(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam Long storeId, @PathVariable Long id) {
        categories.deleteAdmin(actor(user), storeId, id);
    }

    private static Actor actor(AuthenticatedUser user) {
        return new Actor(user.id(), user.role(), user.storeId());
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @Size(max = 255) String imageUrl,
            @Min(0) @Max(9999) Integer sortOrder) {
        CategoryDraft toDraft() {
            return new CategoryDraft(name, description, imageUrl, sortOrder);
        }
    }

    public record CategoryResponse(Long id, Long storeId, String name, String description,
                                   String imageUrl, int sortOrder, boolean active) {
        static CategoryResponse from(Category c) {
            return new CategoryResponse(c.id(), c.storeId(), c.name(), c.description(), c.imageUrl(),
                    c.sortOrder(), c.active());
        }
    }
}
