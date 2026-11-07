
import { Routes, Route, Navigate } from "react-router-dom";

import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";

import Dashboard from "./pages/Dashboard";
import PolicySettings from "./pages/PolicySettings";
import ShoppingAssistant from "./pages/ShoppingAssistant";
import Products from "./pages/Products";
import Transactions from "./pages/Transactions";

export default function App() {
  return (
    <div className="app-shell">

      <Sidebar />

      <main className="main-content">

        <Topbar />

        <div className="page-content">

          <Routes>
            <Route path="/" element={<Dashboard />} />

            <Route
              path="/policies"
              element={<PolicySettings />}
            />

            <Route
              path="/assistant"
              element={<ShoppingAssistant />}
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
              path="*"
              element={<Navigate to="/" replace />}
            />
          </Routes>

        </div>

      </main>

    </div>
  );
}
