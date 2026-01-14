# Система бронирования отелей

![CI](https://github.com/vterdunov/hotel-booking-demo/actions/workflows/ci.yml/badge.svg)

Микросервисная система бронирования отелей на базе Spring Boot 3.5.x с использованием Spring Cloud.

## Архитектура

```
┌─────────────────┐
│   API Gateway   │ :8080
│ (Spring Cloud)  │
└────────┬────────┘
         │
    ┌────┴────┐
    │ Eureka  │ :8761
    │ Server  │
    └────┬────┘
         │
   ┌─────┴─────┐
   │           │
┌──┴──┐     ┌──┴──┐
│Book-│     │Hotel│
│ing  │────▶│Serv-│
│:8081│     │ice  │
└─────┘     │:8082│
            └─────┘
```

## Компоненты системы

| Сервис | Порт | Описание |
|--------|------|----------|
| Eureka Server | 8761 | Service Discovery |
| API Gateway | 8080 | Маршрутизация запросов |
| Booking Service | 8081 | Бронирования и пользователи |
| Hotel Service | 8082 | Отели и номера |

## Требования

- Java 23
- Maven 3.9+
- Docker и Docker Compose (для контейнеризации)

## Запуск с Docker Compose (рекомендуется)

```bash
# Сборка и запуск всех сервисов
docker compose up --build

# Запуск в фоновом режиме
docker compose up --build -d

# Просмотр логов
docker compose logs -f

# Остановка
docker compose down
```

После запуска:
- Eureka Dashboard: http://localhost:8761
- API Gateway: http://localhost:8080
- Swagger Hotel Service: http://localhost:8082/swagger-ui.html
- Swagger Booking Service: http://localhost:8081/swagger-ui.html

## Конфигурация администратора

Создание дефолтного администратора настраивается через переменные окружения:

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `ADMIN_ENABLED` | Включить создание админа | `false` |
| `ADMIN_USERNAME` | Имя пользователя | `admin` |
| `ADMIN_PASSWORD` | Пароль (обязателен) | - |

### Docker Compose

В `docker-compose.yml` уже настроены переменные для booking-service:

```yaml
environment:
  - ADMIN_ENABLED=true
  - ADMIN_USERNAME=admin
  - ADMIN_PASSWORD=admin123
```

### Локальный запуск

```bash
ADMIN_ENABLED=true ADMIN_PASSWORD=secret123 mvn spring-boot:run -pl booking-service
```

### Production

В production рекомендуется:
- Использовать сложный пароль
- Передавать пароль через секреты (Docker Secrets, Kubernetes Secrets, Vault)
- Отключить создание админа после первичной настройки (`ADMIN_ENABLED=false`)

## Предзаполнение данных

При запуске через Docker Compose автоматически создаются демо-данные:
- 3 отеля с номерами
- Всего 17 номеров для тестирования

Для локального запуска:
```bash
DATA_INIT_ENABLED=true mvn spring-boot:run -pl hotel-service
```

## Запуск локально (без Docker)

### 1. Сборка проекта

```bash
mvn clean install -DskipTests
```

### 2. Запуск сервисов (в указанном порядке)

```bash
# Терминал 1: Eureka Server
cd eureka-server
mvn spring-boot:run

# Терминал 2: Hotel Service
cd hotel-service
mvn spring-boot:run

# Терминал 3: Booking Service
cd booking-service
mvn spring-boot:run

# Терминал 4: API Gateway
cd api-gateway
mvn spring-boot:run
```

### 3. Проверка работоспособности

- Eureka Dashboard: http://localhost:8761
- Swagger Hotel Service: http://localhost:8082/swagger-ui.html
- Swagger Booking Service: http://localhost:8081/swagger-ui.html

## API Эндпоинты

### Booking Service

#### Аутентификация

| Метод | URL | Описание | Доступ |
|-------|-----|----------|--------|
| POST | /api/users/register | Регистрация пользователя | Публичный |
| POST | /api/users/auth | Авторизация | Публичный |

#### Пользователи (ADMIN)

| Метод | URL | Описание |
|-------|-----|----------|
| POST | /api/users | Создать пользователя |
| PATCH | /api/users/{id} | Обновить пользователя |
| DELETE | /api/users/{id} | Удалить пользователя |
| GET | /api/users | Получить всех пользователей |

#### Бронирования (USER)

| Метод | URL | Описание |
|-------|-----|----------|
| POST | /api/bookings | Создать бронирование |
| GET | /api/bookings | История бронирований |
| GET | /api/bookings/paged | История бронирований с пагинацией |
| GET | /api/bookings/{id} | Получить бронирование |
| DELETE | /api/bookings/{id} | Отменить бронирование |

### Hotel Service

#### Отели

| Метод | URL | Описание | Доступ |
|-------|-----|----------|--------|
| GET | /api/hotels | Список отелей | USER |
| GET | /api/hotels/{id} | Отель по ID | USER |
| POST | /api/hotels | Создать отель | ADMIN |
| DELETE | /api/hotels/{id} | Удалить отель | ADMIN |

#### Номера

| Метод | URL | Описание | Доступ |
|-------|-----|----------|--------|
| GET | /api/rooms?startDate=&endDate= | Свободные номера | USER |
| GET | /api/rooms/recommend?startDate=&endDate= | Рекомендованные номера | USER |
| GET | /api/rooms/statistics | Статистика загруженности номеров | ADMIN |
| POST | /api/rooms | Создать номер | ADMIN |
| POST | /api/rooms/{id}/confirm-availability | Подтвердить доступность | INTERNAL |
| POST | /api/rooms/{id}/release | Освободить слот | INTERNAL |

## Примеры использования

### 1. Регистрация пользователя

```bash
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password123"}'
```

Ответ:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "user1",
    "role": "USER"
  }
}
```

### 2. Авторизация

```bash
curl -X POST http://localhost:8080/api/users/auth \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password123"}'
```

### 3. Создание отеля (ADMIN)

```bash
curl -X POST http://localhost:8080/api/hotels \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"name": "Grand Hotel", "address": "ул. Пушкина, 10"}'
```

### 4. Создание номера (ADMIN)

```bash
curl -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"hotelId": 1, "number": "101"}'
```

### 5. Получение свободных номеров

```bash
curl -X GET "http://localhost:8080/api/rooms?startDate=2025-01-20&endDate=2025-01-25" \
  -H "Authorization: Bearer <token>"
```

### 6. Создание бронирования

```bash
# С указанием номера
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "roomId": 1,
    "startDate": "2025-01-20",
    "endDate": "2025-01-25",
    "autoSelect": false
  }'

# С автовыбором номера
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "startDate": "2025-01-20",
    "endDate": "2025-01-25",
    "autoSelect": true
  }'
```

### 7. Получение истории бронирований

```bash
curl -X GET http://localhost:8080/api/bookings \
  -H "Authorization: Bearer <token>"
```

### 8. Отмена бронирования

```bash
curl -X DELETE http://localhost:8080/api/bookings/1 \
  -H "Authorization: Bearer <token>"
```

## Тестирование

### Запуск всех тестов

```bash
mvn test
```

### Запуск тестов конкретного модуля

```bash
# Hotel Service
cd hotel-service && mvn test

# Booking Service
cd booking-service && mvn test
```

### CI

Настроен GitHub Actions для автоматического запуска тестов при push и pull request в ветку `main`.

## Особенности реализации

### JWT аутентификация

- Срок действия токена: 1 час
- Алгоритм: HS256
- Роли: USER, ADMIN

### Двухшаговая транзакция бронирования

1. Создание бронирования в статусе `PENDING`
2. Запрос подтверждения в Hotel Service
3. При успехе: статус `CONFIRMED`
4. При ошибке: статус `CANCELLED` + компенсация (release)

### Идемпотентность

Каждый запрос бронирования имеет уникальный `requestId`, который предотвращает дублирование при повторных запросах.

### Retry с экспоненциальным backoff

При ошибках связи с Hotel Service:
- 3 попытки
- Экспоненциальный интервал
- Timeout: 5 секунд

## База данных

Используется H2 in-memory база данных.

### Booking Service

| Таблица | Поля |
|---------|------|
| users | id, username, password, role |
| bookings | id, user_id, room_id, start_date, end_date, status, created_at, request_id |

### Hotel Service

| Таблица | Поля |
|---------|------|
| hotels | id, name, address |
| rooms | id, hotel_id, number, available, times_booked |
| room_slots | id, room_id, start_date, end_date, request_id, confirmed |

## Логирование и трассировка

### Корреляция запросов

Для отслеживания процесса бронирования используется корреляционный идентификатор (`requestId`), который передается между сервисами и логируется на каждом этапе.

### Распределённая трассировка

Интегрирован Micrometer Tracing с Brave для отслеживания запросов между сервисами:
- `traceId` и `spanId` добавляются в логи автоматически
- Формат логов: `[service-name,traceId,spanId]`

Пример логов:
```
INFO [booking-service,abc123,def456] Creating booking for user 1
INFO [hotel-service,abc123,ghi789] Confirming availability for room 1
```
