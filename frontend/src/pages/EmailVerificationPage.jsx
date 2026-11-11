import { useEffect, useState } from "react";
import { ArrowRight, MailCheck } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { api } from "../services/api";
import "./EmailVerificationPage.css";

export default function EmailVerificationPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const signupEmail = searchParams.get("email") || "";
  const [state, setState] = useState(token ? "verifying" : signupEmail ? "pending" : "invalid");
  const [email, setEmail] = useState(signupEmail);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!token) return undefined;
    let active = true;
    api.verifyEmail(token)
      .then(() => { if (active) setState("verified"); })
      .catch((error) => {
        if (!active) return;
        setMessage(error.message || "This verification link is invalid or expired.");
        setState("invalid");
      });
    return () => { active = false; };
  }, [token]);

  async function resend(event) {
    event.preventDefault();
    setSending(true);
    setMessage("");
    try {
      const result = await api.resendVerificationEmail(email);
      setMessage(result.message);
    } catch (error) {
      setMessage(error.message || "Could not request another verification email.");
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="verify-email-page">
      <section className="verify-email-card" aria-live="polite">
        <MailCheck size={38} aria-hidden="true" />
        {state === "verifying" && <><h1>Verifying your email…</h1><p>Please wait a moment.</p></>}
        {state === "verified" && <>
          <h1>Email verified</h1>
          <p>Your address is confirmed. You can now sign in to Spendwise.</p>
          <a className="save-button" href="/?auth=login">Continue to sign in <ArrowRight size={16} /></a>
        </>}
        {state === "pending" && <>
          <h1>Verify your email</h1>
          <p>A verification link was requested for <strong>{email}</strong>. Check your inbox and spam folder.</p>
          <form className="verify-email-resend" onSubmit={resend}>
            <button className="save-button" type="submit" disabled={sending}>
              {sending ? "Sending…" : "Resend verification email"}
            </button>
          </form>
          {message && <p role="status">{message}</p>}
          <p>Already verified? <a className="verify-email-login" href="/?auth=login">Log in</a></p>
        </>}
        {state === "invalid" && <>
          <h1>Verification link unavailable</h1>
          <p>{message || "The link may have expired or already been used. Request a new one below."}</p>
          <form className="verify-email-resend" onSubmit={resend}>
            <label className="form-group">
              <span>Email address</span>
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)}
                autoComplete="email" maxLength={320} required />
            </label>
            <button className="save-button" type="submit" disabled={sending}>
              {sending ? "Sending…" : "Resend verification email"}
            </button>
          </form>
          {message && <p role="status">{message}</p>}
          <p><a className="verify-email-login" href="/?auth=login">Return to sign in</a></p>
        </>}
      </section>
    </main>
  );
}
