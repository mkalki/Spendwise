
const API_BASE_URL = "/api";

let csrfToken;
let checkoutAttempt;

const unresolvedCheckoutCodes = new Set([
  "CHECKOUT_IN_PROGRESS",
  "CHECKOUT_REQUIRES_RECONCILIATION",
  "IDEMPOTENCY_KEY_REUSED",
]);

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
    if (checkoutAttempt?.body !== body) {
      checkoutAttempt = {
        body,
        key: Array.from(crypto.getRandomValues(new Uint8Array(16)),
          (byte) => byte.toString(16).padStart(2, "0")).join(""),
      };
    }
    const attempt = checkoutAttempt;
    attempt.pending ??= request("/v1/checkout-sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": attempt.key,
      },
      body,
    }).then((checkout) => {
      if (!checkout?.approvalUrl || !checkout.checkoutSessionId) {
        throw new Error("Checkout did not return a PayPal approval URL and session ID. Try again.");
      }
      if (checkoutAttempt === attempt) checkoutAttempt = undefined;
      return checkout;
    }).catch((error) => {
      const { status, code } = error;
      const unresolved = status === 408 || status === 425 || status === 429
        || (status === 409 && unresolvedCheckoutCodes.has(code));
      if (checkoutAttempt === attempt && status >= 400 && status < 500 && !unresolved) {
        checkoutAttempt = undefined;
      }
      throw error;
    }).finally(() => {
      attempt.pending = undefined;
    });
    return attempt.pending;
  },

  abandonCheckoutAttempt() {
    checkoutAttempt = undefined;
  },

  async captureCheckout(checkoutSessionId) {
    return request(`/v1/checkout-sessions/${checkoutSessionId}/capture`, {
      method: "POST",
    });
  },

  async cancelCheckout(checkoutSessionId) {
    return request(`/v1/checkout-sessions/${checkoutSessionId}/cancel`, {
      method: "POST",
    });
  },

};
