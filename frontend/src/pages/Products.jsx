import { useMemo, useState } from "react";
import { CheckCircle2, ExternalLink, ShieldCheck, XCircle } from "lucide-react";
import { api } from "../services/api";

function ProductCard({ product, onCheckout, checkoutLoading }) {
  const eligible = Boolean(product.eligible);

  return (
    <article className="panel product-card">
      <div className="product-media">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.productTitle} className="product-image" />
        ) : (
          <div className="product-image product-image-placeholder" aria-hidden="true">
            <ShoppingBagIcon />
          </div>
        )}
        <span className={eligible ? "product-status eligible" : "product-status rejected"}>
          {eligible ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
          {eligible ? "Eligible" : "Not eligible"}
        </span>
      </div>
      <div className="product-card-content">
        <div className="product-card-meta">
          <span>{product.brand || "Product"}</span>
          <span>{product.merchantName || "Merchant unavailable"}</span>
        </div>
        <h3>{product.productTitle}</h3>
        {product.description && <p className="product-description">{product.description}</p>}
        <strong className="product-price">{product.currencyCode} {product.price}</strong>
        <div className={eligible ? "eligibility-note eligible-note" : "eligibility-note rejected-note"}>
          {eligible ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
          <span>{eligible ? "Within your active spending policy" : product.rejectionReason || "Rejected by your active spending policy"}</span>
        </div>
        {eligible && (
          <button
            className="save-button product-checkout-button"
            type="button"
            onClick={() => onCheckout(product)}
            disabled={checkoutLoading}
          >
            {checkoutLoading ? "Preparing checkout..." : "Continue to checkout"}
          </button>
        )}
        {product.offerUrl && (
          <a href={product.offerUrl} target="_blank" rel="noreferrer">
            Visit retailer <ExternalLink size={13} />
          </a>
        )}
      </div>
    </article>
  );
}

function ShoppingBagIcon() {
  return <ShieldCheck size={26} />;
}

export default function Products() {
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const result = useMemo(() => {
    try {
      return JSON.parse(sessionStorage.getItem("spendwise_search_result")) || null;
    } catch {
      return null;
    }
  }, []);

  if (!result) {
    return (
      <div className="panel placeholder-page">
        <h2>Product Discovery</h2>
        <p>Search from the Shopping Assistant to see policy-filtered products.</p>
      </div>
    );
  }

  async function handleCheckout(product) {
    setSelectedProduct(product);
    setCheckoutLoading(true);
    setErrorMessage("");
    try {
      const checkout = await api.createCheckout(result.searchSessionId, product.candidateId);
      sessionStorage.setItem("spendwise_checkout_id", checkout.checkoutSessionId);
      if (!checkout.approvalUrl) {
        throw new Error("PayPal did not return an approval URL.");
      }
      window.location.assign(checkout.approvalUrl);
    } catch (error) {
      setErrorMessage(error.message || "Unable to prepare checkout.");
      setCheckoutLoading(false);
    }
  }

  return (
    <div className="product-results">
      {errorMessage && <div className="policy-message error-message">{errorMessage}</div>}
      {selectedProduct && !checkoutLoading && (
        <div className="panel checkout-approval">
          <div className="checkout-approval-icon"><ShieldCheck size={20} /></div>
          <div>
            <span className="section-kicker">CHECKOUT PREVIEW</span>
            <h3>Ready for secure approval</h3>
            <p>{selectedProduct.productTitle} is within your active spending policy. PayPal Sandbox will open to authorize the reserved amount.</p>
          </div>
        </div>
      )}
      <div className="panel results-panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">POLICY-AWARE DISCOVERY</span>
            <h3>{result.intent?.normalizedQuery || "Search results"}</h3>
            <p>{result.recommendation || "Results filtered by your spending policy."}</p>
          </div>
          <ShieldCheck size={20} />
        </div>
        <div className="product-grid">
          {(result.eligibleCandidates || []).map((product) => (
            <ProductCard
              key={product.candidateId}
              product={product}
              onCheckout={handleCheckout}
              checkoutLoading={checkoutLoading}
            />
          ))}
        </div>
        {!result.eligibleCandidates?.length && <div className="empty-results"><ShieldCheck size={20} /><p>No eligible products were found for this search.</p></div>}
      </div>

      {!!result.rejectedCandidates?.length && (
        <div className="panel rejected-results">
          <div className="panel-heading">
            <div>
              <span className="section-kicker">PROTECTION REVIEW</span>
              <h3>Filtered by your policy</h3>
              <p>These results remain visible so you can understand the protection decision.</p>
            </div>
            <XCircle size={20} />
          </div>
          <div className="product-grid">
            {result.rejectedCandidates.map((product) => (
              <ProductCard
                key={product.candidateId}
                product={product}
                onCheckout={() => {}}
                checkoutLoading={false}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}