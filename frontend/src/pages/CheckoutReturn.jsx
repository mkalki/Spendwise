import { useEffect, useRef, useState } from "react";
import { ArrowLeft, CheckCircle2, CircleAlert, Link as LinkIcon, XCircle } from "lucide-react";
import { Link, useLocation } from "react-router-dom";

import { api } from "../services/api";

export default function CheckoutReturn() {
  const location = useLocation();
  const checkoutId = sessionStorage.getItem("spendwise_checkout_id");
  const paypalOrderId = new URLSearchParams(location.search).get("token");
  const isProCheckout = sessionStorage.getItem("spendwise_checkout_type") === "pro";
  const [status, setStatus] = useState(checkoutId || paypalOrderId ? "processing" : "error");
  const [message, setMessage] = useState("Verifying your PayPal Sandbox payment...");
  const [checkout, setCheckout] = useState(null);
  const pending = useRef(null);

  useEffect(() => {
    const cancelled = location.pathname.endsWith("/cancel");

    if (!checkoutId && !paypalOrderId) return;

    const key = `${checkoutId || paypalOrderId}:${location.pathname}`;
    if (pending.current?.key !== key) {
      const finish = (async () => {
        if (cancelled) {
          if (!checkoutId) throw new Error("Checkout session was not found, so Spendwise could not confirm cancellation.");
          return api.cancelCheckout(checkoutId);
        }
        if (checkoutId) {
          try {
            return await api.captureCheckout(checkoutId);
          } catch (captureError) {
            if (!paypalOrderId) throw captureError;
            const current = await api.getPaymentStatus(paypalOrderId);
            if (current.status === "CAPTURED") return current;
            throw captureError;
          }
        }
        const current = await api.getPaymentStatus(paypalOrderId);
        if (current.status === "CAPTURED") return current;
        if (current.checkoutSessionId) return api.captureCheckout(current.checkoutSessionId);
        throw new Error("Spendwise could not match this PayPal payment to a checkout.");
      })();
      pending.current = { key, finish };
    }
    let active = true;
    pending.current.finish
      .then((checkout) => {
        if (!active) return;
        sessionStorage.removeItem("spendwise_checkout_id");
        sessionStorage.removeItem("spendwise_checkout_type");
        setCheckout(checkout);
        setStatus(cancelled ? "cancelled" : "success");
        if (cancelled) {
          setMessage("The checkout was cancelled and the reservation was released.");
        } else if (isProCheckout) {
          api.getProMembership().then((membership) => {
            if (!active) return;
            window.dispatchEvent(new Event("spendwise:membership-updated"));
            setMessage(membership.pro
              ? `Spendwise Pro is active until ${new Date(membership.expiresAt).toLocaleDateString()}.`
              : `Payment captured successfully: ${checkout.currency} ${checkout.amount}. Pro status is still updating; refresh this page shortly.`);
          }).catch(() => {
            if (active) setMessage(`Payment captured successfully: ${checkout.currency} ${checkout.amount}. Pro status is still updating; refresh this page shortly.`);
          });
        } else {
          setMessage(`Payment captured successfully: ${checkout.currency} ${checkout.amount}.`);
        }
      })
      .catch((error) => {
        if (!active) return;
        setStatus("error");
        setMessage(error.message || "Unable to complete checkout.");
      });
    return () => { active = false; };
  }, [checkoutId, paypalOrderId, isProCheckout, location.pathname]);

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
