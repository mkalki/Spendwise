import { useState } from "react";
import { ArrowRight, Eye, EyeOff, ShieldCheck, ShoppingCart, Sparkles } from "lucide-react";
import { api } from "../services/api";
import "./Shopping.css";

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ identifier: "", email: "", username: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [verificationEmail, setVerificationEmail] = useState("");
  const [resendMessage, setResendMessage] = useState("");
  const [resending, setResending] = useState(false);
  const isLogin = mode === "login";

  function change(event) {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    setError("");
  }

  function switchMode(next) {
    if (next === mode) return;
    setMode(next);
    setForm((previous) => ({ ...previous, password: "" }));
    setShowPassword(false);
    setError("");
  }

  async function submit(event) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      if (isLogin) {
        const user = await api.login(form.identifier, form.password);
        onAuthenticated(user);
      } else {
        const result = await api.signup(form.email, form.username, form.password);
        setVerificationEmail(result.email || form.email);
        setResendMessage("");
      }
    } catch (failure) {
      setError(failure.code === "EMAIL_NOT_VERIFIED"
        ? "Verify your email address before signing in. You can request a new verification email."
        : failure.message || "Authentication failed.");
    } finally {
      setSaving(false);
    }
  }

  async function resendVerification() {
    setResending(true);
    setResendMessage("");
    try {
      const result = await api.resendVerificationEmail(verificationEmail);
      setResendMessage(result.message);
    } catch (failure) {
      setResendMessage(failure.message || "Could not request another verification email.");
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="auth-workspace">
      <aside className="auth-intro">
        <div className="auth-intro-mark"><ShieldCheck size={34} /></div>
        <span className="eyebrow">SPENDWISE ACCESS</span>
        <h2>{isLogin ? "Pick up where you left off." : "Make smarter purchases."}</h2>
        <ul>
          <li><ShoppingCart size={16} /> Take your saved cart across devices</li>
          <li><Sparkles size={16} /> See why offers fit your search</li>
          <li><ShieldCheck size={16} /> Keep checkout within your limits</li>
        </ul>
      </aside>
      <section className="auth-form-area" aria-labelledby="auth-title">
        {verificationEmail ? (
          <div className="auth-verification-notice" aria-live="polite">
            <ShieldCheck size={30} />
            <h3 id="auth-title">Check your email</h3>
            <p>We sent a verification link to <strong>{verificationEmail}</strong>. Verify it before signing in.</p>
            <button className="save-button" type="button" disabled={resending} onClick={resendVerification}>
              {resending ? "Sending…" : "Resend verification email"}
            </button>
            {resendMessage && <p role="status">{resendMessage}</p>}
            <button className="text-button" type="button" onClick={() => {
              setVerificationEmail("");
              switchMode("login");
            }}>Return to sign in</button>
          </div>
        ) : <>
        <div className="auth-mode" role="group" aria-label="Account access">
          <button type="button" aria-pressed={isLogin} disabled={saving} onClick={() => switchMode("login")}>Sign in</button>
          <button type="button" aria-pressed={!isLogin} disabled={saving} onClick={() => switchMode("signup")}>Register</button>
        </div>
        <h3 id="auth-title">{isLogin ? "Sign in to Spendwise" : "Create your account"}</h3>
        <a className="reset-button auth-google" href={isLogin ? "/api/auth/google/login" : "/api/auth/google/signup"}>
          <span aria-hidden="true">G</span> Continue with Google <ArrowRight size={15} />
        </a>
        <p className="auth-divider">or continue with email</p>
        {error && <div className="policy-message error-message" role="alert">{error}</div>}
        <form className="auth-credentials" onSubmit={submit}>
          {isLogin ? (
            <label className="form-group">
              <span>Email or username</span>
              <input name="identifier" value={form.identifier} onChange={change} required autoComplete="username" disabled={saving} />
            </label>
          ) : (
            <>
              <label className="form-group">
                <span>Email</span>
                <input name="email" type="email" value={form.email} onChange={change} autoComplete="email"
                  maxLength={320} required disabled={saving} />
              </label>
              <label className="form-group">
                <span>Username</span>
                <input name="username" value={form.username} onChange={change} autoComplete="username"
                  minLength={3} maxLength={32} pattern="[A-Za-z0-9._\\-]+" aria-describedby="auth-username-help"
                  required disabled={saving} />
                <small id="auth-username-help">3–32 characters. Letters, numbers, periods, underscores, and hyphens.</small>
              </label>
            </>
          )}
          <div className="form-group">
            <label htmlFor="auth-password">Password</label>
            <div className="auth-password">
              <input id="auth-password" name="password" type={showPassword ? "text" : "password"}
                value={form.password} onChange={change} required minLength={isLogin ? undefined : 12} maxLength={128}
                aria-describedby={isLogin ? undefined : "auth-password-help"}
                autoComplete={isLogin ? "current-password" : "new-password"} disabled={saving} />
              <button type="button" className="text-button" aria-label={showPassword ? "Hide password" : "Show password"}
                aria-pressed={showPassword} onClick={() => setShowPassword((visible) => !visible)}>
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
            {!isLogin && <small id="auth-password-help">{Math.min(form.password.length, 12)} / 12 minimum characters</small>}
          </div>
          <button className="save-button" type="submit" disabled={saving}>
            {saving ? "Working…" : isLogin ? "Sign in" : "Create account"} <ArrowRight size={16} />
          </button>
        </form>
        </>}
      </section>
    </div>
  );
}
