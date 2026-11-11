import { useEffect, useRef, useState } from "react";
import { ArrowLeft, CheckCircle2, CircleAlert, Link as LinkIcon, XCircle } from "lucide-react";
import { Link, useLocation } from "react-router-dom";

import { api } from "../services/api";

export default function CheckoutReturn() {
  const location = useLocation();
  const checkoutId = sessionStorage.getItem("spendwise_checkout_id");
  const [status, setStatus] = useState(checkoutId ? "processing" : "error");
  const [message, setMessage] = useState("Verifying your PayPal Sandbox payment...");
  const [checkout, setCheckout] = useState(null);
  const pending = useRef(null);

  useEffect(() => {
    const cancelled = location.pathname.endsWith("/cancel");

    if (!checkoutId) return;

    const key = `${checkoutId}:${location.pathname}`;
    if (pending.current?.key !== key) {
      const finish = cancelled ? api.cancelCheckout(checkoutId) : api.captureCheckout(checkoutId);
      pending.current = { key, finish };
    }
    let active = true;
    pending.current.finish
      .then((checkout) => {
        if (!active) return;
        sessionStorage.removeItem("spendwise_checkout_id");
        setCheckout(checkout);
        setStatus(cancelled ? "cancelled" : "success");
        setMessage(
          cancelled
            ? "The checkout was cancelled and the reservation was released."
            : `Payment captured successfully: ${checkout.currency} ${checkout.amount}.`
        );
      })
      .catch((error) => {
        if (!active) return;
        setStatus("error");
        setMessage(error.message || "Unable to complete checkout.");
      });
    return () => { active = false; };
  }, [checkoutId, location.pathname]);

  return (
    <div className={`panel checkout-result checkout-${status}`}>
      <div className="checkout-state-icon">
        {status === "success" && <CheckCircle2 size={34} />}
        {status === "cancelled" && <XCircle size={34} />}
        {status === "error" && <CircleAlert size={34} />}
        {status === "processing" && <ShieldCheckIcon />}
      </div>
      <span className="eyebrow">PAYPAL SANDBOX CHECKOUT</span>
      <h2>{status === "success" ? "Payment complete" : status === "cancelled" ? "Checkout cancelled" : status === "error" ? "Checkout could not be completed" : "Verifying your checkout"}</h2>
      <p>{!checkoutId && !checkout && status === "error" ? "No checkout session was found." : message}</p>
      {checkout && (
        <div className="checkout-transaction">
          <span>Transaction amount</span>
          <strong>{checkout.currency} {checkout.amount}</strong>
        </div>
      )}
      <div className="checkout-actions">
        <Link className="save-button" to="/transactions">
          <LinkIcon size={16} /> View transactions
        </Link>
        <Link className="reset-button" to="/cart">
          <ArrowLeft size={16} /> Back to cart
        </Link>
        <Link className="reset-button" to="/pro">Manage Spendwise Pro</Link>
      </div>
    </div>
  );
}

function ShieldCheckIcon() {
  return <CheckCircle2 className="checkout-processing-icon" size={34} />;
}
