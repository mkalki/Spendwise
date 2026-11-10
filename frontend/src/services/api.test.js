import assert from "node:assert/strict";
import { test } from "node:test";
import { api } from "./api.js";

function approvedCheckout() {
  return Response.json({
    checkoutSessionId: "101",
    approvalUrl: "https://www.sandbox.paypal.com/checkoutnow",
  });
}

function mockCheckout(t, ...responses) {
  const storage = new Map();
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    value: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
    },
  });
  t.after(() => { delete globalThis.sessionStorage; });
  const requests = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url === "/api/auth/csrf") return Response.json({ token: "test-token" });
    if (url === "/api/v1/checkout-sessions") {
      requests.push({
        key: options.headers["Idempotency-Key"],
        body: JSON.parse(options.body),
      });
      assert.ok(requests.at(-1).key);
    } else {
      assert.match(url, /^\/api\/v1\/checkout-sessions\/101\/(capture|cancel)$/);
    }
    assert.ok(responses.length > 0, "Unexpected checkout request");
    const next = responses.shift();
    return typeof next === "function" ? next() : next;
  });
  return requests;
}

const createCheckout = () => api.createCheckout("search-1", "candidate-1");

test("checkout retries reuse the same key after a server timeout or other 5xx", async (t) => {
  const requests = mockCheckout(t, new Response(null, { status: 504 }),
    new Response(null, { status: 500 }), approvedCheckout());

  await assert.rejects(createCheckout, { status: 504 });
  await assert.rejects(createCheckout, { status: 500 });
  await createCheckout();

  assert.equal(requests.length, 3);
  assert.equal(new Set(requests.map(({ key }) => key)).size, 1);
});

test("a network error or lost response body keeps the key for retry", async (t) => {
  const lostBody = new Response(new ReadableStream({
    start(controller) { controller.error(new TypeError("Response body lost")); },
  }));
  const requests = mockCheckout(t, () => { throw new TypeError("Network failure"); },
    lostBody, approvedCheckout());

  await assert.rejects(createCheckout, { message: "Network failure" });
  await assert.rejects(createCheckout, { message: "Response body lost" });
  await createCheckout();

  assert.equal(requests.length, 3);
  assert.equal(new Set(requests.map(({ key }) => key)).size, 1);
});

test("a confirmed capture or cancellation clears the key for the next attempt", async (t) => {
  const requests = mockCheckout(t,
    approvedCheckout(), Response.json({ checkoutSessionId: "101", status: "CAPTURED" }),
    approvedCheckout(), Response.json({ checkoutSessionId: "101", status: "CANCELLED" }),
    approvedCheckout());

  await createCheckout();
  await api.captureCheckout("101");
  await createCheckout();
  await api.cancelCheckout("101");
  await createCheckout();

  assert.equal(new Set(requests.map(({ key }) => key)).size, 3);
});

test("a definitive 4xx clears the key for the next attempt", async (t) => {
  const requests = mockCheckout(t, Response.json({ code: "PRODUCT_NOT_ELIGIBLE" }, { status: 409 }),
    approvedCheckout());

  await assert.rejects(createCheckout, { status: 409 });
  await createCheckout();

  assert.notEqual(requests[0].key, requests[1].key);
});

test("unresolved 4xx responses and incomplete success responses keep the key", async (t) => {
  const requests = mockCheckout(t, new Response(null, { status: 401 }), new Response(null, { status: 403 }),
    new Response(null, { status: 408 }), new Response(null, { status: 429 }),
    Response.json({ code: "CHECKOUT_IN_PROGRESS" }, { status: 409 }),
    Response.json({ code: "CHECKOUT_REQUIRES_RECONCILIATION" }, { status: 409 }),
    Response.json({ code: "IDEMPOTENCY_KEY_REUSED" }, { status: 409 }),
    Response.json({ checkoutSessionId: "101" }), approvedCheckout());

  for (const status of [401, 403, 408, 429, 409, 409, 409]) {
    await assert.rejects(createCheckout, { status });
  }
  await assert.rejects(createCheckout, /approval URL/);
  await createCheckout();

  assert.equal(requests.length, 9);
  assert.equal(new Set(requests.map(({ key }) => key)).size, 1);
});

test("a changed candidate or search session gets a new key", async (t) => {
  const requests = mockCheckout(t, new Response(null, { status: 504 }),
    new Response(null, { status: 504 }), approvedCheckout());

  await assert.rejects(createCheckout, { status: 504 });
  await assert.rejects(api.createCheckout("search-1", "candidate-2"), { status: 504 });
  await api.createCheckout("search-2", "candidate-2");

  assert.equal(new Set(requests.map(({ key }) => key)).size, 3);
  assert.deepEqual(requests[2].body, { searchSessionId: "search-2", candidateId: "candidate-2" });
});

test("concurrent submissions for the same payload share one request", async (t) => {
  const response = Promise.withResolvers();
  const requests = mockCheckout(t, () => response.promise);

  const first = createCheckout();
  const second = createCheckout();
  response.resolve(approvedCheckout());
  const checkouts = await Promise.all([first, second]);

  assert.equal(requests.length, 1);
  assert.equal(checkouts[0], checkouts[1]);
});

test("switching offers preserves an in-flight attempt and its key after a late success", async (t) => {
  const response = Promise.withResolvers();
  const requests = mockCheckout(t, () => response.promise, approvedCheckout(), approvedCheckout());

  const original = createCheckout();
  await api.createCheckout("search-1", "candidate-2");
  response.resolve(approvedCheckout());
  await original;
  await createCheckout();

  assert.notEqual(requests[0].key, requests[1].key);
  assert.equal(requests[0].key, requests[2].key);
});

test("reloading while checkout is in flight reuses the key even if the first response is lost", async (t) => {
  const sent = Promise.withResolvers();
  const response = Promise.withResolvers();
  const requests = mockCheckout(t, () => {
    sent.resolve();
    return response.promise;
  }, approvedCheckout());

  const first = assert.rejects(createCheckout, /Response lost/);
  await sent.promise;
  const { api: reloadedApi } = await import("./api.js?reload");
  await reloadedApi.createCheckout("search-1", "candidate-1");
  response.reject(new TypeError("Response lost"));
  await first;

  assert.equal(requests.length, 2);
  assert.equal(requests[0].key, requests[1].key);
});
