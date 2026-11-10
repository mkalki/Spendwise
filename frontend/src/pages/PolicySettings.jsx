
import { useEffect, useState } from "react";

import {
  ShieldCheck,
  Save,
  RefreshCw,
  Wallet,
  ShoppingBag,
  CalendarDays,
  Hash,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

import { api } from "../services/api";

const defaultPolicy = {
  maxItemPrice: "60",
  maxOrderAmount: "100",
  weeklySpendingLimit: "150",
  maxOrdersPerWeek: "1",
  country: "US",
};

export default function PolicySettings() {

  const [policy, setPolicy] = useState(defaultPolicy);

  const [errors, setErrors] = useState({});

  const [loading, setLoading] = useState(true);

  const [saving, setSaving] = useState(false);

  const [message, setMessage] = useState("");

  const [errorMessage, setErrorMessage] = useState("");

  const [source, setSource] = useState("defaults");
  const [previousCurrency, setPreviousCurrency] = useState(null);

  // Fetch saved policy when the component loads.
  useEffect(() => {

    async function loadPolicy() {

      try {

        setLoading(true);

        const data = await api.getPolicy();

        if (data) {
          const needsUsdLimits = data.currency && data.currency !== "USD";
          setPreviousCurrency(needsUsdLimits ? data.currency : null);

          setPolicy({
            maxItemPrice: needsUsdLimits ? "" : String(data.maxItemPrice ?? 60),

            maxOrderAmount: needsUsdLimits ? "" : String(data.maxOrderAmount ?? 100),

            weeklySpendingLimit: needsUsdLimits ? "" : String(data.weeklySpendingLimit ?? 150),

            maxOrdersPerWeek: String(
              data.maxOrdersPerWeek ?? 1
            ),

            country: data.country ?? "US",
          });

          setSource("backend");

        }

      } catch (error) {

        console.error("Policy loading failed:", error);

        setErrorMessage(
          "Could not load the saved policy. Showing the default policy values."
        );

      } finally {

        setLoading(false);

      }

    }

    loadPolicy();

  }, []);

  // Update form state.
  function handleChange(event) {

    const { name, value } = event.target;

    setPolicy((previous) => ({
      ...previous,
      [name]: value,
    }));

    setErrors((previous) => ({
      ...previous,
      [name]: "",
    }));

    setMessage("");
  }

  // Validate form.
  function validatePolicy() {

    const newErrors = {};

    const itemPrice = Number(policy.maxItemPrice);

    const orderAmount = Number(policy.maxOrderAmount);

    const weeklyLimit = Number(policy.weeklySpendingLimit);

    const maxOrders = Number(policy.maxOrdersPerWeek);

    if (!policy.maxItemPrice || !Number.isFinite(itemPrice) || itemPrice <= 0) {
      newErrors.maxItemPrice =
        "Enter a valid positive item price.";
    }

    if (!policy.maxOrderAmount || !Number.isFinite(orderAmount) || orderAmount <= 0) {
      newErrors.maxOrderAmount =
        "Enter a valid positive order amount.";
    }

    if (!policy.weeklySpendingLimit || !Number.isFinite(weeklyLimit) || weeklyLimit <= 0) {
      newErrors.weeklySpendingLimit =
        "Enter a valid weekly spending limit.";
    }

    if (!policy.maxOrdersPerWeek || !Number.isInteger(maxOrders) || maxOrders < 1) {
      newErrors.maxOrdersPerWeek =
        "Enter a whole number greater than zero.";
    }

    if (
      Number.isFinite(itemPrice) &&
      Number.isFinite(orderAmount) &&
      itemPrice > orderAmount
    ) {
      newErrors.maxItemPrice =
        "Maximum item price cannot exceed maximum order amount.";
    }

    if (
      Number.isFinite(orderAmount) &&
      Number.isFinite(weeklyLimit) &&
      orderAmount > weeklyLimit
    ) {
      newErrors.maxOrderAmount =
        "Maximum order amount cannot exceed weekly limit.";
    }

    if (!policy.country) {
      newErrors.country = "Select a country.";
    }

    setErrors(newErrors);

    return Object.keys(newErrors).length === 0;
  }

  // Save policy to Spring Boot.
  async function handleSubmit(event) {

    event.preventDefault();

    setMessage("");
    setErrorMessage("");

    if (!validatePolicy()) {
      return;
    }

    try {

      setSaving(true);

      const payload = {
        maxItemPrice: policy.maxItemPrice,
        maxOrderAmount: policy.maxOrderAmount,
        weeklySpendingLimit: policy.weeklySpendingLimit,
        maxOrdersPerWeek: policy.maxOrdersPerWeek,
        currency: "USD",
        country: policy.country,
      };

      const savedPolicy = await api.savePolicy(payload);

      // Use the returned backend values when available.
      if (savedPolicy) {
        setPolicy({
          maxItemPrice: String(savedPolicy.maxItemPrice ?? payload.maxItemPrice),
          maxOrderAmount: String(savedPolicy.maxOrderAmount ?? payload.maxOrderAmount),
          weeklySpendingLimit: String(
            savedPolicy.weeklySpendingLimit ?? payload.weeklySpendingLimit
          ),
          maxOrdersPerWeek: String(savedPolicy.maxOrdersPerWeek ?? payload.maxOrdersPerWeek),
          country: savedPolicy.country ?? payload.country,
        });
      }

      setSource("backend");
      setPreviousCurrency(null);

      setMessage("Your spending policy has been saved successfully.");

    } catch (error) {

      console.error("Policy save failed:", error);

      setErrorMessage(error.message || "Unable to save policy.");

    } finally {

      setSaving(false);

    }

  }

  function handleReset() {

    setPolicy(defaultPolicy);

    setErrors({});

    setMessage("");

    setErrorMessage("");

  }

  const currencySymbol = "$";

  return (
    <div className="policy-page">
      <header className="policy-heading">
        <div>
          <span className="eyebrow">SPENDING CONTROL CENTER</span>
          <div className="policy-title">
            <ShieldCheck size={25} />
            <h2>Spending policies</h2>
          </div>
          <p>Set the boundaries that keep every purchase intentional and protected.</p>
        </div>
        <span className="policy-active">
          <span />
          {loading ? "Loading policy" : "Protection active"}
        </span>
      </header>

      <div className="policy-banner">
        <ShieldCheck size={23} />
        <div>
          <strong>Your money, your rules.</strong>
          <p>Spendwise checks each product and checkout against these limits before payment.</p>
        </div>
      </div>

      {loading && (
        <div className="policy-message info-message">Loading your saved policy...</div>
      )}
      {!loading && previousCurrency && (
        <div className="policy-message info-message">
          Your saved policy uses {previousCurrency}. Review your spending limits in USD, then save to switch.
        </div>
      )}
      {!loading && errorMessage && (
        <div className="policy-message error-message">
          <AlertCircle size={18} />
          {errorMessage}
        </div>
      )}
      {message && (
        <div className="policy-message success-message">
          <CheckCircle2 size={18} />
          {message}
        </div>
      )}

      <div className="policy-layout">
        <form className="policy-form" onSubmit={handleSubmit}>
          <div className="policy-form-header">
            <div>
              <span className="section-kicker">CONTROL SETTINGS</span>
              <h3>Configure your limits</h3>
              <p>These are the maximum amounts Spendwise can authorize for shopping.</p>
            </div>
            <span className="demo-source">
              {source === "backend" ? "Saved policy" : "Default policy"}
            </span>
          </div>

          <div className="form-section">
            <h4>Region and currency</h4>
            <div className="policy-fields">
              <div className="form-group">
                <label htmlFor="currency">Currency</label>
                <select id="currency" value="USD" disabled>
                  <option value="USD">USD - US Dollar</option>
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="country">Country</label>
                <select id="country" name="country" value={policy.country} onChange={handleChange} disabled={loading || saving}>
                  <option value="US">United States</option>
                  <option value="IN">India</option>
                </select>
                {errors.country && <span className="field-error">{errors.country}</span>}
              </div>
            </div>
          </div>

          <div className="form-section">
            <h4>Spending boundaries</h4>
            <div className="policy-fields">
              <div className="form-group">
                <label htmlFor="maxItemPrice"><ShoppingBag size={16} /> Maximum price per item</label>
                <div className="input-with-symbol">
                  <span>{currencySymbol}</span>
                  <input id="maxItemPrice" name="maxItemPrice" type="number" min="0.01" step="0.01" value={policy.maxItemPrice} onChange={handleChange} disabled={loading || saving} placeholder="60" />
                </div>
                {errors.maxItemPrice && <span className="field-error">{errors.maxItemPrice}</span>}
                <small>No individual product can exceed this amount.</small>
              </div>
              <div className="form-group">
                <label htmlFor="maxOrderAmount"><Wallet size={16} /> Maximum order amount</label>
                <div className="input-with-symbol">
                  <span>{currencySymbol}</span>
                  <input id="maxOrderAmount" name="maxOrderAmount" type="number" min="0.01" step="0.01" value={policy.maxOrderAmount} onChange={handleChange} disabled={loading || saving} placeholder="100" />
                </div>
                {errors.maxOrderAmount && <span className="field-error">{errors.maxOrderAmount}</span>}
                <small>The total order cannot exceed this amount.</small>
              </div>
              <div className="form-group">
                <label htmlFor="weeklySpendingLimit"><CalendarDays size={16} /> Weekly spending limit</label>
                <div className="input-with-symbol">
                  <span>{currencySymbol}</span>
                  <input id="weeklySpendingLimit" name="weeklySpendingLimit" type="number" min="0.01" step="0.01" value={policy.weeklySpendingLimit} onChange={handleChange} disabled={loading || saving} placeholder="150" />
                </div>
                {errors.weeklySpendingLimit && <span className="field-error">{errors.weeklySpendingLimit}</span>}
                <small>Total purchases must stay within this budget.</small>
              </div>
              <div className="form-group">
                <label htmlFor="maxOrdersPerWeek"><Hash size={16} /> Maximum orders per week</label>
                <input id="maxOrdersPerWeek" name="maxOrdersPerWeek" type="number" min="1" step="1" value={policy.maxOrdersPerWeek} onChange={handleChange} disabled={loading || saving} placeholder="1" />
                {errors.maxOrdersPerWeek && <span className="field-error">{errors.maxOrdersPerWeek}</span>}
                <small>Maximum number of successful purchases allowed.</small>
              </div>
            </div>
          </div>

          <div className="policy-actions">
            <button type="button" className="reset-button" onClick={handleReset} disabled={loading || saving}>
              <RefreshCw size={16} />
              Reset form
            </button>
            <button type="submit" className="save-button" disabled={loading || saving}>
              <Save size={17} />
              {saving ? "Saving policy..." : "Save spending policy"}
            </button>
          </div>
        </form>

        <aside className="policy-summary-card">
          <div className="summary-card-heading">
            <div className="summary-icon"><ShieldCheck size={23} /></div>
            <div>
              <span className="section-kicker">CURRENT POLICY</span>
              <h3>Protection summary</h3>
            </div>
          </div>
          <p className="summary-card-copy">Your active boundaries are applied before checkout.</p>
          <div className="summary-rule"><span>Per item</span><strong>{currencySymbol}{Number(policy.maxItemPrice || 0).toFixed(2)}</strong></div>
          <div className="summary-rule"><span>Per order</span><strong>{currencySymbol}{Number(policy.maxOrderAmount || 0).toFixed(2)}</strong></div>
          <div className="summary-rule"><span>Weekly limit</span><strong>{currencySymbol}{Number(policy.weeklySpendingLimit || 0).toFixed(2)}</strong></div>
          <div className="summary-rule"><span>Orders per week</span><strong>{policy.maxOrdersPerWeek || 0}</strong></div>
          <div className="summary-status"><CheckCircle2 size={16} /> Policy protection enabled</div>
        </aside>
      </div>
    </div>
  );

}
