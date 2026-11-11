import { useEffect, useState } from "react";
import { Check, Sparkles } from "lucide-react";
import { api } from "../services/api";

export default function Pro() {
  const [membership, setMembership] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api.getProMembership().then((value) => { if (active) setMembership(value); })
      .catch((failure) => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, []);

  async function subscribe(plan) {
    setBusy(true); setError("");
    try {
      const checkout = await api.createProCheckout(plan);
      if (!checkout.approvalUrl || !checkout.checkoutSessionId) throw new Error("PayPal did not return an approval link.");
      sessionStorage.setItem("spendwise_checkout_id", checkout.checkoutSessionId);
      window.location.assign(checkout.approvalUrl);
    } catch (failure) { setError(failure.message); setBusy(false); }
  }

  return <section className="panel cart-list">
    <span className="eyebrow"><Sparkles size={14} /> SPENDWISE PRO</span>
    <h2>More room to search.</h2>
    <p>Unlimited Spendwise searches. External provider limits still apply.</p>
    {membership?.pro && <p role="status">Pro is active until {new Date(membership.expiresAt).toLocaleDateString()}.</p>}
    {error && <p role="alert" className="policy-message error-message">{error}</p>}
    <div className="product-grid">
      <article className="panel product-card"><div className="product-card-content">
        <h3>Monthly</h3><strong className="product-price">${membership?.nextMonthlyPrice || "1.00"} {membership?.nextMonthlyPrice === "0.80" ? "renewal" : "first month"}</strong>
        <p><Check size={14} /> Then ${membership?.nextMonthlyPrice || "0.80"} per month with manual renewal.</p>
        <button className="save-button" disabled={busy || membership?.pro} onClick={() => subscribe("MONTHLY")}>Pay monthly with PayPal</button>
      </div></article>
      <article className="panel product-card"><div className="product-card-content">
        <h3>Annual</h3><strong className="product-price">$6.00 / year</strong>
        <p><Check size={14} /> One manual payment covers 12 months.</p>
        <button className="save-button" disabled={busy || membership?.pro} onClick={() => subscribe("ANNUAL")}>Pay yearly with PayPal</button>
      </div></article>
    </div>
    {busy && <p role="status">Preparing secure PayPal checkout…</p>}
  </section>;
}
