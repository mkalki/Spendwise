import { useState } from "react";
import { ArrowRight, ShieldCheck, ShoppingCart, Sparkles } from "lucide-react";
import "./Shopping.css";

export default function AuthPage() {
  const [mode, setMode] = useState("login");
  const isLogin = mode === "login";

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
        <div className="auth-mode" role="group" aria-label="Google account access">
          <button type="button" aria-pressed={isLogin} onClick={() => setMode("login")}>Sign in</button>
          <button type="button" aria-pressed={!isLogin} onClick={() => setMode("signup")}>Register</button>
        </div>
        <h3 id="auth-title">{isLogin ? "Sign in to Spendwise" : "Create your account"}</h3>
        <p className="auth-divider">Continue securely with your Google account</p>
        <a className="reset-button auth-google" href={isLogin ? "/api/auth/google/login" : "/api/auth/google/signup"}>
          <span aria-hidden="true">G</span> {isLogin ? "Sign in with Google" : "Register with Google"} <ArrowRight size={15} />
        </a>
      </section>
    </div>
  );
}
