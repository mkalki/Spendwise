
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

function clearCheckoutAttempt(checkoutId) {
  const attempts = readCheckoutAttempts();
  const body = Object.keys(attempts).find((body) => attempts[body].checkoutId === String(checkoutId));
  if (body !== undefined) {
    delete attempts[body];
    saveCheckoutAttempts(attempts);
  }
}

async function settleCheckout(checkoutId, action) {
  try {
    const checkout = await request(`/v1/checkout-sessions/${checkoutId}/${action}`, {
      method: "POST",
    });
    if (["CAPTURED", "CANCELLED", "EXPIRED"].includes(checkout?.status)) {
      clearCheckoutAttempt(checkoutId);
    }
    return checkout;
  } catch (error) {
    if (error.status === 409 && error.code === "CHECKOUT_EXPIRED") {
      clearCheckoutAttempt(checkoutId);
    }
    throw error;
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

  async updateProfile(name) {
    return request("/auth/profile", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
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

  async getShoppingRecommendations(searchSessionId) {
    return request(`/v1/shopping/search/${searchSessionId}/recommendations`, { method: "POST" });
  },

  getProMembership() {
    return request("/payments/pro");
  },

  createProCheckout(plan) {
    const key = Array.from(crypto.getRandomValues(new Uint8Array(16)),
      (byte) => byte.toString(16).padStart(2, "0")).join("");
    return request("/payments/pro/checkout", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": key },
      body: JSON.stringify({ plan }),
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

  getBlockedPurchases() {
    return request("/v1/transactions/blocked");
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
    return settleCheckout(checkoutSessionId, "capture");
  },

  async cancelCheckout(checkoutSessionId) {
    return settleCheckout(checkoutSessionId, "cancel");
  },

};
