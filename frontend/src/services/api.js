
const USE_MOCK_API = true;

const API_BASE_URL = "/api";

const DEFAULT_POLICY = {
  maxItemPrice: 60,
  maxOrderAmount: 100,
  weeklySpendingLimit: 150,
  maxOrdersPerWeek: 1,
  currency: "USD",
  country: "US",
};

const STORAGE_KEY = "spendwise_demo_policy";

function getMockPolicy() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);

    return saved
      ? JSON.parse(saved)
      : { ...DEFAULT_POLICY };
  } catch {
    return { ...DEFAULT_POLICY };
  }
}

function saveMockPolicy(policy) {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify(policy)
  );

  return { ...policy };
}

async function request(path, options = {}) {
  const response = await fetch(
    `${API_BASE_URL}${path}`,
    {
      ...options,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        ...options.headers,
      },
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
    throw new Error(
      data?.message ||
      `Request failed: ${response.status}`
    );
  }

  return data;
}

export const api = {

  async getPolicy() {
    if (USE_MOCK_API) {
      return getMockPolicy();
    }

    return request("/policies");
  },

  async savePolicy(policy) {
    if (USE_MOCK_API) {
      return saveMockPolicy(policy);
    }

    return request("/policies", {
      method: "PUT",
      body: JSON.stringify(policy),
    });
  },

};
