import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSearchParams } from "react-router-dom";
import { ArrowUpRight, Search, Send, Sparkles, ShieldCheck } from "lucide-react";

import { api } from "../services/api";

export default function ShoppingAssistant({ userId }) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [message, setMessage] = useState(searchParams.get("query") || "");
  const [searching, setSearching] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [submittedMessage, setSubmittedMessage] = useState("");
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
    try {
      const result = await api.searchShopping(requestMessage);
      sessionStorage.setItem("spendwise_search_result", JSON.stringify({ userId, result }));
      navigate("/products");
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
