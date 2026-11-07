
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
  currency: "USD",
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

  // Fetch saved policy when the component loads.
  useEffect(() => {

    async function loadPolicy() {

      try {

        setLoading(true);

        const data = await api.getPolicy();

        if (data) {

          setPolicy({
            maxItemPrice: String(data.maxItemPrice ?? 60),

            maxOrderAmount: String(data.maxOrderAmount ?? 100),

            weeklySpendingLimit: String(
              data.weeklySpendingLimit ?? 150
            ),

            maxOrdersPerWeek: String(
              data.maxOrdersPerWeek ?? 1
            ),

            currency: data.currency ?? "USD",

            country: data.country ?? "US",
          });

          setSource("backend");

        }

      } catch (error) {

        console.error("Policy loading failed:", error);

        setErrorMessage(
          "Could not load the saved policy. Showing initial demo values."
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

    if (!policy.currency) {
      newErrors.currency = "Select a currency.";
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
        maxItemPrice: Number(policy.maxItemPrice),

        maxOrderAmount: Number(policy.maxOrderAmount),

        weeklySpendingLimit: Number(
          policy.weeklySpendingLimit
        ),

        maxOrdersPerWeek: Number(
          policy.maxOrdersPerWeek
        ),

        currency: policy.currency,

        country: policy.country,
      };

      const savedPolicy = await api.savePolicy(payload);

      // Use the returned backend values when available.
      if (savedPolicy) {
        setPolicy({
          maxItemPrice: String(
            savedPolicy.maxItemPrice ?? payload.maxItemPrice
          ),
          maxOrderAmount: String(
            savedPolicy.maxOrderAmount ?? payload.maxOrderAmount
          ),
          weeklySpendingLimit: String(
            savedPolicy.weeklySpendingLimit ??
            payload.weeklySpendingLimit
          ),
          maxOrdersPerWeek: String(
            savedPolicy.maxOrdersPerWeek ??
            payload.maxOrdersPerWeek
          ),
          currency: savedPolicy.currency ?? payload.currency,
          country: savedPolicy.country ?? payload.country,
        });
      }

      setSource("backend");

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

  const currencySymbol =
    policy.currency === "USD" ? "$" :
    policy.currency === "INR" ? "₹" :
    policy.currency;

  return (

    <div className="policy-page">

      {/* Page heading */}

      <div className="policy-heading">

        <div>

          <div className="policy-title">

            <ShieldCheck size={25} />

            <h2>Spending Policy</h2>

          </div>

          <p>
            Set your shopping limits and protect your budget.
          </p>

        </div>

        <span className="policy-active">
          <span></span>
          Protection settings
        </span>

      </div>

      {/* Information banner */}

      <div className="policy-banner">

        <ShieldCheck size={23} />

        <div>

          <strong>Your money, your rules.</strong>

          <p>
            CartGuard checks every purchase against your
            spending policy before checkout.
          </p>

        </div>

      </div>

      {loading && (

        <div className="policy-message info-message">
          Loading your saved policy...
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

      {/* Policy form */}

      <form
        className="policy-form"
        onSubmit={handleSubmit}
      >

        <div className="policy-form-header">

          <div>
            <h3>Configure your limits</h3>

            <p>
              Define the maximum amount CartGuard can
              authorize for shopping.
            </p>
          </div>

          <span className="demo-source">
            {source === "backend"
              ? "Backend policy"
              : "Initial demo values"}
          </span>

        </div>

        {/* Currency and country */}

        <div className="form-section">

          <h4>Regional preferences</h4>

          <div className="policy-fields">

            <div className="form-group">

              <label htmlFor="currency">
                Currency
              </label>

              <select
                id="currency"
                name="currency"
                value={policy.currency}
                onChange={handleChange}
                disabled={loading || saving}
              >
                <option value="USD">USD - US Dollar</option>
                <option value="INR">INR - Indian Rupee</option>
              </select>

              {errors.currency && (
                <span className="field-error">
                  {errors.currency}
                </span>
              )}

            </div>

            <div className="form-group">

              <label htmlFor="country">
                Country
              </label>

              <select
                id="country"
                name="country"
                value={policy.country}
                onChange={handleChange}
                disabled={loading || saving}
              >
                <option value="US">United States</option>
                <option value="IN">India</option>
              </select>

              {errors.country && (
                <span className="field-error">
                  {errors.country}
                </span>
              )}

            </div>

          </div>

        </div>

        {/* Financial limits */}

        <div className="form-section">

          <h4>Spending restrictions</h4>

          <div className="policy-fields">

            <div className="form-group">

              <label htmlFor="maxItemPrice">
                <ShoppingBag size={16} />
                Maximum price per item
              </label>

              <div className="input-with-symbol">

                <span>{currencySymbol}</span>

                <input
                  id="maxItemPrice"
                  name="maxItemPrice"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={policy.maxItemPrice}
                  onChange={handleChange}
                  disabled={loading || saving}
                  placeholder="60"
                />

              </div>

              {errors.maxItemPrice && (
                <span className="field-error">
                  {errors.maxItemPrice}
                </span>
              )}

              <small>
                No individual product can exceed this amount.
              </small>

            </div>

            <div className="form-group">

              <label htmlFor="maxOrderAmount">
                <Wallet size={16} />
                Maximum order amount
              </label>

              <div className="input-with-symbol">

                <span>{currencySymbol}</span>

                <input
                  id="maxOrderAmount"
                  name="maxOrderAmount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={policy.maxOrderAmount}
                  onChange={handleChange}
                  disabled={loading || saving}
                  placeholder="100"
                />

              </div>

              {errors.maxOrderAmount && (
                <span className="field-error">
                  {errors.maxOrderAmount}
                </span>
              )}

              <small>
                The total order cannot exceed this amount.
              </small>

            </div>

            <div className="form-group">

              <label htmlFor="weeklySpendingLimit">
                <CalendarDays size={16} />
                Weekly spending limit
              </label>

              <div className="input-with-symbol">

                <span>{currencySymbol}</span>

                <input
                  id="weeklySpendingLimit"
                  name="weeklySpendingLimit"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={policy.weeklySpendingLimit}
                  onChange={handleChange}
                  disabled={loading || saving}
                  placeholder="150"
                />

              </div>

              {errors.weeklySpendingLimit && (
                <span className="field-error">
                  {errors.weeklySpendingLimit}
                </span>
              )}

              <small>
                Total purchases must stay within this budget.
              </small>

            </div>

            <div className="form-group">

              <label htmlFor="maxOrdersPerWeek">
                <Hash size={16} />
                Maximum orders per week
              </label>

              <input
                id="maxOrdersPerWeek"
                name="maxOrdersPerWeek"
                type="number"
                min="1"
                step="1"
                value={policy.maxOrdersPerWeek}
                onChange={handleChange}
                disabled={loading || saving}
                placeholder="1"
              />

              {errors.maxOrdersPerWeek && (
                <span className="field-error">
                  {errors.maxOrdersPerWeek}
                </span>
              )}

              <small>
                Maximum number of successful purchases allowed.
              </small>

            </div>

          </div>

        </div>

        {/* Summary */}

        <div className="policy-summary">

          <div className="summary-icon">
            <ShieldCheck size={23} />
          </div>

          <div>

            <strong>Spending protection summary</strong>

            <p>
              Maximum item: {currencySymbol}
              {Number(policy.maxItemPrice || 0).toFixed(2)}
              {" · "}
              Maximum order: {currencySymbol}
              {Number(policy.maxOrderAmount || 0).toFixed(2)}
              {" · "}
              Weekly: {currencySymbol}
              {Number(policy.weeklySpendingLimit || 0).toFixed(2)}
            </p>

          </div>

        </div>

        {/* Action buttons */}

        <div className="policy-actions">

          <button
            type="button"
            className="reset-button"
            onClick={handleReset}
            disabled={loading || saving}
          >
            <RefreshCw size={16} />
            Reset form
          </button>

          <button
            type="submit"
            className="save-button"
            disabled={loading || saving}
          >

            <Save size={17} />

            {saving ? "Saving..." : "Save spending policy"}

          </button>

        </div>

      </form>

    </div>

  );

}
