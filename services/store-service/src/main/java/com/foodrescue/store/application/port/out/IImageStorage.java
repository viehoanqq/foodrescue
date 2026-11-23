package com.foodrescue.store.application.port.out;

public interface IImageStorage {
    String saveLicense(Upload file);
    String saveImage(Upload file);
    void delete(String url);
    byte[] readLicense(String url);
    record Upload(byte[] bytes, String contentType) {
        public boolean isEmpty() { return bytes == null || bytes.length == 0; }
    }
}
