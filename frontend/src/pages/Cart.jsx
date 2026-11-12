import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { CreditCard, MapPin, ShoppingCart, Trash2 } from "lucide-react";
import { api } from "../services/api";
import "./Shopping.css";

const addressFields = [
  { name: "fullName", label: "Full name", autoComplete: "name", maxLength: 120, required: true },
  { name: "line1", label: "Street address", autoComplete: "address-line1", maxLength: 160, required: true },
  { name: "line2", label: "Apartment or suite (optional)", autoComplete: "address-line2", maxLength: 160 },
  { name: "city", label: "City", autoComplete: "address-level2", maxLength: 100, required: true },
  { name: "region", label: "State or region", autoComplete: "address-level1", maxLength: 100 },
  { name: "postalCode", label: "Postal code", autoComplete: "postal-code", maxLength: 24 },
  { name: "countryCode", label: "Two-letter country code", autoComplete: "country", maxLength: 2, pattern: "[A-Za-z]{2}", required: true },
];

function AddressFields({ kind, title, address }) {
  return (
    <fieldset className="checkout-address">
      <legend>{title}</legend>
      {addressFields.map(({ label, name, autoComplete, ...inputProps }) => (
        <label className="form-group" key={name}>
          <span>{label}</span>
          <input name={`${kind}-${name}`} autoComplete={`${kind} ${autoComplete}`}
            defaultValue={address?.[name] ?? ""} {...inputProps} />
        </label>
      ))}
    </fieldset>
  );
}

function readAddress(data, kind) {
  const address = Object.fromEntries(addressFields.map(({ name }) =>
    [name, data.get(`${kind}-${name}`).trim()]));
  address.countryCode = address.countryCode.toUpperCase();
  return address;
}

function CheckoutForm({ item, saved, onSaved }) {
  const [sameAddress, setSameAddress] = useState(!saved.billing);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const submitting = useRef(false);
  const checkoutScope = useRef(null);
  const searchSessionId = item?.searchSessionId;
  const candidateId = item?.candidateId;

  useEffect(() => {
    const scope = { active: true };
    checkoutScope.current = scope;
    return () => {
      scope.active = false;
    };
  }, [searchSessionId, candidateId]);

  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    const pay = event.nativeEvent.submitter?.value === "pay";
    const form = new FormData(event.currentTarget);
    const scope = checkoutScope.current;
    let redirecting = false;
    submitting.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const details = await api.saveCheckoutDetails({
        shipping: readAddress(form, "shipping"),
        billing: sameAddress ? null : readAddress(form, "billing"),
      });
      if (!scope?.active) return;
      onSaved(details);
      if (pay && item) {
        const checkout = await api.createCheckout(searchSessionId, candidateId);
        if (!scope.active) return;
        sessionStorage.setItem("spendwise_checkout_id", checkout.checkoutSessionId);
        window.location.assign(checkout.approvalUrl);
        redirecting = true;
        return;
      }
      setNotice("Saved to your Spendwise account.");
    } catch (failure) {
      if (scope?.active) setError(failure.message);
    } finally {
      if (!redirecting) {
        submitting.current = false;
        setBusy(false);
      }
    }
  }

  return (
    <form className="panel cart-checkout" onSubmit={submit}>
      <div className="shop-heading">
        <div>
          <span className="eyebrow">CHECKOUT PROFILE</span>
          <h3>Delivery and billing details</h3>
        </div>
        <MapPin size={20} />
      </div>
      <p className="shop-muted">These details are saved to your account. PayPal Sandbox handles payment approval.</p>
      <AddressFields kind="shipping" title="Delivery address" address={saved.shipping} />
      <label className="shop-checkbox"><input type="checkbox" checked={sameAddress}
        onChange={(event) => setSameAddress(event.target.checked)} /> Billing address matches delivery</label>
      {!sameAddress && <AddressFields kind="billing" title="Billing address" address={saved.billing} />}
      <div className="shop-actions">
        <button className="reset-button" type="submit" value="save" disabled={busy}>Save details</button>
        <button className="save-button" type="submit" value="pay" disabled={busy || !item}>
          <CreditCard size={16} /> {busy ? "Working…" : "Open PayPal Sandbox"}
        </button>
      </div>
      {item && <p className="shop-muted">Selected for checkout: {item.productTitle}. Price and policy are checked again before payment.</p>}
      {notice && <p role="status">{notice}</p>}
      {error && <p className="policy-message error-message" role="alert">{error}</p>}
    </form>
  );
}

export default function Cart() {
  const [items, setItems] = useState(null);
  const [saved, setSaved] = useState(null);
  const [selection, setSelection] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([api.getCart(), api.getCheckoutDetails()])
      .then(([cart, details]) => {
        if (!active) return;
        setItems(cart);
        setSaved(details);
      })
      .catch((failure) => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, []);

  async function remove(item) {
    setRemoving(item.candidateId);
    setError("");
    try {
      await api.removeFromCart(item.candidateId);
      setItems((current) => current.filter(({ candidateId }) => candidateId !== item.candidateId));
    } catch (failure) {
      setError(failure.message);
    } finally {
      setRemoving(null);
    }
  }

  const selected = items?.find(({ candidateId }) => candidateId === selection) ?? items?.[0];

  return (
    <div className="shop-page cart-page">
      <section className="panel cart-list">
        <div className="shop-heading">
          <div>
            <span className="eyebrow">SAVED TO YOUR ACCOUNT</span>
            <h2>Your cart {items && <small> / {items.length}</small>}</h2>
          </div>
          <ShoppingCart size={24} />
        </div>
        <p className="shop-muted">Choose one offer at a time for policy-checked PayPal Sandbox checkout.</p>
        {error && <p className="policy-message error-message" role="alert">{error}</p>}
        {!items && !error && <p role="status">Loading your saved cart…</p>}
        {items?.length === 0 && <p>Your cart is empty. <Link to="/assistant">Find something to add</Link>.</p>}
        {!!items?.length && <ul className="cart-items">
          {items.map((item) => (
            <li className="cart-line" key={item.candidateId}>
              {item.imageUrl ? <img src={item.imageUrl} alt="" loading="lazy" /> : <ShoppingCart size={24} aria-hidden="true" />}
              <div className="cart-line-product">
                <strong>{item.productTitle}</strong>
                <span>{item.merchantName} · {item.currencyCode} {Number(item.price).toFixed(2)}</span>
              </div>
              <label className="shop-checkbox">
                <input type="radio" name="cart-selection" checked={selected?.candidateId === item.candidateId}
                  disabled={Boolean(removing)} onChange={() => setSelection(item.candidateId)} />
                Select <span className="shop-sr-only">{item.productTitle}</span>
              </label>
              <button type="button" className="text-button" aria-label={`Remove ${item.productTitle}`}
                onClick={() => remove(item)} disabled={Boolean(removing)}><Trash2 size={17} /></button>
            </li>
          ))}
        </ul>}
        <Link className="reset-button" to="/products">Continue shopping</Link>
      </section>
      {saved && <CheckoutForm item={selected} saved={saved} onSaved={setSaved} />}
    </div>
  );
}
