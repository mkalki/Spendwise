
import { useEffect, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";

import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";

import Dashboard from "./pages/Dashboard";
import PolicySettings from "./pages/PolicySettings";
import ShoppingAssistant from "./pages/ShoppingAssistant";
import Products from "./pages/Products";
import Transactions from "./pages/Transactions";
import Landing from "./pages/Landing";
import CheckoutReturn from "./pages/CheckoutReturn";
import { api } from "./services/api";
import AmbientScene from "./components/AmbientScene";
import { ExperienceLoader } from "./components/ExperienceEffects";

export default function App() {
  const location = useLocation();
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "instant" });
  }, [location.pathname]);

  useEffect(() => {
    api.getCurrentUser()
      .then(setUser)
      .catch((error) => {
        if (error.status !== 401) {
          console.error("Session check failed:", error);
        }
      })
      .finally(() => setCheckingSession(false));
  }, []);

  const ready = !checkingSession;

  if (checkingSession) {
    return (
      <>
        <ExperienceLoader ready={ready} />
        <AmbientScene />
        <main className="page-content"><div className="panel">Checking your session...</div></main>
      </>
    );
  }

  if (!user) {
    return (
      <>
        <ExperienceLoader ready={ready} />
        <AmbientScene />
        <Landing onAuthenticated={setUser} />
      </>
    );
  }

  return (
    <div className="app-shell studio-shell" id="top">
      <ExperienceLoader ready={ready} />

      <Sidebar />

      <main className="main-content">

        <Topbar
          user={user}
          onLogout={async () => {
            try {
              await api.logout();
              setUser(null);
            } catch (error) {
              console.error("Logout failed:", error);
            }
          }}
        />

        <div className="page-content">

          <Routes>
            <Route path="/" element={<Dashboard user={user} />} />

            <Route
              path="/policies"
              element={<PolicySettings />}
            />

            <Route
              path="/assistant"
              element={<ShoppingAssistant key={location.search} />}
            />

            <Route
              path="/products"
              element={<Products />}
            />

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

