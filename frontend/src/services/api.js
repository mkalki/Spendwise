
const API_BASE_URL = "/api";

let csrfToken;
const checkoutAttemptStorage = "spendwise_checkout_attempts";
const pendingCheckouts = new Map();

function readCheckoutAttempts() {
  return JSON.parse(sessionStorage.getItem(checkoutAttemptStorage) ?? "{}");
}

function saveCheckoutAttempts(attempts) {
  sessionStorage.setItem(checkoutAttemptStorage, JSON.stringify(attempts));
}

function clearSettledCheckout(checkout) {
  if (!["CAPTURED", "CANCELLED"].includes(checkout?.status)) return;
  const attempts = readCheckoutAttempts();
  const body = Object.keys(attempts).find((body) => attempts[body].checkoutId === checkout.checkoutSessionId);
  if (body !== undefined) {
    delete attempts[body];
    saveCheckoutAttempts(attempts);
  }
}

const unresolvedCheckoutCodes = new Set([
  "CHECKOUT_IN_PROGRESS",
  "CHECKOUT_REQUIRES_RECONCILIATION",
  "IDEMPOTENCY_KEY_REUSED",
]);
const retryableCheckoutStatuses = new Set([401, 403, 408, 425, 429]);

async function request(path, options = {}) {
  const method = options.method ?? "GET";
  const headers = {
    Accept: "application/json",
    ...options.headers,
  };

  if (method !== "GET" && method !== "HEAD") {
    csrfToken ??= await getCsrfToken();
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(
    `${API_BASE_URL}${path}`,
    {
      ...options,
      method,
      credentials: "include",
      headers,
    }
  );

  const text = await response.text();

  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const error = new Error(
      data?.message ||
      data?.detail ||
      `Request failed: ${response.status}`
    );
    error.status = response.status;
    error.code = data?.code;
    throw error;
  }

  return data;
}

async function getCsrfToken() {
  const data = await request("/auth/csrf");
  return data.token;
}

function toFrontendPolicy(data) {
  return {
    maxItemPrice: data.maxProductPrice,
    maxOrderAmount: data.maxOrderAmount,
    weeklySpendingLimit: data.weeklyBudget,
    maxOrdersPerWeek: data.maxOrdersPerWeek,
    currency: data.currencyCode,
    country: data.countryCode,
  };
}

function toBackendPolicy(policy) {
  return {
    maxProductPrice: Number(policy.maxItemPrice).toFixed(2),
    maxOrderAmount: Number(policy.maxOrderAmount).toFixed(2),
    weeklyBudget: Number(policy.weeklySpendingLimit).toFixed(2),
    maxOrdersPerWeek: Number(policy.maxOrdersPerWeek),
    countryCode: policy.country,
    currencyCode: policy.currency,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
  };
}

export const api = {

  async getCurrentUser() {
    return request("/auth/me");
  },

  async login(identifier, password) {
    const data = await request("/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ identifier, password }),
    });
    csrfToken = undefined;
    return data;
  },

  async signup(email, username, password) {
    const data = await request("/auth/signup", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, username, password }),
    });
    csrfToken = undefined;
    return data;
  },

  async logout() {
    await request("/auth/logout", { method: "POST" });
    csrfToken = undefined;
  },

  async getPolicy() {
    const data = await request("/v1/policies/active");
    return toFrontendPolicy(data);
  },

  async savePolicy(policy) {
    const data = await request("/v1/policies/active", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(toBackendPolicy(policy)),
    });
    return toFrontendPolicy(data);
  },

  async searchShopping(message) {
    return request("/v1/shopping/search", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ message, sortBy: "AI_RECOMMENDED" }),
    });
  },

  getCart() {
    return request("/v1/cart");
  },

  addToCart(candidateId) {
    return request(`/v1/cart/items/${candidateId}`, { method: "PUT" });
  },

  removeFromCart(candidateId) {
    return request(`/v1/cart/items/${candidateId}`, { method: "DELETE" });
  },

  getCheckoutDetails() {
    return request("/v1/cart/details");
  },

  saveCheckoutDetails(details) {
    return request("/v1/cart/details", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(details),
    });
  },

  async getTransactionSummary() {
    return request("/v1/transactions/summary");
  },

  async getTransactions() {
    return request("/v1/transactions");
  },

  async createCheckout(searchSessionId, candidateId) {
    const body = JSON.stringify({ searchSessionId, candidateId });
    if (pendingCheckouts.has(body)) return pendingCheckouts.get(body);

    const attempts = readCheckoutAttempts();
    if (!attempts[body]) {
      attempts[body] = {
        key: Array.from(crypto.getRandomValues(new Uint8Array(16)),
          (byte) => byte.toString(16).padStart(2, "0")).join(""),
      };
      // Save before sending so a reload or a lost response can replay the order request.
      saveCheckoutAttempts(attempts);
    }
    const { key } = attempts[body];
    const pending = request("/v1/checkout-sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": key,
      },
      body,
    }).then((checkout) => {
      if (!checkout?.approvalUrl || !checkout.checkoutSessionId) {
        throw new Error("Checkout did not return a PayPal approval URL and session ID. Try again.");
      }
      const current = readCheckoutAttempts();
      if (current[body]?.key === key) {
        current[body].checkoutId = checkout.checkoutSessionId;
        saveCheckoutAttempts(current);
      }
      return checkout;
    }).catch((error) => {
      const { status, code } = error;
      const unresolved = retryableCheckoutStatuses.has(status)
        || (status === 409 && unresolvedCheckoutCodes.has(code));
      if (status >= 400 && status < 500 && !unresolved) {
        const current = readCheckoutAttempts();
        if (current[body]?.key === key) {
          delete current[body];
          saveCheckoutAttempts(current);
        }
      }
      throw error;
    }).finally(() => {
      pendingCheckouts.delete(body);
    });
    pendingCheckouts.set(body, pending);
    return pending;
  },

  async captureCheckout(checkoutSessionId) {
    const checkout = await request(`/v1/checkout-sessions/${checkoutSessionId}/capture`, {
      method: "POST",
    });
    clearSettledCheckout(checkout);
    return checkout;
  },

  async cancelCheckout(checkoutSessionId) {
    const checkout = await request(`/v1/checkout-sessions/${checkoutSessionId}/cancel`, {
      method: "POST",
    });
    clearSettledCheckout(checkout);
    return checkout;
  },

};
