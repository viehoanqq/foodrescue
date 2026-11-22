# Cau truc chung cua service

Moi service nghiep vu la **mot Maven project, mot Spring Boot application, mot database rieng**. `api-gateway` va `building-blocks` la module ky thuat.

```text
<service>/
  pom.xml
  Dockerfile
  src/main/java/com/foodrescue/<name>/
    <Name>Application.java
    domain/model/                      # Model nghiep vu
    domain/repository/                 # Repository interface khi can
    domain/exception/
    application/usecase/               # Dieu phoi nghiep vu
    application/port/out/              # Interface cho DB, HTTP, event, transaction
    api/                           # Controller, HTTP DTO, error handler
    infrastructure/adapter/out/persistence/ # JPA/JDBC, entity
    infrastructure/adapter/out/http/   # Client service khac
    infrastructure/messaging/          # RabbitMQ, Outbox, Inbox
    infrastructure/config/             # Spring bean wiring
  src/main/resources/application.yml
  src/main/resources/db/migration/     # Flyway
```

Huong phu thuoc: `infrastructure -> application -> domain`. Domain khong import Spring/JPA; JPA entity chi nam trong adapter persistence. Use case nhan port interface va duoc tao bang `@Bean` trong infrastructure. Moi thay doi schema tao migration Flyway moi.

Hiện Identity đã có auth, quản trị user và audit. Catalog và Order mới có luồng giữ kho nội bộ. Store và Finance mới có entity, Flyway và Outbox publisher. Gateway chỉ chuyển API Identity đã triển khai; frontend dùng dữ liệu demo cho các nghiệp vụ còn lại.
