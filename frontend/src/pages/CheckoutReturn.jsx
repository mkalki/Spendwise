import { useEffect, useState } from "react";
import { ArrowLeft, CheckCircle2, CircleAlert, Link as LinkIcon, XCircle } from "lucide-react";
import { Link, useLocation } from "react-router-dom";

import { api } from "../services/api";

export default function CheckoutReturn() {
  const location = useLocation();
  const [status, setStatus] = useState("processing");
  const [message, setMessage] = useState("Verifying your PayPal Sandbox payment...");
  const [checkout, setCheckout] = useState(null);

  useEffect(() => {
    const checkoutId = sessionStorage.getItem("spendwise_checkout_id");
    const cancelled = location.pathname.endsWith("/cancel");

    if (!checkoutId) {
      setStatus("error");
      setMessage("No checkout session was found.");
      return;
    }

    const finish = cancelled ? api.cancelCheckout(checkoutId) : api.captureCheckout(checkoutId);
    finish
      .then((checkout) => {
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
        setStatus("error");
        setMessage(error.message || "Unable to complete checkout.");
      });
  }, [location.pathname]);

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
      <p>{message}</p>
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
        <Link className="reset-button" to="/products">
          <ArrowLeft size={16} /> Back to products
        </Link>
      </div>
    </div>
  );
}

function ShieldCheckIcon() {
  return <CheckCircle2 className="checkout-processing-icon" size={34} />;
}
