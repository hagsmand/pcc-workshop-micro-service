# feat: Graceful out-of-stock handling in Saga Tracking UI

> **Issue #1** · [View on GitHub](https://github.com/hagsmand/workshop-bob-micro/issues/1)  
> **Label:** `enhancement`  
> **Status:** Open

---

## Summary

When an order fails due to out-of-stock inventory, the saga stops at the inventory step — no payment or shipment record is ever created. The frontend's **"3) Track Saga Flow"** section still calls the payment and shipping endpoints, which currently throw `RuntimeException` and return **HTTP 500**, displaying confusing red error cards to the user.

This issue tracks the work to return user-friendly responses and update the frontend to render them correctly.

---

## Problem

- `GET /api/payments/order/{orderId}` → throws `RuntimeException` → **HTTP 500**
- `GET /api/shipping/order/{orderId}` → throws `RuntimeException` → **HTTP 500**

The frontend displays a red **"HTTP 500"** error card for both the Payment Service and Shipping Service panels, which is misleading — the order didn't fail due to a server error, it failed because the item was out of stock.

---

## Acceptance Criteria

- [ ] `GET /api/payments/order/{orderId}` returns **HTTP 200** with a structured JSON body when no payment record exists
- [ ] `GET /api/shipping/order/{orderId}` returns **HTTP 200** with a structured JSON body when no shipment record exists
- [ ] Payment response includes a descriptive message: _"Order was not completed — no payment was processed. This may be due to insufficient inventory stock."_
- [ ] Shipping response includes a descriptive message: _"Order was not completed — no shipment was created. This may be due to insufficient inventory stock or payment failure."_
- [ ] Frontend **Payment Service card** displays a `NOT APPLICABLE` badge and a `Message` row with the above text
- [ ] Frontend **Shipping Service card** displays a `NOT APPLICABLE` badge and a `Message` row with the above text
- [ ] Both cards are visually consistent (same badge style, same layout)
- [ ] **Saga lane — Inventory node** shows a red/error state indicating inventory reservation failed
- [ ] **Saga lane — Payment node** shows a neutral/grey state with text _"Skipped — order not completed"_
- [ ] **Saga lane — Shipping node** shows a neutral/grey state with text _"Skipped — order not completed"_
- [ ] No red HTTP error cards appear anywhere in the saga tracking section for this scenario
- [ ] No HTTP 4xx or 5xx codes are returned for the out-of-stock scenario

---

## Proposed Solution

### Backend (`payment-service` & `shipping-service`)

1. Replace the `RuntimeException` thrown when no record is found with a graceful HTTP 200 response containing a structured JSON body (e.g. `{ "status": "NOT_APPLICABLE", "message": "..." }`).
2. Create a custom exception class (e.g. `ResourceNotFoundException`) and a `GlobalExceptionHandler` (`@RestControllerAdvice`) that maps it to HTTP 200 with the friendly payload — or handle it directly in the service/controller layer.

### Frontend (`mvp-frontend/app.js`)

1. Detect the `NOT_APPLICABLE` status in the payment and shipping responses.
2. Render a `NOT APPLICABLE` badge and a `Message` row instead of a generic error card.
3. Update the saga lane node rendering to show the correct state for inventory failure, and skipped state for payment and shipping.

---

## Scope

- ✅ Modify `payment-service` backend (controller/service/exception handling)
- ✅ Modify `shipping-service` backend (controller/service/exception handling)
- ✅ Modify `mvp-frontend/app.js` (response rendering & saga lane diagram)
- ❌ Do **not** change saga event flow, Kafka topics, or order processing logic
- ❌ Do **not** modify `order-service`, `inventory-service`, `notification-service`

---

## Services Involved

| Service | Role |
|---|---|
| `order-service` | Creates orders and tracks saga state |
| `inventory-service` | Reserves stock; publishes failure event when out of stock |
| `payment-service` | Processes payment after inventory is reserved |
| `shipping-service` | Creates shipment after payment succeeds |
| `mvp-frontend` | Vanilla JS frontend with "Track Saga Flow" section |

---

## Labels

`enhancement` · `frontend` · `backend` · `ux`