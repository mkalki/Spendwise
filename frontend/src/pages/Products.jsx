import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, ExternalLink, ShieldCheck, ShoppingCart, SlidersHorizontal, Sparkles, Star, XCircle } from "lucide-react";
import { api } from "../services/api";
import "./Shopping.css";

const defaults = { sort: "recommended", brand: "", merchant: "", max: "", status: "all" };
const reviewCount = new Intl.NumberFormat();
const comparisonLimit = 3;

function ProductCard({ product, pick, added, busy, disabled, onAdd, comparison }) {
  const eligible = product.eligible;
  const StatusIcon = eligible ? CheckCircle2 : XCircle;

  return (
    <article className="panel product-card">
      <div className="product-media">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.productTitle} className="product-image" loading="lazy" />
        ) : (
          <div className="product-image product-image-placeholder" aria-hidden="true"><ShieldCheck size={26} /></div>
        )}
        <span className={`product-status ${eligible ? "eligible" : "rejected"}`}>
          <StatusIcon size={13} /> {eligible ? "Eligible" : "Not eligible"}
        </span>
      </div>
      <div className="product-card-content">
        <div className="product-card-meta">
          <span>{product.brand || "Product"}</span>
          <span>{product.merchantName || "Merchant unavailable"}</span>
        </div>
        {pick && <span className="product-pick"><Sparkles size={13} /> {pick}</span>}
        <h3>{product.productTitle}</h3>
        <div className="product-rating">
          <Star size={14} aria-hidden="true" />
          {product.rating ? (
            <span><strong>{product.rating.value} / 5</strong> · {reviewCount.format(product.rating.count)} reviews
              {product.rating.sourceCount > 0 && ` from ${product.rating.sourceCount} rating sources`}</span>
          ) : <span>No customer rating available</span>}
        </div>
        {product.price && <strong className="product-price">{product.currencyCode} {product.price}</strong>}
        {eligible && <div className="product-fit">
          <strong>{pick ? "Why this is a top pick" : "Why this offer is shown"}</strong>
          <p>{product.recommendationExplanation ||
            `Channel3 matched this offer from ${product.merchantName || "the listed retailer"} to your search. Its listed price of ${product.currencyCode} ${product.price} fits your current search and spending limits.`}</p>
        </div>}
        {product.description && <details className="product-details">
          <summary>Product details</summary><p>{product.description}</p>
        </details>}
        <div className={`eligibility-note ${eligible ? "eligible-note" : "rejected-note"}`}>
          <StatusIcon size={15} />
          <span>{eligible ? "Within your active spending policy" : product.rejectionReason || "Rejected by your active spending policy"}</span>
        </div>
        {eligible && comparison && <label className="shop-checkbox">
          <input type="checkbox" checked={comparison.selected} disabled={comparison.full && !comparison.selected}
            aria-label={`Compare ${product.productTitle} from ${product.merchantName}`}
            onChange={() => comparison.toggle(product.candidateId)} />
          Compare offer
        </label>}
        {eligible && (added ? (
          <Link className="reset-button product-checkout-button" to="/cart"><CheckCircle2 size={16} /> In cart · View cart</Link>
        ) : (
          <button className="save-button product-checkout-button" type="button" onClick={() => onAdd(product)}
            disabled={disabled}><ShoppingCart size={16} /> {busy ? "Adding…" : "Add to cart"}</button>
        ))}
        {product.offerUrl && <a href={product.offerUrl} target="_blank" rel="noreferrer">
          Retailer listing and reviews <ExternalLink size={13} />
        </a>}
      </div>
    </article>
  );
}

const comparisonRows = [
  ["Price", (offer) => `${offer.currencyCode} ${offer.price}`],
  ["Brand", (offer) => offer.brand],
  ["Retailer", (offer) => offer.merchantName],
  ["Availability", () => "In stock at search"],
  ["Rating", (offer) => offer.rating
    && `${offer.rating.value} / 5 · ${reviewCount.format(offer.rating.count)} reviews`],
  ["Product details", (offer) => offer.description],
  ["Match to your request", (offer) => offer.recommendationExplanation],
];

function OfferComparison({ offers }) {
  return (
    <section className="offer-comparison" aria-labelledby="offer-comparison-heading">
      <h3 id="offer-comparison-heading">Side-by-side comparison</h3>
      <div className="comparison-scroll" tabIndex={0} role="region" aria-label="Scrollable offer comparison">
        <table>
          <thead>
            <tr>
              <th scope="col">Offer detail</th>
              {offers.map((offer) => <th scope="col" key={offer.candidateId}>
                {offer.offerUrl ? <a href={offer.offerUrl} target="_blank" rel="noreferrer">{offer.productTitle}</a>
                  : offer.productTitle}
              </th>)}
            </tr>
          </thead>
          <tbody>
            {comparisonRows.map(([label, value]) => <tr key={label}>
              <th scope="row">{label}</th>
              {offers.map((offer) => <td key={offer.candidateId}>{value(offer) || "Unavailable"}</td>)}
            </tr>)}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function readResult(userId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem("spendwise_search_result"));
    return saved?.userId === userId ? saved.result : null;
  } catch {
    return null;
  }
}

function availableValues(products, field) {
  return [...new Set(products.map((product) => product[field]).filter(Boolean))].sort((a, b) => a.localeCompare(b));
}

function compareOffers(a, b, sort) {
  if (sort === "rating") {
    return (b.rating?.value ?? -1) - (a.rating?.value ?? -1) || (b.rating?.count ?? 0) - (a.rating?.count ?? 0);
  }
  if (sort === "low" || sort === "high") {
    if (a.price == null || b.price == null) return Number(a.price == null) - Number(b.price == null);
    if (a.currencyCode !== b.currencyCode) return a.currencyCode.localeCompare(b.currencyCode);
    return (Number(a.price) - Number(b.price)) * (sort === "high" ? -1 : 1);
  }
  return 0;
}

export default function Products({ userId }) {
  const [filters, setFilters] = useState(defaults);
  const [saved, setSaved] = useState(null);
  const [adding, setAdding] = useState(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [selectedIds, setSelectedIds] = useState([]);
  const [showComparison, setShowComparison] = useState(false);
  const result = readResult(userId);

  useEffect(() => {
    let active = true;
    api.getCart().then((cart) => {
      if (active) setSaved(cart.map(({ candidateId }) => candidateId));
    }).catch((failure) => {
      if (!active) return;
      setSaved([]);
      setError(failure.message);
    });
    return () => { active = false; };
  }, [userId]);

  async function add(product) {
    setAdding(product.candidateId);
    setStatus("");
    setError("");
    try {
      await api.addToCart(product.candidateId);
      setSaved((current) => [...current, product.candidateId]);
      setStatus(`${product.productTitle} added to your cart.`);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setAdding(null);
    }
  }

  function change(event) {
    const { name, value } = event.target;
    setFilters((current) => ({ ...current, [name]: value }));
  }

  function toggleComparison(candidateId) {
    setSelectedIds((current) => {
      if (current.includes(candidateId)) return current.filter((id) => id !== candidateId);
      return current.length < comparisonLimit ? [...current, candidateId] : current;
    });
  }

  if (!result) return (
    <section className="panel cart-list">
      <h2>Product discovery</h2>
      <p>Ask the Shopping Assistant for products to see recommendations and offers.</p>
      <div className="shop-actions">
        <Link className="save-button" to="/assistant">Search products</Link>
        <Link className="reset-button" to="/cart">View cart</Link>
      </div>
    </section>
  );

  const all = [...result.eligibleCandidates, ...result.rejectedCandidates];
  const filtered = all.filter((product) =>
    (!filters.brand || filters.brand === product.brand)
    && (!filters.merchant || filters.merchant === product.merchantName)
    && (!filters.max || (product.price != null && product.currencyCode === result.intent?.currencyCode
      && Number(product.price) <= Number(filters.max)))
    && (filters.status === "all" || product.eligible === (filters.status === "eligible")))
    .sort((a, b) => compareOffers(a, b, filters.sort));
  const top = result.eligibleCandidates[0]?.candidateId;
  const canCompare = result.eligibleCandidates.length >= 2;
  const selectedOffers = selectedIds.map((id) => result.eligibleCandidates.find((offer) => offer.candidateId === id))
    .filter(Boolean);
  const comparisonVisible = showComparison && selectedOffers.length >= 2;

  return (
    <div className="product-results">
      <section className="panel results-panel">
        <div className="shop-heading">
          <div>
            <span className="eyebrow">POLICY-AWARE DISCOVERY</span>
            <h2>{result.intent?.normalizedQuery || "Search results"}</h2>
            <p className="shop-muted">{result.recommendation || "Results checked against your spending policy."}</p>
          </div>
          <Link className="reset-button" to="/cart"><ShoppingCart size={16} /> View cart</Link>
        </div>
        <div className="product-filters" aria-label="Filter and sort offers">
          <div className="shop-heading"><strong><SlidersHorizontal size={16} /> Refine offers</strong>
            <button type="button" className="text-button" onClick={() => setFilters(defaults)}>Clear filters</button></div>
          <div className="product-filter-grid">
            <label className="form-group"><span>Sort</span><select name="sort" value={filters.sort} onChange={change}>
              <option value="recommended">Recommended</option><option value="low">Price low to high</option>
              <option value="high">Price high to low</option><option value="rating">Highest rating</option>
            </select></label>
            <label className="form-group"><span>Brand</span><select name="brand" value={filters.brand} onChange={change}>
              <option value="">All brands</option>
              {availableValues(all, "brand").map((brand) => <option key={brand}>{brand}</option>)}
            </select></label>
            <label className="form-group"><span>Retailer</span><select name="merchant" value={filters.merchant} onChange={change}>
              <option value="">All retailers</option>
              {availableValues(all, "merchantName").map((merchant) => <option key={merchant}>{merchant}</option>)}
            </select></label>
            <label className="form-group"><span>Max price {result.intent?.currencyCode}</span>
              <input name="max" type="number" min="0" step="0.01" value={filters.max} onChange={change} /></label>
            <label className="form-group"><span>Policy status</span><select name="status" value={filters.status} onChange={change}>
              <option value="all">All offers</option><option value="eligible">Eligible only</option>
              <option value="rejected">Filtered out</option>
            </select></label>
          </div>
        </div>
        {canCompare && <div className="comparison-tools shop-heading">
          <span className="shop-muted" role="status">Select 2 or 3 eligible offers to compare · {selectedOffers.length} selected</span>
          <div className="shop-actions">
            <button className="reset-button" type="button" disabled={selectedOffers.length < 2}
              aria-expanded={comparisonVisible} onClick={() => setShowComparison(!comparisonVisible)}>
              {comparisonVisible ? "Hide comparison" : "Compare selected"}
            </button>
            {selectedOffers.length > 0 && <button className="text-button" type="button" onClick={() => {
              setSelectedIds([]);
              setShowComparison(false);
            }}>Clear selection</button>}
          </div>
        </div>}
        {comparisonVisible && <OfferComparison offers={selectedOffers} />}
        <p className="shop-muted" role="status">{filtered.length} of {all.length} offers {status && ` · ${status}`}</p>
        {error && <div className="policy-message error-message" role="alert">{error}</div>}
        {filtered.length ? <div className="product-grid">
          {filtered.map((product) => (
            <ProductCard key={product.candidateId} product={product} added={saved?.includes(product.candidateId)}
              busy={adding === product.candidateId} disabled={!saved || Boolean(adding)} onAdd={add}
              comparison={canCompare ? {
                selected: selectedIds.includes(product.candidateId),
                full: selectedOffers.length === comparisonLimit,
                toggle: toggleComparison,
              } : null}
              pick={product.candidateId === top && (result.aiRanked ? "AI top pick" : "Top catalog match")} />
          ))}
        </div> : <div className="empty-results"><ShieldCheck size={20} /><p>No offers match these filters.</p></div>}
      </section>
    </div>
  );
}
