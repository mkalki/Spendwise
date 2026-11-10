import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowUpRight, Search, Send, Sparkles, ShieldCheck } from "lucide-react";

import { api } from "../services/api";
import "./Shopping.css";

function IntentReview({ result }) {
  const { intent, eligibleCandidates, rejectedCandidates } = result;
  const offerCount = eligibleCandidates.length + rejectedCandidates.length;

  return (
    <section className="chat-message assistant intent-review" aria-labelledby="intent-heading">
      <span className="chat-role">SPENDWISE</span>
      <h3 id="intent-heading">How I interpreted your request</h3>
      <dl>
        <div><dt>Search terms</dt><dd>{intent.normalizedQuery}</dd></div>
        <div><dt>Category</dt><dd>{intent.category || "Unspecified"}</dd></div>
        <div><dt>Requested budget</dt><dd>{intent.promptMaxPrice ? `${intent.currencyCode} ${intent.promptMaxPrice}` : "Unspecified"}</dd></div>
        {intent.effectiveMaxPrice && <div><dt>Search price cap</dt><dd>{intent.currencyCode} {intent.effectiveMaxPrice}</dd></div>}
        {intent.useCase && <div><dt>Use case</dt><dd>{intent.useCase}</dd></div>}
        <div><dt>Preferences</dt><dd>{intent.preferences?.length ? intent.preferences.join(", ") : "Unspecified"}</dd></div>
      </dl>
      <p className="shop-muted">You can edit your prompt below and search again.</p>
      <Link className="save-button" to="/products">
        {offerCount ? `View ${offerCount} ${offerCount === 1 ? "offer" : "offers"}` : "View search results"}
        <ArrowUpRight size={16} aria-hidden="true" />
      </Link>
    </section>
  );
}

export default function ShoppingAssistant({ userId }) {
  const [searchParams] = useSearchParams();
  const [message, setMessage] = useState(searchParams.get("query") || "");
  const [searching, setSearching] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [submittedMessage, setSubmittedMessage] = useState("");
  const [result, setResult] = useState(null);
  const suggestions = [
    "Find the best laptop under ₹60,000.",
    "Find wireless headphones for studying.",
    "Compare smartphones under ₹25,000.",
    "Find a budget-friendly mechanical keyboard.",
  ];

  async function handleSubmit(event) {
    event.preventDefault();
    if (!message.trim()) return;

    const requestMessage = message.trim();
    setSearching(true);
    setErrorMessage("");
    setSubmittedMessage(requestMessage);
    setResult(null);
    try {
      const result = await api.searchShopping(requestMessage);
      sessionStorage.setItem("spendwise_search_result", JSON.stringify({ userId, result }));
      setResult(result);
    } catch (error) {
      setErrorMessage(error.message || "Unable to search products.");
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="assistant-page">
      <section className="panel assistant-workspace">
        <div className="assistant-header">
          <div>
            <span className="eyebrow">SPENDWISE INTELLIGENCE</span>
            <h2>Shop smarter.<br />Spend better.</h2>
            <p>Tell SpendWise what you want to buy. Your spending policies help guide every recommendation.</p>
          </div>
          <div className="assistant-protection">
            <ShieldCheck size={18} />
            <span>Policy-aware search</span>
          </div>
        </div>

        <div className="assistant-thread" aria-live="polite">
          {!submittedMessage && !searching && (
            <div className="assistant-empty">
              <div className="assistant-empty-icon"><Sparkles size={22} /></div>
              <strong>What are you looking for today?</strong>
              <span>Start with a product, a budget, or a use case.</span>
            </div>
          )}
          {submittedMessage && (
            <div className="chat-message user">
              <span className="chat-role">YOU</span>
              {submittedMessage}
            </div>
          )}
          {searching && (
            <div className="chat-message assistant assistant-typing">
              <span className="chat-role">SPENDWISE</span>
              <span className="typing-dots"><i /><i /><i /></span>
              Searching policy-aware options...
            </div>
          )}
          {result && <IntentReview result={result} />}
        </div>

        {errorMessage && <div className="policy-message error-message">{errorMessage}</div>}

        <form className="assistant-composer" onSubmit={handleSubmit}>
          <div className="assistant-input-shell">
            <textarea
              id="shopping-message"
              aria-label="What would you like to shop for?"
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="Describe what you want to find..."
              disabled={searching}
              rows="1"
              required
            />
            <button className="assistant-send" type="submit" aria-label="Search products" disabled={searching || !message.trim()}>
              {searching ? <Search size={17} /> : <Send size={17} />}
            </button>
          </div>
          <small>Spendwise checks recommendations against your active spending policy before checkout.</small>
        </form>
      </section>

      <aside className="panel assistant-prompts">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">START A SEARCH</span>
            <h3>Try a prompt</h3>
          </div>
          <ArrowUpRight size={18} />
        </div>
        <div className="assistant-suggestions">
          {suggestions.map((suggestion) => (
            <button className="suggestion-chip" key={suggestion} type="button" onClick={() => setMessage(suggestion)} disabled={searching}>
              {suggestion}
              <ArrowUpRight size={14} />
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}
