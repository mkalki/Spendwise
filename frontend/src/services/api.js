
const API_BASE_URL = "/api";

let csrfToken;

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
      body: JSON.stringify({ message }),
    });
  },

  async getTransactionSummary() {
    return request("/v1/transactions/summary");
  },

  async getTransactions() {
    return request("/v1/transactions");
  },

  async createCheckout(searchSessionId, candidateId) {
    return request("/v1/checkout-sessions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": crypto.randomUUID(),
      },
      body: JSON.stringify({ searchSessionId, candidateId }),
    });
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
