const API_PREFIX = "/gateway";

const gatewayStatus = document.getElementById("gateway-status");
const inventoryFeedback = document.getElementById("inventory-feedback");
const orderFeedback = document.getElementById("order-feedback");
const flowFeedback = document.getElementById("flow-feedback");
const availabilityFeedback = document.getElementById("availability-feedback");

const inventoryList = document.getElementById("inventory-list");
const createdOrderCard = document.getElementById("created-order-card");

const flowOrderView = document.getElementById("flow-order");
const flowPaymentView = document.getElementById("flow-payment");
const flowShippingView = document.getElementById("flow-shipping");
const flowNotifyOrderView = document.getElementById("flow-notify-order");
const flowNotifyCustomerView = document.getElementById("flow-notify-customer");

const sagaOrderNode = document.getElementById("saga-order");
const sagaInventoryNode = document.getElementById("saga-inventory");
const sagaPaymentNode = document.getElementById("saga-payment");
const sagaShippingNode = document.getElementById("saga-shipping");
const sagaNotificationNode = document.getElementById("saga-notification");

const orderCustomerIdInput = document.getElementById("order-customer-id");
const flowCustomerIdInput = document.getElementById("flow-customer-id");

const moneyFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function setStatus(element, kind, text) {
  element.className = `status status-${kind}`;
  element.textContent = text;
}

function parseError(error) {
  if (error && typeof error === "object" && "message" in error) {
    return error.message;
  }
  return String(error);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function row(label, value, mono = false) {
  const klass = mono ? ' class="mono"' : "";
  return `<li><span>${escapeHtml(label)}</span><strong${klass}>${escapeHtml(value ?? "-")}</strong></li>`;
}

function toMoney(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return value == null ? "-" : String(value);
  }
  return moneyFormatter.format(numeric);
}

function toLabelKind(status) {
  const value = (status || "").toUpperCase();
  if (!value) return "neutral";
  if (value.includes("FAIL") || value.includes("CANCEL") || value.includes("ERROR")) return "error";
  if (value.includes("CHECK") || value.includes("PROCESS") || value.includes("PENDING")) return "warn";
  if (value.includes("COMPLETE") || value.includes("RESERVE") || value.includes("SHIP") || value.includes("SENT")) return "ok";
  return "neutral";
}

function makeBadge(status, forcedKind) {
  const kind = forcedKind || toLabelKind(status);
  return `<span class="badge badge-${kind}">${escapeHtml(status || "UNKNOWN")}</span>`;
}

function setEmptyCard(element, message) {
  element.className = "entity-card empty";
  element.textContent = message;
}

function parseHttpErrorText(message) {
  const raw = String(message || "");
  const match = raw.match(/^HTTP\s+(\d+):\s*([\s\S]*)$/);
  if (!match) {
    return { code: null, summary: raw };
  }

  const code = Number(match[1]);
  const body = match[2] || "";

  try {
    const parsed = JSON.parse(body);
    const summary = parsed.error || parsed.message || parsed.path || body;
    return { code, summary: String(summary) };
  } catch (_err) {
    return { code, summary: body.trim() || `HTTP ${code}` };
  }
}

function renderUnavailableCard(element, label, error) {
  const parsed = parseHttpErrorText(parseError(error));
  const status = parsed.code ? `HTTP ${parsed.code}` : "Unavailable";

  element.className = "entity-card";
  element.innerHTML = `
    <div class="entity-head">
      <strong>${escapeHtml(label)}</strong>
      <span class="badge badge-error">${escapeHtml(status)}</span>
    </div>
    <p class="entity-note">${escapeHtml(parsed.summary || "No response")}</p>
  `;
}

function renderOrderCard(element, order) {
  if (!order || typeof order !== "object") {
    setEmptyCard(element, "No order data.");
    return;
  }

  const items = Array.isArray(order.items) ? order.items : [];
  const firstItem = items[0];

  element.className = "entity-card";
  element.innerHTML = `
    <div class="entity-head">
      ${makeBadge(order.status)}
      <span class="mono">${escapeHtml(order.orderId || "-")}</span>
    </div>
    <ul class="data-list">
      ${row("Customer", order.customerId, true)}
      ${row("Total", toMoney(order.totalAmount))}
      ${row("Items", String(items.length))}
      ${row("Updated", order.lastModifiedAt || order.createdAt || "-")}
    </ul>
    ${firstItem ? `<p class="entity-note">First item: ${escapeHtml(firstItem.productName || "Item")} x ${escapeHtml(firstItem.quantity || 0)}</p>` : ""}
  `;
}

function renderPaymentCard(element, payment) {
  if (!payment || typeof payment !== "object") {
    setEmptyCard(element, "No payment data yet.");
    return;
  }

  element.className = "entity-card";
  element.innerHTML = `
    <div class="entity-head">
      ${makeBadge(payment.status)}
      <span class="mono">${escapeHtml(payment.id || "-")}</span>
    </div>
    <ul class="data-list">
      ${row("Order", payment.orderId, true)}
      ${row("Amount", toMoney(payment.amount))}
      ${row("Method", payment.paymentMethod || "-")}
      ${row("Transaction", payment.transactionId || "-")}
    </ul>
  `;
}

function renderShippingCard(element, shipment) {
  if (!shipment || typeof shipment !== "object") {
    setEmptyCard(element, "No shipment data yet.");
    return;
  }

  element.className = "entity-card";
  element.innerHTML = `
    <div class="entity-head">
      ${makeBadge(shipment.status)}
      <span class="mono">${escapeHtml(shipment.id || "-")}</span>
    </div>
    <ul class="data-list">
      ${row("Order", shipment.orderId, true)}
      ${row("Carrier", shipment.carrierName || "-")}
      ${row("Tracking", shipment.trackingNumber || "-")}
      ${row("Est. delivery", shipment.estimatedDeliveryDate || "-")}
    </ul>
  `;
}

function renderNotificationCard(element, notifications, emptyMessage) {
  if (!Array.isArray(notifications) || notifications.length === 0) {
    setEmptyCard(element, emptyMessage);
    return;
  }

  const latest = notifications[notifications.length - 1];
  const sentCount = notifications.filter((item) => String(item.status || "").toUpperCase() === "SENT").length;

  element.className = "entity-card";
  element.innerHTML = `
    <div class="entity-head">
      ${makeBadge(`${sentCount}/${notifications.length} sent`, sentCount > 0 ? "ok" : "warn")}
      <span class="mono">${escapeHtml(latest.id || "-")}</span>
    </div>
    <ul class="data-list">
      ${row("Latest type", latest.type || "-")}
      ${row("Latest status", latest.status || "-")}
      ${row("Latest subject", latest.subject || "-")}
      ${row("Last sent", latest.sentAt || latest.createdAt || "-")}
    </ul>
  `;
}

function setSagaNode(element, kind, detail) {
  element.className = `saga-node saga-${kind}`;
  const detailNode = element.querySelector("span");
  if (detailNode) {
    detailNode.textContent = detail;
  }
}

function resetSagaLane() {
  setSagaNode(sagaOrderNode, "wait", "Awaiting lookup");
  setSagaNode(sagaInventoryNode, "wait", "Pending reservation");
  setSagaNode(sagaPaymentNode, "wait", "Pending payment event");
  setSagaNode(sagaShippingNode, "wait", "Pending shipment event");
  setSagaNode(sagaNotificationNode, "wait", "Pending outbound message");
}

function updateSagaLane(orderResult, paymentResult, shippingResult, notifyOrderResult) {
  const order = orderResult.status === "fulfilled" ? orderResult.value : null;
  const payment = paymentResult.status === "fulfilled" ? paymentResult.value : null;
  const shipping = shippingResult.status === "fulfilled" ? shippingResult.value : null;
  const orderStatus = String(order?.status || "").toUpperCase();

  if (order) {
    const kind = toLabelKind(order.status);
    setSagaNode(sagaOrderNode, kind === "neutral" ? "ok" : kind, order.status || "Loaded");
  } else {
    setSagaNode(sagaOrderNode, "error", "Order lookup failed");
  }

  if (!orderStatus) {
    setSagaNode(sagaInventoryNode, "wait", "Pending order read");
  } else if (["PENDING", "INVENTORY_CHECKING"].includes(orderStatus)) {
    setSagaNode(sagaInventoryNode, "warn", "Checking stock");
  } else if (orderStatus === "CANCELLED") {
    setSagaNode(sagaInventoryNode, "error", "Reservation cancelled");
  } else if (orderStatus === "FAILED") {
    setSagaNode(sagaInventoryNode, "warn", "Compensation applied");
  } else {
    setSagaNode(sagaInventoryNode, "ok", "Inventory reserved");
  }

  if (payment) {
    const paymentStatus = String(payment.status || "").toUpperCase();
    if (paymentStatus === "COMPLETED") {
      setSagaNode(sagaPaymentNode, "ok", "Payment completed");
    } else if (paymentStatus === "FAILED") {
      setSagaNode(sagaPaymentNode, "error", "Payment failed");
    } else {
      setSagaNode(sagaPaymentNode, "warn", payment.status || "Payment pending");
    }
  } else if (["INVENTORY_CHECKING", "INVENTORY_RESERVED", "PAYMENT_PROCESSING"].includes(orderStatus)) {
    setSagaNode(sagaPaymentNode, "warn", "Awaiting payment event");
  } else if (["FAILED", "CANCELLED"].includes(orderStatus)) {
    setSagaNode(sagaPaymentNode, "warn", "No payment record");
  } else {
    setSagaNode(sagaPaymentNode, "wait", "Pending payment event");
  }

  if (shipping) {
    setSagaNode(sagaShippingNode, "ok", shipping.status || "Shipment created");
  } else if (payment && String(payment.status || "").toUpperCase() === "COMPLETED") {
    setSagaNode(sagaShippingNode, "warn", "Waiting for shipping event");
  } else if (payment && String(payment.status || "").toUpperCase() === "FAILED") {
    setSagaNode(sagaShippingNode, "warn", "Skipped after payment failure");
  } else if (["SHIPPED", "COMPLETED"].includes(orderStatus)) {
    setSagaNode(sagaShippingNode, "error", "Shipment record missing");
  } else {
    setSagaNode(sagaShippingNode, "wait", "Pending shipment event");
  }

  if (notifyOrderResult.status === "fulfilled") {
    const notifications = Array.isArray(notifyOrderResult.value) ? notifyOrderResult.value : [];
    if (notifications.length > 0) {
      setSagaNode(sagaNotificationNode, "ok", `${notifications.length} message(s)`);
    } else {
      setSagaNode(sagaNotificationNode, "warn", "No notifications yet");
    }
  } else {
    setSagaNode(sagaNotificationNode, "warn", "Notification service unavailable");
  }
}

function generateUuid() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }

  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (ch) => {
    const rand = Math.floor(Math.random() * 16);
    const value = ch === "x" ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
}

async function request(path, options = {}) {
  const response = await fetch(`${API_PREFIX}${path}`, {
    method: options.method || "GET",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${typeof payload === "string" ? payload : JSON.stringify(payload, null, 2)}`);
  }

  return payload;
}

function renderInventory(items) {
  if (!Array.isArray(items) || items.length === 0) {
    inventoryList.textContent = "Inventory is empty.";
    return;
  }

  const rows = items
    .map((item) => {
      const safeId = escapeHtml(item.id || "");
      const safeName = escapeHtml(item.name || "");
      const encodedName = encodeURIComponent(item.name || "");
      const available = item.availableQuantity ?? 0;
      const reserved = item.reservedQuantity ?? 0;
      const total = available + reserved;
      return `
        <tr>
          <td><code>${safeId}</code></td>
          <td>${safeName}</td>
          <td>${escapeHtml(available)}</td>
          <td>${escapeHtml(reserved)}</td>
          <td>${escapeHtml(total)}</td>
          <td><button class="btn btn-small use-item" type="button" data-id="${safeId}" data-name="${encodedName}">Use</button></td>
        </tr>
      `;
    })
    .join("");

  inventoryList.innerHTML = `
    <table class="inventory-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Name</th>
          <th>Available</th>
          <th>Reserved</th>
          <th>Total</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

async function loadInventory() {
  try {
    const items = await request("/api/inventory");
    renderInventory(items);
    setStatus(inventoryFeedback, "ok", `Loaded ${items.length} inventory item(s).`);
  } catch (error) {
    setStatus(inventoryFeedback, "error", `Failed to load inventory: ${parseError(error)}`);
  }
}

async function checkGatewayHealth() {
  try {
    const data = await request("/actuator/health", { headers: {} });
    setStatus(gatewayStatus, "ok", `Gateway: ${data.status || "UP"}`);
  } catch (error) {
    setStatus(gatewayStatus, "error", `Gateway unreachable: ${parseError(error)}`);
  }
}

document.getElementById("check-gateway").addEventListener("click", checkGatewayHealth);

document.getElementById("generate-order-customer-id").addEventListener("click", () => {
  const uuid = generateUuid();
  orderCustomerIdInput.value = uuid;
  flowCustomerIdInput.value = uuid;
  setStatus(orderFeedback, "ok", `Generated customer UUID: ${uuid}`);
});

document.getElementById("generate-flow-customer-id").addEventListener("click", () => {
  const uuid = generateUuid();
  flowCustomerIdInput.value = uuid;
  setStatus(flowFeedback, "ok", `Generated customer UUID: ${uuid}`);
});

document.getElementById("refresh-inventory").addEventListener("click", loadInventory);

document.getElementById("inventory-form").addEventListener("submit", async (event) => {
  event.preventDefault();

  const payload = {
    name: document.getElementById("inventory-name").value.trim(),
    description: document.getElementById("inventory-description").value.trim(),
    quantity: Number(document.getElementById("inventory-quantity").value),
  };

  try {
    const created = await request("/api/inventory", { method: "POST", body: payload });
    setStatus(inventoryFeedback, "ok", `Inventory item created: ${created.id}`);
    document.getElementById("order-product-id").value = created.id;
    document.getElementById("check-product-id").value = created.id;
    document.getElementById("order-product-name").value = created.name;
    await loadInventory();
  } catch (error) {
    setStatus(inventoryFeedback, "error", `Create failed: ${parseError(error)}`);
  }
});

inventoryList.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement) || !target.classList.contains("use-item")) {
    return;
  }

  const id = target.getAttribute("data-id") || "";
  const name = decodeURIComponent(target.getAttribute("data-name") || "");
  document.getElementById("order-product-id").value = id;
  document.getElementById("order-product-name").value = name;
  document.getElementById("check-product-id").value = id;
  setStatus(orderFeedback, "ok", `Prefilled product ${name} (${id})`);
});

document.getElementById("order-form").addEventListener("submit", async (event) => {
  event.preventDefault();

  const payload = {
    customerId: orderCustomerIdInput.value.trim(),
    items: [
      {
        productId: document.getElementById("order-product-id").value.trim(),
        productName: document.getElementById("order-product-name").value.trim(),
        quantity: Number(document.getElementById("order-quantity").value),
        price: Number(document.getElementById("order-price").value),
      },
    ],
  };

  try {
    const order = await request("/api/orders", { method: "POST", body: payload });
    renderOrderCard(createdOrderCard, order);
    setStatus(orderFeedback, "ok", `Order created: ${order.orderId}`);

    document.getElementById("flow-order-id").value = order.orderId;
    flowCustomerIdInput.value = order.customerId;
  } catch (error) {
    setStatus(orderFeedback, "error", `Order creation failed: ${parseError(error)}`);
    renderUnavailableCard(createdOrderCard, "Order creation", error);
  }
});

document.getElementById("flow-form").addEventListener("submit", async (event) => {
  event.preventDefault();

  const orderId = document.getElementById("flow-order-id").value.trim();
  const customerId = flowCustomerIdInput.value.trim();

  if (!orderId) {
    setStatus(flowFeedback, "error", "Order UUID is required.");
    return;
  }

  setStatus(flowFeedback, "neutral", "Fetching related service records...");

  const calls = [
    request(`/api/orders/${orderId}`),
    request(`/api/payments/order/${orderId}`),
    request(`/api/shipping/order/${orderId}`),
    request(`/api/notifications/order/${orderId}`),
  ];

  if (customerId) {
    calls.push(request(`/api/notifications/customer/${customerId}`));
  }

  const results = await Promise.allSettled(calls);
  const [orderResult, paymentResult, shippingResult, notifyOrderResult, notifyCustomerResult] = results;

  if (orderResult.status === "fulfilled") {
    renderOrderCard(flowOrderView, orderResult.value);
  } else {
    renderUnavailableCard(flowOrderView, "Order service", orderResult.reason);
  }

  if (paymentResult.status === "fulfilled") {
    renderPaymentCard(flowPaymentView, paymentResult.value);
  } else {
    renderUnavailableCard(flowPaymentView, "Payment service", paymentResult.reason);
  }

  if (shippingResult.status === "fulfilled") {
    renderShippingCard(flowShippingView, shippingResult.value);
  } else {
    renderUnavailableCard(flowShippingView, "Shipping service", shippingResult.reason);
  }

  if (notifyOrderResult.status === "fulfilled") {
    renderNotificationCard(flowNotifyOrderView, notifyOrderResult.value, "No notifications linked to this order yet.");
  } else {
    renderUnavailableCard(flowNotifyOrderView, "Notification service (order)", notifyOrderResult.reason);
  }

  if (customerId) {
    if (notifyCustomerResult && notifyCustomerResult.status === "fulfilled") {
      renderNotificationCard(flowNotifyCustomerView, notifyCustomerResult.value, "No notifications for this customer yet.");
    } else {
      renderUnavailableCard(
        flowNotifyCustomerView,
        "Notification service (customer)",
        notifyCustomerResult ? notifyCustomerResult.reason : "No result",
      );
    }
  } else {
    setEmptyCard(flowNotifyCustomerView, "Provide customer UUID to query recipient notifications.");
  }

  updateSagaLane(orderResult, paymentResult, shippingResult, notifyOrderResult);

  const successCount = results.filter((item) => item.status === "fulfilled").length;
  if (successCount === results.length) {
    setStatus(flowFeedback, "ok", `Loaded ${successCount}/${results.length} related records.`);
  } else if (successCount > 0) {
    setStatus(flowFeedback, "warn", `Loaded ${successCount}/${results.length}. Some events may still be in transit.`);
  } else {
    setStatus(flowFeedback, "error", "No records loaded. Verify IDs and service health.");
  }
});

document.getElementById("availability-form").addEventListener("submit", async (event) => {
  event.preventDefault();

  const productId = document.getElementById("check-product-id").value.trim();
  const quantity = Number(document.getElementById("check-quantity").value);

  try {
    const available = await request(
      `/api/inventory/check?productId=${encodeURIComponent(productId)}&quantity=${encodeURIComponent(quantity)}`,
    );

    if (available === true) {
      setStatus(availabilityFeedback, "ok", `Inventory is available for quantity ${quantity}.`);
    } else {
      setStatus(availabilityFeedback, "warn", `Inventory is NOT available for quantity ${quantity}.`);
    }
  } catch (error) {
    setStatus(availabilityFeedback, "error", `Availability check failed: ${parseError(error)}`);
  }
});

resetSagaLane();
setEmptyCard(createdOrderCard, "No order created yet.");
setEmptyCard(flowOrderView, "No data");
setEmptyCard(flowPaymentView, "No data");
setEmptyCard(flowShippingView, "No data");
setEmptyCard(flowNotifyOrderView, "No data");
setEmptyCard(flowNotifyCustomerView, "No data");

loadInventory();
checkGatewayHealth();
