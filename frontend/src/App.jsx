
import { useEffect, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";

import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";

import Dashboard from "./pages/Dashboard";
import PolicySettings from "./pages/PolicySettings";
import ShoppingAssistant from "./pages/ShoppingAssistant";
import Products from "./pages/Products";
import Cart from "./pages/Cart";
import Transactions from "./pages/Transactions";
import Landing from "./pages/Landing";
import CheckoutReturn from "./pages/CheckoutReturn";
import Pro from "./pages/Pro";
import ProfileSettings from "./pages/ProfileSettings";
import { api } from "./services/api";
import AmbientScene from "./components/AmbientScene";
import { ExperienceLoader } from "./components/ExperienceEffects";

export default function App() {
  const location = useLocation();
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [slowSession, setSlowSession] = useState(false);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "instant" });
  }, [location.pathname]);

  useEffect(() => {
    let cancelled = false;
    let controller;
    let retryTimer;
    const slowTimer = window.setTimeout(() => setSlowSession(true), 12000);

    async function checkSession() {
      controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), 30000);
      try {
        const currentUser = await api.getCurrentUser({ signal: controller.signal });
        if (!cancelled) {
          setUser(currentUser);
          setCheckingSession(false);
        }
      } catch (error) {
        if (cancelled) return;
        if (error.status === 401) {
          setCheckingSession(false);
          return;
        }
        setSlowSession(true);
        retryTimer = window.setTimeout(checkSession, 3000);
      } finally {
        window.clearTimeout(timeout);
      }
    }

    checkSession();
    return () => {
      cancelled = true;
      controller?.abort();
      window.clearTimeout(slowTimer);
      window.clearTimeout(retryTimer);
    };
  }, []);

  const ready = !checkingSession || slowSession;
  async function logout() {
    try { await api.logout(); setUser(null); }
    catch (error) { console.error("Logout failed:", error); }
  }

  if (checkingSession) {
    return (
      <>
        <ExperienceLoader ready={ready} />
        <AmbientScene />
        <main className="page-content"><div className="panel" role="status">
          {slowSession
            ? "Starting the Spendwise server. This page will continue when it is ready."
            : "Checking your session..."}
        </div></main>
      </>
    );
  }

  if (!user) {
    return (
      <>
        <ExperienceLoader ready={ready} />
        <AmbientScene />
        <Landing />
      </>
    );
  }

  return (
    <div className="app-shell studio-shell" id="top">
      <ExperienceLoader ready={ready} />

      <Sidebar user={user} onLogout={logout} />

      <main className="main-content">

        <Topbar />

        <div className="page-content">

          <Routes>
            <Route path="/" element={<Dashboard user={user} />} />

            <Route
              path="/policies"
              element={<PolicySettings />}
            />

            <Route
              path="/assistant"
              element={<ShoppingAssistant key={location.search} userId={user.id} />}
            />

            <Route
              path="/products"
              element={<Products userId={user.id} />}
            />

            <Route path="/cart" element={<Cart key={user.id} />} />
            <Route path="/pro" element={<Pro />} />
            <Route path="/profile" element={<ProfileSettings user={user} onUserUpdate={setUser} />} />

            <Route
              path="/transactions"
              element={<Transactions />}
            />

            <Route
              path="/checkout/return"
              element={<CheckoutReturn />}
            />

            <Route
              path="/checkout/cancel"
              element={<CheckoutReturn />}
            />

            <Route
              path="*"
              element={<Navigate to="/" replace />}
            />
          </Routes>

        </div>

      </main>

    </div>
  );
}

