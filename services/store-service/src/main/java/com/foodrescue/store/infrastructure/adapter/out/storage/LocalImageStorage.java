package com.foodrescue.store.infrastructure.adapter.out.storage;

import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.application.port.out.IImageStorage.Upload;
import com.foodrescue.store.domain.exception.StoreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalImageStorage implements IImageStorage {
    private final Path root;
    public LocalImageStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }
    @Override public String saveLicense(Upload file) { return save(file, "licenses"); }
    @Override public String saveImage(Upload file) { return save(file, "images"); }

    private String save(Upload file, String folder) {
        if (file == null || file.isEmpty()) throw new StoreException(400, "FILE_REQUIRED", "Thiếu ảnh tải lên");
        String extension = switch (file.contentType() == null ? "" : file.contentType().toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new StoreException(400, "INVALID_FILE_TYPE", "Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP");
        };
        try {
            Path directory = root.resolve(folder);
            Files.createDirectories(directory);
            Path target = directory.resolve(UUID.randomUUID() + extension);
            Files.write(target, file.bytes());
            return "/uploads/" + folder + "/" + target.getFileName();
        } catch (IOException ex) { throw new StoreException(500, "UPLOAD_FAILED", "Không thể lưu ảnh"); }
    }

    @Override public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/")) return;
        Path target = root.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { }
    }

    @Override public byte[] readLicense(String url) {
        if (url == null || !url.startsWith("/uploads/licenses/"))
            throw new StoreException(404, "LICENSE_NOT_FOUND", "Không tìm thấy giấy phép");
        Path target = root.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root.resolve("licenses")))
            throw new StoreException(404, "LICENSE_NOT_FOUND", "Không tìm thấy giấy phép");
        try { return Files.readAllBytes(target); }
        catch (IOException ex) { throw new StoreException(404, "LICENSE_NOT_FOUND", "Không tìm thấy giấy phép"); }
    }
}
