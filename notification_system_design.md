# Stage 1

# Notification System — API Design

---

## Table of Contents

1. [Overview](#overview)
2. [Core Actions](#core-actions)
3. [Common Conventions](#common-conventions)
4. [REST API Endpoints](#rest-api-endpoints)
   - [Create Notification](#1-create-notification)
   - [Bulk Create Notifications](#2-bulk-create-notifications)
   - [List Notifications for a User](#3-list-notifications-for-a-user)
   - [Get Single Notification](#4-get-single-notification)
   - [Get Unread Count](#5-get-unread-count)
   - [Mark Notification as Read](#6-mark-notification-as-read)
   - [Mark All as Read](#7-mark-all-notifications-as-read)
   - [Bulk Mark as Read](#8-bulk-mark-as-read)
   - [Delete Notification](#9-delete-notification)
   - [Get Preferences](#10-get-user-preferences)
   - [Update Preferences](#11-update-user-preferences)
5. [JSON Schemas](#json-schemas)
6. [Real-Time Notification Design](#real-time-notification-design)
7. [Error Handling](#error-handling)

---

## Overview

This document describes the REST API contract for the Notification Platform — a microservice responsible for creating, delivering, persisting, and managing user-facing notifications. It targets front-end developers building notification UI components and supports both polling and real-time (WebSocket) delivery.

**Base URL:** `https://api.example.com/api/v1`  
**Version:** `v1`  
**Content-Type:** `application/json`  
**Authentication:** Bearer JWT — all `/api/v1/**` endpoints require `Authorization: Bearer <token>`

---

## Core Actions

| Action | Description |
|---|---|
| **Create** | Send a notification to one or many users |
| **Read / List** | Fetch all notifications for a user (filtered, paginated) |
| **Mark Read** | Mark one, many, or all notifications as read |
| **Delete** | Remove a notification |
| **Count Unread** | Get badge count, broken down by priority |
| **Manage Preferences** | Per-user channel and type opt-in/opt-out settings |
| **Real-time Delivery** | Push new notifications instantly over WebSocket |

---

## Common Conventions

### Naming
- All endpoints use **kebab-case** nouns: `/notifications`, `/unread-count`, `/read-all`
- Resources are **plural**: `/notifications`, `/users`
- Sub-resources nest under their parent: `/notifications/users/{userId}/preferences`
- Actions that cannot be expressed as CRUD use PATCH + a verb suffix: `/read`, `/read-all`

### HTTP Methods

| Method | Usage |
|---|---|
| `POST` | Create a new resource |
| `GET` | Read/list resources (no side effects) |
| `PATCH` | Partial update (only provided fields change) |
| `DELETE` | Remove a resource |

### Pagination
All list endpoints support:
```
?page=0&size=20
```
Response includes `totalElements`, `totalPages`, `hasNext`, `hasPrevious`.

### Filtering
```
?isRead=false&type=ALERT
```

### Standard Headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization` | Request | `Bearer <JWT>` |
| `Content-Type` | Request | `application/json` |
| `Accept` | Request | `application/json` |
| `X-User-Id` | Request | Acting user's ID (used for ownership checks) |
| `X-Request-Id` | Request | Idempotency / tracing key |
| `X-Correlation-Id` | Response | Trace ID echoed back |

---

## REST API Endpoints

---

### 1. Create Notification

**`POST /api/v1/notifications`**

Creates a notification for a single user and delivers it in real-time if the user is connected.

#### Request Headers
```
Authorization: Bearer <token>
Content-Type: application/json
X-Request-Id: <uuid>
```

#### Request Body
```json
{
  "userId": "user-abc123",
  "title": "Order Shipped",
  "message": "Your order #5521 has been dispatched and will arrive by Friday.",
  "type": "TRANSACTION",
  "priority": "HIGH",
  "actionUrl": "/orders/5521",
  "sourceService": "order-service",
  "referenceId": "5521",
  "referenceType": "ORDER",
  "expiresAt": "2025-01-15T00:00:00Z"
}
```

#### Field Definitions
| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | string | ✅ | Recipient's user ID |
| `title` | string | ✅ | Short heading (max 100 chars) |
| `message` | string | ✅ | Body text (max 1000 chars) |
| `type` | enum | ✅ | `SYSTEM` `ALERT` `PROMOTION` `SOCIAL` `TRANSACTION` `REMINDER` |
| `priority` | enum | ❌ | `LOW` `MEDIUM` `HIGH` `CRITICAL` — default `MEDIUM` |
| `actionUrl` | string | ❌ | Deep-link for click action |
| `sourceService` | string | ❌ | Originating microservice name |
| `referenceId` | string | ❌ | Related entity ID |
| `referenceType` | enum | ❌ | `ORDER` `POST` `USER` `PAYMENT` `PRODUCT` `GENERIC` |
| `expiresAt` | ISO-8601 | ❌ | Auto-delete after this timestamp |

#### Response — 201 Created
```json
{
  "success": true,
  "message": "Notification created",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "id": "notif-uuid-001",
    "userId": "user-abc123",
    "title": "Order Shipped",
    "message": "Your order #5521 has been dispatched and will arrive by Friday.",
    "type": "TRANSACTION",
    "priority": "HIGH",
    "isRead": false,
    "createdAt": "2025-01-10T09:00:00Z",
    "readAt": null,
    "expiresAt": "2025-01-15T00:00:00Z",
    "actionUrl": "/orders/5521",
    "sourceService": "order-service",
    "referenceId": "5521",
    "referenceType": "ORDER"
  }
}
```

---

### 2. Bulk Create Notifications

**`POST /api/v1/notifications/bulk`**

Broadcasts a notification to multiple users in a single request.

#### Request Body
```json
{
  "userIds": ["user-001", "user-002", "user-003"],
  "title": "Platform Maintenance",
  "message": "Scheduled maintenance on Jan 12 from 2–4 AM UTC.",
  "type": "SYSTEM",
  "priority": "HIGH",
  "actionUrl": "/status",
  "sourceService": "platform-ops",
  "expiresAt": "2025-01-12T06:00:00Z"
}
```

#### Response — 207 Multi-Status
```json
{
  "success": true,
  "message": "Bulk create complete",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "requested": 3,
    "succeeded": 3,
    "failed": 0,
    "failedIds": []
  }
}
```

---

### 3. List Notifications for a User

**`GET /api/v1/notifications/users/{userId}`**

Returns a paginated list of notifications. Supports filtering by read status and type.

#### Query Parameters
| Param | Type | Default | Description |
|---|---|---|---|
| `isRead` | boolean | (none) | Filter by read state |
| `type` | enum | (none) | Filter by notification type |
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `20` | Items per page (max 100) |

#### Example Request
```
GET /api/v1/notifications/users/user-abc123?isRead=false&type=ALERT&page=0&size=10
Authorization: Bearer <token>
```

#### Response — 200 OK
```json
{
  "success": true,
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "data": [
      {
        "id": "notif-uuid-001",
        "userId": "user-abc123",
        "title": "Login from new device",
        "message": "A login was detected from Chrome on Windows in London, UK.",
        "type": "ALERT",
        "priority": "CRITICAL",
        "isRead": false,
        "createdAt": "2025-01-10T08:45:00Z",
        "readAt": null,
        "expiresAt": null,
        "actionUrl": "/security/sessions",
        "sourceService": "auth-service",
        "referenceId": null,
        "referenceType": null
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

---

### 4. Get Single Notification

**`GET /api/v1/notifications/{id}`**

#### Request Headers
```
Authorization: Bearer <token>
X-User-Id: user-abc123
```

#### Response — 200 OK
```json
{
  "success": true,
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "id": "notif-uuid-001",
    "userId": "user-abc123",
    "title": "Order Shipped",
    "message": "Your order #5521 has been dispatched.",
    "type": "TRANSACTION",
    "priority": "HIGH",
    "isRead": true,
    "createdAt": "2025-01-10T09:00:00Z",
    "readAt": "2025-01-10T09:05:00Z",
    "expiresAt": null,
    "actionUrl": "/orders/5521",
    "sourceService": "order-service",
    "referenceId": "5521",
    "referenceType": "ORDER"
  }
}
```

#### Response — 404 Not Found
```json
{
  "success": false,
  "message": "Notification not found: notif-uuid-999",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": null
}
```

---

### 5. Get Unread Count

**`GET /api/v1/notifications/users/{userId}/unread-count`**

Lightweight endpoint for badge indicators — no pagination, no content.

#### Response — 200 OK
```json
{
  "success": true,
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "userId": "user-abc123",
    "totalUnread": 7,
    "criticalUnread": 1,
    "highUnread": 3
  }
}
```

---

### 6. Mark Notification as Read

**`PATCH /api/v1/notifications/{id}/read`**

#### Request Headers
```
Authorization: Bearer <token>
X-User-Id: user-abc123
```

#### Response — 200 OK
```json
{
  "success": true,
  "message": "Notification marked as read",
  "timestamp": "2025-01-10T09:05:00Z",
  "data": {
    "id": "notif-uuid-001",
    "isRead": true,
    "readAt": "2025-01-10T09:05:00Z"
  }
}
```

---

### 7. Mark All Notifications as Read

**`PATCH /api/v1/notifications/users/{userId}/read-all`**

Marks every unread notification for the user as read in a single operation.

#### Response — 200 OK
```json
{
  "success": true,
  "message": "7 notifications marked as read",
  "timestamp": "2025-01-10T09:05:00Z",
  "data": null
}
```

---

### 8. Bulk Mark as Read

**`PATCH /api/v1/notifications/bulk/read`**

#### Request Headers
```
Authorization: Bearer <token>
X-User-Id: user-abc123
Content-Type: application/json
```

#### Request Body
```json
{
  "notificationIds": ["notif-uuid-001", "notif-uuid-002", "notif-uuid-003"]
}
```

#### Response — 200 OK
```json
{
  "success": true,
  "message": "Bulk read complete",
  "timestamp": "2025-01-10T09:05:00Z",
  "data": {
    "requested": 3,
    "succeeded": 3,
    "failed": 0,
    "failedIds": []
  }
}
```

---

### 9. Delete Notification

**`DELETE /api/v1/notifications/{id}`**

#### Request Headers
```
Authorization: Bearer <token>
X-User-Id: user-abc123
```

#### Response — 200 OK
```json
{
  "success": true,
  "message": "Notification deleted",
  "timestamp": "2025-01-10T09:10:00Z",
  "data": null
}
```

---

### 10. Get User Preferences

**`GET /api/v1/notifications/users/{userId}/preferences`**

#### Response — 200 OK
```json
{
  "success": true,
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "userId": "user-abc123",
    "emailEnabled": true,
    "pushEnabled": true,
    "inAppEnabled": true,
    "smsEnabled": false,
    "systemEnabled": true,
    "alertEnabled": true,
    "promotionEnabled": false,
    "socialEnabled": true,
    "transactionEnabled": true,
    "reminderEnabled": true,
    "quietHoursStart": "22:00",
    "quietHoursEnd": "08:00",
    "updatedAt": "2025-01-09T14:00:00Z"
  }
}
```

---

### 11. Update User Preferences

**`PATCH /api/v1/notifications/users/{userId}/preferences`**

Only fields included in the body are updated (partial update semantics).

#### Request Body
```json
{
  "promotionEnabled": false,
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

#### Response — 200 OK
```json
{
  "success": true,
  "message": "Preferences updated",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "userId": "user-abc123",
    "promotionEnabled": false,
    "quietHoursStart": "22:00",
    "quietHoursEnd": "08:00",
    "updatedAt": "2025-01-10T09:00:00Z"
  }
}
```

---

## JSON Schemas

### Notification Object
```json
{
  "id":            "string (UUID)",
  "userId":        "string",
  "title":         "string (max 100)",
  "message":       "string (max 1000)",
  "type":          "SYSTEM | ALERT | PROMOTION | SOCIAL | TRANSACTION | REMINDER",
  "priority":      "LOW | MEDIUM | HIGH | CRITICAL",
  "isRead":        "boolean",
  "createdAt":     "ISO-8601 timestamp",
  "readAt":        "ISO-8601 timestamp | null",
  "expiresAt":     "ISO-8601 timestamp | null",
  "actionUrl":     "string | null",
  "sourceService": "string | null",
  "referenceId":   "string | null",
  "referenceType": "ORDER | POST | USER | PAYMENT | PRODUCT | GENERIC | null"
}
```

### ApiResponse Envelope
```json
{
  "success":   "boolean",
  "message":   "string | null",
  "data":      "object | null",
  "timestamp": "ISO-8601 timestamp"
}
```

### PagedResponse
```json
{
  "data":          "array",
  "page":          "integer (0-based)",
  "size":          "integer",
  "totalElements": "integer",
  "totalPages":    "integer",
  "hasNext":       "boolean",
  "hasPrevious":   "boolean"
}
```

---

## Real-Time Notification Design

### Mechanism: WebSocket

The service uses a persistent **WebSocket** connection to push notifications to the browser or mobile app instantly — without polling.

**Connection URL:**
```
ws://api.example.com/ws/notifications?userId={userId}
```

**Connection Flow:**
```
Client                          Server
  |                               |
  |--- WS Handshake ------------> |
  |<-- 101 Switching Protocols -- |
  |                               |
  |  [user creates order]         |
  |                               |
  |<-- {"event":"NOTIFICATION"    |
  |      "data": { ... }} -----   |
  |                               |
  |--- "ping" -----------------> |
  |<-- "pong" ------------------ |
  |                               |
  |--- Close ------------------>  |
```

### WebSocket Message Payloads

#### Incoming: New Notification
```json
{
  "event": "NOTIFICATION",
  "data": {
    "id": "notif-uuid-001",
    "userId": "user-abc123",
    "title": "Order Shipped",
    "message": "Your order #5521 has been dispatched.",
    "type": "TRANSACTION",
    "priority": "HIGH",
    "isRead": false,
    "createdAt": "2025-01-10T09:00:00Z",
    "actionUrl": "/orders/5521"
  }
}
```

#### Incoming: Unread Count Update
```json
{
  "event": "UNREAD_COUNT",
  "data": {
    "unreadCount": 8
  }
}
```

#### Client → Server: Keepalive
```
ping
```

#### Server → Client: Keepalive Response
```
pong
```

### Fallback: SSE (Server-Sent Events)

For clients that cannot maintain WebSocket connections, an SSE endpoint is provided as a fallback:

```
GET /api/v1/notifications/users/{userId}/stream
Accept: text/event-stream
Authorization: Bearer <token>
```

SSE events follow the same payload format as WebSocket messages.

### Architecture Overview

```
[Producer Services]
  order-service, auth-service, marketing-service
        │
        ▼
[POST /api/v1/notifications]
        │
        ├──► [H2 / PostgreSQL] — persisted for history
        │
        └──► [NotificationWebSocketHandler]
                │
                ├──► Session Map: { userId → [WS sessions] }
                │
                └──► Push JSON to all active user sessions
```

### Why WebSocket Over Polling?

| Concern | Polling | WebSocket |
|---|---|---|
| Latency | 1–30 seconds | < 100 ms |
| Server load | High (N users × poll interval) | Low (idle connections) |
| Battery (mobile) | High | Low |
| Complexity | Simple | Moderate |

Polling via `GET /api/v1/notifications/users/{userId}/unread-count` remains available as a lightweight fallback for constrained environments.

---

## Error Handling

All errors use the standard `ApiResponse` envelope with `success: false`.

### HTTP Status Code Reference

| Status | Meaning |
|---|---|
| `200 OK` | Success |
| `201 Created` | Resource created |
| `207 Multi-Status` | Bulk operation with partial results |
| `400 Bad Request` | Validation failure |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | Authenticated but not authorized |
| `404 Not Found` | Resource does not exist |
| `500 Internal Server Error` | Unexpected server fault |

### Validation Error Response — 400
```json
{
  "success": false,
  "message": "Validation failed",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": {
    "userId": "userId is required",
    "title": "must not be blank"
  }
}
```

### Unauthorized — 401
```json
{
  "success": false,
  "message": "Authentication required",
  "timestamp": "2025-01-10T09:00:00Z",
  "data": null
}
```

---

---

# Stage 2

# Notification System — Persistent Storage Design

---

## Table of Contents

1. [Database Choice & Rationale](#1-database-choice--rationale)
2. [Complete Database Schema](#2-complete-database-schema)
3. [Scalability Problems & Solutions](#3-scalability-problems--solutions)
4. [SQL Queries for All REST API Operations](#4-sql-queries-for-all-rest-api-operations)

---

## 1. Database Choice & Rationale

### Recommended Database: **PostgreSQL**

PostgreSQL is the recommended primary store for the notification platform. The decision is made after evaluating the data characteristics of this system against the strengths of relational and NoSQL alternatives.

### Why Not NoSQL?

| NoSQL Option | Why It Doesn't Fit |
|---|---|
| **MongoDB** | Notifications are structured and uniform — there is no need for schema flexibility. Joins between notifications and preferences are clean in SQL. |
| **Cassandra** | Excellent for pure time-series append-only workloads, but `PATCH` operations (mark-read, update preferences) are expensive. Cassandra penalises reads-before-writes and `UPDATE` semantics. |
| **Redis** | Ideal as a cache layer (used later in this stage), but not a durable primary store for historical notification records. |
| **DynamoDB** | Good at scale but forces you to pre-define every access pattern. Our API has multiple filter combinations (`isRead`, `type`, pagination) that are expensive to model without GSIs. |

### Why PostgreSQL Wins

| Requirement | How PostgreSQL Satisfies It |
|---|---|
| **Structured, consistent schema** | Notifications and preferences are fixed-schema entities. ACID guarantees prevent double-delivery or double-marking. |
| **Complex filtering** | `WHERE user_id = ? AND is_read = ? AND type = ?` with compound indexes is efficient and natural. |
| **Partial updates** | `UPDATE ... SET is_read = true, read_at = now() WHERE id = ? AND user_id = ?` is atomic. |
| **COUNT queries** | `SELECT COUNT(*) WHERE user_id = ? AND is_read = false` is O(index) with a partial index. |
| **Bulk operations** | `UPDATE ... WHERE id = ANY(ARRAY[...])` and `INSERT ... ON CONFLICT` are first-class. |
| **TTL / expiry cleanup** | A scheduled `DELETE WHERE expires_at < now()` job is trivial. |
| **Horizontal read scaling** | Read replicas can serve the high-volume list and count queries. |
| **JSON support** | `JSONB` column for metadata / extensibility without breaking the schema. |
| **Mature ecosystem** | pgBouncer (connection pooling), pg_partman (partitioning), Flyway (migrations), PgHero (monitoring). |

### Supporting Layer: **Redis**

Redis sits alongside PostgreSQL as a caching and counter layer — it is not a replacement for durable storage.

| Redis Role | Detail |
|---|---|
| **Unread count cache** | `HSET user:{userId}:unread total 7 critical 1 high 3` — O(1) reads for badge counts |
| **Recent notifications cache** | Cache the last 20 notifications per user with a 60-second TTL |
| **Rate limiting** | Prevent notification flooding per user/source service |
| **Pub/Sub broker** | `PUBLISH notifications:{userId} <payload>` for fan-out to WebSocket servers |

### Architecture Summary

```
[API Layer]
     │
     ├──► PostgreSQL (primary write + durable read)
     │         └── Read Replica (list/count queries)
     │
     └──► Redis
               ├── Unread count cache (Hash)
               ├── Recent notifications cache (List + TTL)
               └── Pub/Sub (real-time fan-out)
```

---

## 2. Complete Database Schema

### Design Principles
- UUIDs as primary keys — avoids sequential ID enumeration attacks and works across distributed inserts.
- All timestamps stored as `TIMESTAMPTZ` (timezone-aware).
- Enum types defined at the database level for data integrity.
- Partial indexes on high-selectivity filter columns to keep index size small.
- Table partitioning by month on `notifications` (see Section 3).

---

### Enum Type Definitions

```sql
-- Notification category
CREATE TYPE notification_type AS ENUM (
    'SYSTEM',
    'ALERT',
    'PROMOTION',
    'SOCIAL',
    'TRANSACTION',
    'REMINDER'
);

-- Urgency level
CREATE TYPE notification_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

-- What entity the notification refers to
CREATE TYPE reference_type AS ENUM (
    'ORDER',
    'POST',
    'USER',
    'PAYMENT',
    'PRODUCT',
    'GENERIC'
);
```

---

### Table: `notifications`

The core table. Every row is one notification for one user.

```sql
CREATE TABLE notifications (
    -- Identity
    id               UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Ownership
    user_id          VARCHAR(128)          NOT NULL,

    -- Content
    title            VARCHAR(100)          NOT NULL,
    message          VARCHAR(1000)         NOT NULL,
    type             notification_type     NOT NULL,
    priority         notification_priority NOT NULL DEFAULT 'MEDIUM',

    -- State
    is_read          BOOLEAN               NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,

    -- Deep linking
    action_url       VARCHAR(500),

    -- Origin tracing
    source_service   VARCHAR(100),

    -- Related entity
    reference_id     VARCHAR(128),
    reference_type   reference_type,

    -- Extensible metadata (arbitrary JSON from the producer)
    metadata         JSONB,

    -- Constraints
    CONSTRAINT chk_read_at CHECK (
        (is_read = FALSE AND read_at IS NULL) OR
        (is_read = TRUE  AND read_at IS NOT NULL)
    ),
    CONSTRAINT chk_expires_after_created CHECK (
        expires_at IS NULL OR expires_at > created_at
    )
);

COMMENT ON TABLE  notifications              IS 'One row per notification per recipient user.';
COMMENT ON COLUMN notifications.user_id      IS 'Recipient user identifier from the auth service.';
COMMENT ON COLUMN notifications.metadata     IS 'Arbitrary producer-supplied key/value pairs stored as JSONB.';
COMMENT ON COLUMN notifications.expires_at   IS 'If set, the notification is eligible for purge after this time.';
```

#### Indexes on `notifications`

```sql
-- PRIMARY access pattern: inbox for a user, newest-first
CREATE INDEX idx_notif_user_created
    ON notifications (user_id, created_at DESC);

-- Filtered inbox: unread only (partial index — only indexes FALSE rows)
CREATE INDEX idx_notif_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;

-- Filtered inbox: by type
CREATE INDEX idx_notif_user_type
    ON notifications (user_id, type, created_at DESC);

-- Unread count queries (covering index for COUNT)
CREATE INDEX idx_notif_user_unread_priority
    ON notifications (user_id, priority)
    WHERE is_read = FALSE;

-- TTL cleanup job
CREATE INDEX idx_notif_expires
    ON notifications (expires_at)
    WHERE expires_at IS NOT NULL;

-- Reference lookup (find all notifications about a given order)
CREATE INDEX idx_notif_reference
    ON notifications (reference_id, reference_type)
    WHERE reference_id IS NOT NULL;
```

---

### Table: `notification_preferences`

One row per user, created on first access with sensible defaults.

```sql
CREATE TABLE notification_preferences (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             VARCHAR(128) NOT NULL UNIQUE,

    -- Delivery channel toggles
    email_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    push_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    in_app_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    sms_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Per-type opt-outs
    system_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    promotion_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    social_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    transaction_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    reminder_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Quiet hours (NULL means no quiet hours)
    quiet_hours_start   TIME,
    quiet_hours_end     TIME,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  notification_preferences             IS 'Per-user notification delivery preferences.';
COMMENT ON COLUMN notification_preferences.user_id     IS 'Unique user identifier — one preference row per user.';
COMMENT ON COLUMN notification_preferences.quiet_hours_start IS 'Start of do-not-disturb window (local time, e.g. 22:00).';

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

---

### Table: `notification_delivery_log`

Audit trail for multi-channel delivery (email, push, SMS). Kept separate from the main table to avoid row bloat.

```sql
CREATE TABLE notification_delivery_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID         NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL,      -- 'EMAIL', 'PUSH', 'IN_APP', 'SMS'
    status          VARCHAR(20)  NOT NULL,      -- 'SENT', 'FAILED', 'SKIPPED'
    attempted_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    error_message   TEXT,

    CONSTRAINT chk_channel CHECK (channel IN ('EMAIL','PUSH','IN_APP','SMS')),
    CONSTRAINT chk_status  CHECK (status  IN ('SENT','FAILED','SKIPPED'))
);

CREATE INDEX idx_delivery_log_notification
    ON notification_delivery_log (notification_id);

CREATE INDEX idx_delivery_log_status
    ON notification_delivery_log (status, attempted_at DESC)
    WHERE status = 'FAILED';
```

---

### Entity-Relationship Overview

```
notification_preferences          notifications
┌────────────────────┐           ┌───────────────────────────┐
│ id (PK)            │           │ id (PK)                   │
│ user_id (UNIQUE)   │           │ user_id                   │
│ email_enabled      │           │ title                     │
│ push_enabled       │           │ message                   │
│ in_app_enabled     │           │ type                      │
│ sms_enabled        │           │ priority                  │
│ system_enabled     │           │ is_read                   │
│ alert_enabled      │           │ created_at                │
│ promotion_enabled  │           │ read_at                   │
│ social_enabled     │           │ expires_at                │
│ transaction_enabled│           │ action_url                │
│ reminder_enabled   │           │ source_service            │
│ quiet_hours_start  │           │ reference_id              │
│ quiet_hours_end    │           │ reference_type            │
│ updated_at         │           │ metadata (JSONB)          │
└────────────────────┘           └───────────┬───────────────┘
                                             │ 1
                                             │
                                             ▼ N
                              notification_delivery_log
                              ┌───────────────────────────┐
                              │ id (PK)                   │
                              │ notification_id (FK)      │
                              │ channel                   │
                              │ status                    │
                              │ attempted_at              │
                              │ error_message             │
                              └───────────────────────────┘
```

---

## 3. Scalability Problems & Solutions

### Problem 1 — Table Bloat at Scale

**Scenario:** At 1 million active users each receiving 10 notifications/day, the `notifications` table accumulates ~300 million rows per month. Sequential scans on un-partitioned tables become slow. `VACUUM` and `ANALYZE` operations stall. Index maintenance overhead grows.

**Solution: Range Partitioning by Month**

```sql
-- Convert notifications to a partitioned table (Postgres 10+)
CREATE TABLE notifications (
    id              UUID                  NOT NULL DEFAULT gen_random_uuid(),
    user_id         VARCHAR(128)          NOT NULL,
    title           VARCHAR(100)          NOT NULL,
    message         VARCHAR(1000)         NOT NULL,
    type            notification_type     NOT NULL,
    priority        notification_priority NOT NULL DEFAULT 'MEDIUM',
    is_read         BOOLEAN               NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ           NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    action_url      VARCHAR(500),
    source_service  VARCHAR(100),
    reference_id    VARCHAR(128),
    reference_type  reference_type,
    metadata        JSONB,
    PRIMARY KEY (id, created_at)         -- partition key must be in PK
) PARTITION BY RANGE (created_at);

-- Create partitions (can be automated with pg_partman)
CREATE TABLE notifications_2025_01
    PARTITION OF notifications
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE notifications_2025_02
    PARTITION OF notifications
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Indexes are defined per-partition automatically once created on the parent
```

**Result:** Each query with a `created_at` filter hits only the relevant monthly partition. Old partitions can be detached and archived to cold storage (e.g. S3 via `pg_dump`) or dropped entirely without locking the live table.

---

### Problem 2 — Slow COUNT Queries for Unread Badges

**Scenario:** `SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE` is called on every page load for the badge counter. Under heavy load this becomes a bottleneck even with indexes.

**Solution: Redis Counter Cache**

Instead of hitting PostgreSQL for every badge poll, maintain a per-user counter in Redis.

```
-- Redis data structure
HSET user:{userId}:unread  total 7  critical 1  high 3

-- On notification created:
HINCRBY user:{userId}:unread total 1
HINCRBY user:{userId}:unread {priority_bucket} 1

-- On mark-read (single):
HINCRBY user:{userId}:unread total -1
HINCRBY user:{userId}:unread {priority_bucket} -1

-- On mark-all-read:
DEL user:{userId}:unread
```

The Redis counter is the source of truth for the unread-count endpoint. It is re-seeded from PostgreSQL on cache miss (e.g. after a server restart).

---

### Problem 3 — Write Throughput on Bulk Broadcasts

**Scenario:** A broadcast to 5 million users (e.g. a platform-wide maintenance notice) requires 5 million `INSERT` statements. Single-row inserts at this volume will saturate the database.

**Solution: Batch Inserts + Async Queue**

```sql
-- PostgreSQL supports multi-row INSERT — send in batches of 500
INSERT INTO notifications (user_id, title, message, type, priority, source_service)
VALUES
    ('user-001', 'Maintenance', 'Scheduled downtime Jan 12.', 'SYSTEM', 'HIGH', 'platform-ops'),
    ('user-002', 'Maintenance', 'Scheduled downtime Jan 12.', 'SYSTEM', 'HIGH', 'platform-ops'),
    -- ... up to 500 rows per statement
ON CONFLICT DO NOTHING;
```

At the application layer, the bulk endpoint publishes messages to a queue (Kafka or RabbitMQ). Worker threads consume batches and insert in chunks of 500 rows, providing back-pressure and retry capability.

---

### Problem 4 — Index Bloat on `is_read`

**Scenario:** `is_read` flips from `FALSE` to `TRUE` for most rows eventually. A standard B-tree index on `is_read` becomes ineffective because it indexes all values. Frequent `UPDATE` operations cause index bloat.

**Solution: Partial Index (Already Applied)**

The schema uses a **partial index** that only indexes unread rows:

```sql
CREATE INDEX idx_notif_user_unread
    ON notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;
```

This index stays small (only unread rows), is fast to scan, and automatically shrinks as notifications are marked read. No additional maintenance needed.

---

### Problem 5 — Hot Partitions / User Skew

**Scenario:** A celebrity account or a high-traffic bot user receives millions of notifications, creating a hot row in the index for that `user_id`.

**Solution: Application-Level Rate Limiting + Per-User Archival**

- Rate limit notification creation per `(sourceService, userId)` pair using Redis token buckets.
- For power users, automatically archive notifications older than 30 days to a separate `notifications_archive` table (same schema, no active indexes).

```sql
-- Archive old notifications for a given user
INSERT INTO notifications_archive
    SELECT * FROM notifications
    WHERE user_id = 'power-user-id'
      AND created_at < now() - INTERVAL '30 days';

DELETE FROM notifications
    WHERE user_id = 'power-user-id'
      AND created_at < now() - INTERVAL '30 days';
```

---

### Problem 6 — Connection Pool Exhaustion

**Scenario:** At high concurrency, thousands of API threads each hold a PostgreSQL connection, exhausting the server's connection limit (typically 100–200).

**Solution: PgBouncer in Transaction Mode**

Deploy PgBouncer between the Spring Boot application and PostgreSQL. In **transaction pooling** mode, a database connection is borrowed only for the duration of a single transaction, allowing thousands of application threads to share a small pool of real database connections.

```
Spring Boot (N threads)  →  PgBouncer (pool: 50 conns)  →  PostgreSQL (max_connections: 100)
```

---

### Scalability Solutions Summary

| Problem | Root Cause | Solution |
|---|---|---|
| Table bloat (300M+ rows) | Unbounded growth | Monthly range partitioning + cold archival |
| Slow COUNT for badges | Full index scan per request | Redis HINCRBY counter cache |
| Bulk broadcast write spikes | 5M single INSERTs | Async queue + batch inserts (500 rows/stmt) |
| Index bloat on `is_read` | Index covers all values | Partial index WHERE is_read = FALSE |
| Hot user partitions | Uneven data distribution | Rate limiting + per-user archival |
| Connection exhaustion | Thread-per-connection model | PgBouncer transaction pooling |

---

## 4. SQL Queries for All REST API Operations

All queries are written for PostgreSQL. Named parameters use `:param` notation (Spring Data / JDBC template style).

---

### API 1 — `POST /api/v1/notifications` (Create Notification)

```sql
INSERT INTO notifications (
    user_id,
    title,
    message,
    type,
    priority,
    action_url,
    source_service,
    reference_id,
    reference_type,
    expires_at,
    metadata
)
VALUES (
    :userId,
    :title,
    :message,
    :type::notification_type,
    :priority::notification_priority,
    :actionUrl,
    :sourceService,
    :referenceId,
    :referenceType::reference_type,
    :expiresAt,
    :metadata::jsonb
)
RETURNING *;

-- Simultaneously update the Redis unread counter (application layer):
-- HINCRBY user:{userId}:unread total 1
-- HINCRBY user:{userId}:unread {priority} 1
```

---

### API 2 — `POST /api/v1/notifications/bulk` (Bulk Create)

```sql
-- Executed in batches of 500. Example for 3 users:
INSERT INTO notifications (
    user_id, title, message, type, priority,
    action_url, source_service, expires_at
)
VALUES
    ('user-001', :title, :message, :type::notification_type, :priority::notification_priority, :actionUrl, :sourceService, :expiresAt),
    ('user-002', :title, :message, :type::notification_type, :priority::notification_priority, :actionUrl, :sourceService, :expiresAt),
    ('user-003', :title, :message, :type::notification_type, :priority::notification_priority, :actionUrl, :sourceService, :expiresAt)
ON CONFLICT DO NOTHING
RETURNING id, user_id;
```

---

### API 3 — `GET /api/v1/notifications/users/{userId}` (List — All Filters)

```sql
-- Case A: No filters (all notifications, paginated)
SELECT *
FROM   notifications
WHERE  user_id = :userId
ORDER BY created_at DESC
LIMIT  :size
OFFSET :page * :size;

-- Case B: Filter by isRead only
SELECT *
FROM   notifications
WHERE  user_id = :userId
  AND  is_read = :isRead
ORDER BY created_at DESC
LIMIT  :size
OFFSET :page * :size;

-- Case C: Filter by type only
SELECT *
FROM   notifications
WHERE  user_id = :userId
  AND  type = :type::notification_type
ORDER BY created_at DESC
LIMIT  :size
OFFSET :page * :size;

-- Case D: Filter by both isRead and type
SELECT *
FROM   notifications
WHERE  user_id = :userId
  AND  is_read = :isRead
  AND  type    = :type::notification_type
ORDER BY created_at DESC
LIMIT  :size
OFFSET :page * :size;

-- Total count for pagination metadata (runs alongside the data query)
SELECT COUNT(*)
FROM   notifications
WHERE  user_id = :userId
  -- add same AND clauses as the data query above
;
```

---

### API 4 — `GET /api/v1/notifications/{id}` (Get Single Notification)

```sql
SELECT *
FROM   notifications
WHERE  id      = :id::uuid
  AND  user_id = :userId;  -- ownership check in the same query
```

---

### API 5 — `GET /api/v1/notifications/users/{userId}/unread-count`

```sql
-- Primary path: served from Redis HGETALL user:{userId}:unread

-- Cache-miss fallback (re-seeds the Redis hash):
SELECT
    COUNT(*)                                           AS total_unread,
    COUNT(*) FILTER (WHERE priority = 'CRITICAL')      AS critical_unread,
    COUNT(*) FILTER (WHERE priority = 'HIGH')          AS high_unread
FROM   notifications
WHERE  user_id = :userId
  AND  is_read = FALSE;
```

---

### API 6 — `PATCH /api/v1/notifications/{id}/read` (Mark Single as Read)

```sql
UPDATE notifications
SET    is_read = TRUE,
       read_at = now()
WHERE  id      = :id::uuid
  AND  user_id = :userId
  AND  is_read = FALSE       -- idempotent: no-op if already read
RETURNING *;

-- Then decrement Redis:
-- HINCRBY user:{userId}:unread total -1
-- HINCRBY user:{userId}:unread {priority} -1
```

---

### API 7 — `PATCH /api/v1/notifications/users/{userId}/read-all` (Mark All as Read)

```sql
UPDATE notifications
SET    is_read = TRUE,
       read_at = now()
WHERE  user_id = :userId
  AND  is_read = FALSE;

-- Returns: number of rows affected (used in the API response message)

-- Then reset Redis counter:
-- DEL user:{userId}:unread
```

---

### API 8 — `PATCH /api/v1/notifications/bulk/read` (Bulk Mark as Read)

```sql
UPDATE notifications
SET    is_read = TRUE,
       read_at = now()
WHERE  id      = ANY(:notificationIds::uuid[])
  AND  user_id = :userId        -- ownership enforced on all rows at once
  AND  is_read = FALSE
RETURNING id;

-- Application compares RETURNING ids against :notificationIds
-- to determine which succeeded and which were not found / already read.
```

---

### API 9 — `DELETE /api/v1/notifications/{id}` (Delete Notification)

```sql
DELETE FROM notifications
WHERE  id      = :id::uuid
  AND  user_id = :userId
RETURNING id, is_read, priority;

-- If the deleted row was unread, decrement Redis counter:
-- HINCRBY user:{userId}:unread total -1
-- HINCRBY user:{userId}:unread {priority} -1
```

---

### API 10 — `GET /api/v1/notifications/users/{userId}/preferences`

```sql
-- Returns row if it exists; application creates defaults if not found
SELECT *
FROM   notification_preferences
WHERE  user_id = :userId;

-- If no row found, insert defaults and return:
INSERT INTO notification_preferences (user_id)
VALUES (:userId)
ON CONFLICT (user_id) DO NOTHING
RETURNING *;
```

---

### API 11 — `PATCH /api/v1/notifications/users/{userId}/preferences` (Update Preferences)

```sql
-- UPSERT — creates the row on first update; only sets provided fields.
-- The application builds the SET clause dynamically for non-null fields.
INSERT INTO notification_preferences (user_id, email_enabled, push_enabled,
    in_app_enabled, sms_enabled, system_enabled, alert_enabled,
    promotion_enabled, social_enabled, transaction_enabled,
    reminder_enabled, quiet_hours_start, quiet_hours_end)
VALUES (:userId, :emailEnabled, :pushEnabled, :inAppEnabled, :smsEnabled,
    :systemEnabled, :alertEnabled, :promotionEnabled, :socialEnabled,
    :transactionEnabled, :reminderEnabled,
    :quietHoursStart::time, :quietHoursEnd::time)
ON CONFLICT (user_id) DO UPDATE
    SET email_enabled       = COALESCE(EXCLUDED.email_enabled,       notification_preferences.email_enabled),
        push_enabled        = COALESCE(EXCLUDED.push_enabled,        notification_preferences.push_enabled),
        in_app_enabled      = COALESCE(EXCLUDED.in_app_enabled,      notification_preferences.in_app_enabled),
        sms_enabled         = COALESCE(EXCLUDED.sms_enabled,         notification_preferences.sms_enabled),
        system_enabled      = COALESCE(EXCLUDED.system_enabled,      notification_preferences.system_enabled),
        alert_enabled       = COALESCE(EXCLUDED.alert_enabled,       notification_preferences.alert_enabled),
        promotion_enabled   = COALESCE(EXCLUDED.promotion_enabled,   notification_preferences.promotion_enabled),
        social_enabled      = COALESCE(EXCLUDED.social_enabled,      notification_preferences.social_enabled),
        transaction_enabled = COALESCE(EXCLUDED.transaction_enabled, notification_preferences.transaction_enabled),
        reminder_enabled    = COALESCE(EXCLUDED.reminder_enabled,    notification_preferences.reminder_enabled),
        quiet_hours_start   = COALESCE(EXCLUDED.quiet_hours_start,   notification_preferences.quiet_hours_start),
        quiet_hours_end     = COALESCE(EXCLUDED.quiet_hours_end,     notification_preferences.quiet_hours_end),
        updated_at          = now()
RETURNING *;
```

---

### Scheduled — Expired Notification Purge (runs nightly at 2 AM)

```sql
DELETE FROM notifications
WHERE  expires_at IS NOT NULL
  AND  expires_at < now()
RETURNING id, user_id;

-- Application uses RETURNING to clean up any Redis counters
-- for rows that were still unread at expiry time.
```

---

### Bonus — Delivery Log Insert (written by each channel dispatcher)

```sql
INSERT INTO notification_delivery_log (notification_id, channel, status, error_message)
VALUES (:notificationId::uuid, :channel, :status, :errorMessage);
```

### Bonus — Fetch Failed Deliveries for Retry

```sql
SELECT ndl.*, n.user_id, n.title, n.type
FROM   notification_delivery_log ndl
JOIN   notifications n ON n.id = ndl.notification_id
WHERE  ndl.status       = 'FAILED'
  AND  ndl.attempted_at > now() - INTERVAL '1 hour'
ORDER BY ndl.attempted_at ASC
LIMIT  100;
```
