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
