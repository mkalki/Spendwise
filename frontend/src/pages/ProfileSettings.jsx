import { useEffect, useState } from "react";
import { Check, MapPin, Save, UserRound } from "lucide-react";
import PolicySettings from "./PolicySettings";
import { api } from "../services/api";
import "./ProfileSettings.css";

const addressFields = [
  ["fullName", "Full name", "name", true],
  ["line1", "Street address", "address-line1", true],
  ["line2", "Apartment or suite", "address-line2", false],
  ["city", "City", "address-level2", true],
  ["region", "State or region", "address-level1", false],
  ["postalCode", "Postal code", "postal-code", false],
  ["countryCode", "Country code", "country", true],
];

function AddressForm({ details, onSaved }) {
  const [saving, setSaving] = useState(false);
  const [billingOverride, setBillingOverride] = useState(null);
  const sameBilling = billingOverride ?? !details?.billing;
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setSaving(true); setMessage(""); setError("");
    const data = new FormData(event.currentTarget);
    const shipping = Object.fromEntries(addressFields.map(([key]) => [key, (data.get(`shipping-${key}`) || "").trim()]));
    shipping.countryCode = shipping.countryCode.toUpperCase();
    const billing = sameBilling ? null
      : Object.fromEntries(addressFields.map(([key]) => [key, (data.get(`billing-${key}`) || "").trim()]));
    if (billing) billing.countryCode = billing.countryCode.toUpperCase();
    try {
      const saved = await api.saveCheckoutDetails({ shipping, billing });
      onSaved(saved); setMessage("Address saved to your Spendwise account.");
    } catch (failure) { setError(failure.message); }
    finally { setSaving(false); }
  }
  function fieldSet(kind, title, address) {
    return <fieldset className="profile-address-fields"><legend>{title}</legend><div className="profile-form-grid">
      {addressFields.map(([key, label, autoComplete, required]) => <label className="form-group" key={`${kind}-${key}`}>
        <span>{label}</span><input name={`${kind}-${key}`} autoComplete={`${kind} ${autoComplete}`}
          defaultValue={address?.[key] || ""} required={required} maxLength={key === "fullName" ? 120 : key === "line1" || key === "line2" ? 160 : key === "postalCode" ? 24 : 100}
          pattern={key === "countryCode" ? "[A-Za-z]{2}" : undefined} />
      </label>)}
    </div></fieldset>;
  }
  return <form className="panel profile-settings-card" onSubmit={submit}>
    <div className="profile-section-heading"><MapPin /><div><h2>Saved address</h2><p>Used to prefill delivery details at checkout. You can review them before paying.</p></div></div>
    {fieldSet("shipping", "Delivery address", details?.shipping)}
    <label className="shop-checkbox profile-same-address"><input type="checkbox" checked={sameBilling} onChange={(event) => setBillingOverride(event.target.checked)} /> Billing address matches delivery</label>
    {!sameBilling && fieldSet("billing", "Billing address", details?.billing)}
    {sameBilling && <p className="shop-muted">You can use a separate billing address by unchecking the box.</p>}
    {message && <p role="status">{message}</p>}{error && <p className="policy-message error-message" role="alert">{error}</p>}
    <button className="save-button" disabled={saving}><Save size={15} /> {saving ? "Saving…" : "Save address"}</button>
  </form>;
}

export default function ProfileSettings({ user, onUserUpdate }) {
  const [tab, setTab] = useState("personal");
  const [name, setName] = useState(user?.name || "");
  const [address, setAddress] = useState(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => { api.getCheckoutDetails().then(setAddress).catch((failure) => setError(failure.message)); }, []);
  async function saveProfile(event) {
    event.preventDefault(); setSaving(true); setMessage(""); setError("");
    try {
      const updated = await api.updateProfile(name);
      onUserUpdate(updated); setName(updated.name || ""); setMessage("Personal details saved.");
    } catch (failure) { setError(failure.message); }
    finally { setSaving(false); }
  }

  return <div className="profile-settings-page">
    <header className="profile-page-heading"><span className="eyebrow">ACCOUNT SETTINGS</span><h1>Edit profile</h1>
      <p>Manage your Spendwise details, spending policy, and saved address.</p></header>
    <nav className="profile-tabs" role="tablist" aria-label="Profile settings">
      {[['personal', 'Personal details'], ['policy', 'Spending policy'], ['address', 'Address']].map(([key, label]) =>
        <button key={key} type="button" role="tab" aria-selected={tab === key} onClick={() => setTab(key)}>{label}</button>)}
    </nav>
    {tab === "personal" && <form className="panel profile-settings-card" onSubmit={saveProfile}>
      <div className="profile-section-heading"><UserRound /><div><h2>Personal details</h2><p>Your name is shown in your Spendwise workspace. Email is managed by your sign-in provider.</p></div></div>
      <div className="profile-form-grid"><label className="form-group"><span>Name</span><input value={name} onChange={(event) => setName(event.target.value)} maxLength={120} required /></label>
        <label className="form-group"><span>Email</span><input value={user?.email || ""} readOnly /></label></div>
      {message && <p role="status"><Check size={15} /> {message}</p>}{error && <p className="policy-message error-message" role="alert">{error}</p>}
      <button className="save-button" disabled={saving}><Save size={15} /> {saving ? "Saving…" : "Save personal details"}</button>
    </form>}
    {tab === "policy" && <PolicySettings />}
    {tab === "address" && <AddressForm details={address} onSaved={setAddress} />}
  </div>;
}
