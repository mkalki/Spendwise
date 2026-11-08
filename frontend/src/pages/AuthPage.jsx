import { useState } from "react";

import { ShieldCheck } from "lucide-react";

import { api } from "../services/api";

export default function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState("google");
  const [form, setForm] = useState({
    identifier: "",
    email: "",
    username: "",
    password: "",
  });
  const [errorMessage, setErrorMessage] = useState("");
  const [saving, setSaving] = useState(false);

  function handleChange(event) {
    setForm((previous) => ({
      ...previous,
      [event.target.name]: event.target.value,
    }));
    setErrorMessage("");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setErrorMessage("");
    setSaving(true);

    try {
      const user = mode === "login"
        ? await api.login(form.identifier, form.password)
        : await api.signup(form.email, form.username, form.password);
      onAuthenticated(user);
    } catch (error) {
      setErrorMessage(error.message || "Authentication failed.");
    } finally {
      setSaving(false);
    }
  }

  const isLogin = mode === "login";
  const isGoogle = mode === "google";

  if (isGoogle) {
    return (
      <main className="page-content auth-page">
        <div className="panel auth-panel">
          <div className="policy-title">
            <ShieldCheck size={25} />
            <h2>Welcome to Spendwise</h2>
          </div>
          <p>Sign in securely to manage your protected shopping policy.</p>

          {errorMessage && (
            <div className="policy-message error-message">{errorMessage}</div>
          )}

          <button
            className="save-button"
            type="button"
            onClick={() => {
              window.location.assign("http://localhost:8080/api/auth/google/login");
            }}
          >
            Continue with Google
          </button>

          <button
            className="text-button"
            type="button"
            onClick={() => {
              setMode("login");
              setErrorMessage("");
            }}
          >
            Use email and password instead
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="page-content auth-page">
      <div className="panel auth-panel">
        <div className="policy-title">
          <ShieldCheck size={25} />
          <h2>{isLogin ? "Welcome back" : "Create your account"}</h2>
        </div>
        <p>Sign in to manage your protected shopping policy.</p>

        {errorMessage && (
          <div className="policy-message error-message">{errorMessage}</div>
        )}

        <form className="policy-form" onSubmit={handleSubmit}>
          {isLogin ? (
            <div className="form-group">
              <label htmlFor="identifier">Email or username</label>
              <input
                id="identifier"
                name="identifier"
                value={form.identifier}
                onChange={handleChange}
                required
                autoComplete="username"
              />
            </div>
          ) : (
            <>
              <div className="form-group">
                <label htmlFor="email">Email</label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  value={form.email}
                  onChange={handleChange}
                  required
                  autoComplete="email"
                />
              </div>
              <div className="form-group">
                <label htmlFor="username">Username</label>
                <input
                  id="username"
                  name="username"
                  value={form.username}
                  onChange={handleChange}
                  required
                  autoComplete="username"
                />
              </div>
            </>
          )}

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              minLength="12"
              value={form.password}
              onChange={handleChange}
              required
              autoComplete={isLogin ? "current-password" : "new-password"}
            />
          </div>

          <button className="save-button" type="submit" disabled={saving}>
            {saving ? "Please wait..." : isLogin ? "Sign in" : "Create account"}
          </button>
        </form>

        <button
          className="text-button"
          type="button"
          onClick={() => {
            setMode(isLogin ? "signup" : "login");
            setErrorMessage("");
          }}
        >
          {isLogin ? "Need an account? Sign up" : "Already have an account? Sign in"}
        </button>
      </div>
    </main>
  );
}
