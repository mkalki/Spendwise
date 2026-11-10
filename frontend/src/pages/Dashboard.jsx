import {
  Wallet,

  ShieldCheck,
  CircleDollarSign,
  ReceiptText,
  ArrowUpRight,
} from "lucide-react";
import { lazy, Suspense, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";

const ProductScene = lazy(() => import("../components/ProductScene"));
const statDefinitions = [
  {
    title: "Total spending",
    key: "spent",
    note: "Completed spend this week",
    icon: CircleDollarSign,
  },
  {
    title: "Remaining budget",
    key: "remaining",
    note: "Available within your policy",
    icon: Wallet,
  },
  {
    title: "Completed transactions",
    key: "orders",
    note: "Successful protected purchases",
    icon: ReceiptText,
  },
  {
    title: "Active spending policies",
    key: "policies",
    note: "Protection rules currently applied",
    icon: ShieldCheck,
  },
];

function StatCard({ item }) {

  const Icon = item.icon;

  return (

    <div className="stat-card">

      <div className="stat-top">

        <span>{item.title}</span>

        <div className="stat-icon">
          <Icon size={20} />
        </div>

      </div>

      <h2>{item.value}</h2>

      <p>{item.note}</p>

    </div>

  );
}

export default function Dashboard() {
  const navigate = useNavigate();
  const [policy, setPolicy] = useState(null);
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    Promise.all([api.getPolicy(), api.getTransactionSummary(), api.getTransactions()])
      .then(([policyData, summaryData, historyData]) => {
        setPolicy(policyData);
        setSummary(summaryData);
        setTransactions((historyData.transactions || []).slice(0, 5));
      })
      .catch((error) => setErrorMessage(error.message || "Unable to load dashboard data."))
      .finally(() => setLoading(false));
  }, []);

  const spent = Number(summary?.completedSpend || 0);
  const weeklyBudget = Number(policy?.weeklySpendingLimit || 0);
  const remaining = Number(summary?.remainingBudget || 0);
  const percentConsumed = weeklyBudget
    ? Math.min(100, Math.max(0, Math.round((spent / weeklyBudget) * 100)))
    : 0;
  const currency = policy?.currency || summary?.currency || "USD";
  const stats = statDefinitions.map((item) => ({
    ...item,
    value: loading
      ? "—"
      : item.key === "weeklyBudget"
      ? `${currency} ${weeklyBudget.toFixed(2)}`
      : item.key === "spent"
        ? `${currency} ${spent.toFixed(2)}`
        : item.key === "remaining"
          ? `${currency} ${remaining.toFixed(2)}`
          : item.key === "orders"
            ? String(summary?.completedOrders || 0)
            : policy ? "1 active" : "Not configured",
  }));

  return (

    <div className="dashboard">
      {errorMessage && <div className="policy-message error-message">{errorMessage}</div>}

      <div className="welcome-section">

        <div>
          <span className="eyebrow">YOUR PROTECTED SHOPPING SPACE</span>
          <h2>Spend with intention.</h2>

          <p>
            Discover what matters, make confident purchases, and keep every
            decision aligned with your personal spending rules.
          </p>

          <button className="save-button hero-action" type="button" onClick={() => navigate("/assistant")}>
            Open shopping assistant
            <ArrowUpRight size={16} />
          </button>
        </div>

        <div className="dashboard-3d"><Suspense fallback={null}><ProductScene /></Suspense></div><div className="hero-status">
          <span className="hero-status-dot" />
          <span>Protection active</span>
        </div>

      </div>

      <div className="stats-grid" aria-busy={loading}>

        {stats.map((item) => (
          <StatCard
            key={item.title}
            item={item}
          />
        ))}

      </div>

      <div className="dashboard-grid">

        <div className="panel budget-panel">

          <div className="panel-heading">

            <div>
              <h3>Weekly budget</h3>
              <p>Your spending overview</p>
            </div>

            <Wallet size={21} />

          </div>

          <div className="budget-content">

            <div
              className="budget-ring"
              style={{
                "--budget-progress": `${percentConsumed * 3.6}deg`,
              }}
            >

              <div className="budget-ring-inner">

                <span>{percentConsumed}%</span>
                <small>Consumed</small>

              </div>

            </div>

            <div className="budget-details">

              <div>
                <span className="dot spent-dot"></span>
                <span>Spent</span>
                <strong>{currency} {spent.toFixed(2)}</strong>
              </div>

              <div>
                <span className="dot remaining-dot"></span>
                <span>Remaining</span>
                <strong>{currency} {remaining.toFixed(2)}</strong>
              </div>

              <div>
                <span className="dot total-dot"></span>
                <span>Total budget</span>
                <strong>{currency} {weeklyBudget.toFixed(2)}</strong>
              </div>

            </div>

          </div>

        </div>

        <div className="panel policy-panel">

          <div className="panel-heading">

            <div>
              <h3>Active spending rules</h3>
              <p>Your purchase protection</p>
            </div>

            <ShieldCheck className="policy-heading-icon" size={22} />

          </div>

          <div className="rule-item">
            <span>Maximum per item</span>
            <strong>{policy?.currency || "USD"} {Number(policy?.maxItemPrice || 0).toFixed(2)}</strong>
          </div>

          <div className="rule-item">
            <span>Maximum per order</span>
            <strong>{policy?.currency || "USD"} {Number(policy?.maxOrderAmount || 0).toFixed(2)}</strong>
          </div>

          <div className="rule-item">
            <span>Weekly spending limit</span>
            <strong>{policy?.currency || "USD"} {Number(policy?.weeklySpendingLimit || 0).toFixed(2)}</strong>
          </div>

          <div className="rule-item">
            <span>Maximum orders/week</span>
            <strong>{policy?.maxOrdersPerWeek || 0}</strong>
          </div>

          <div className="protection-status">
            <ShieldCheck size={17} />
            <span>Spending protection enabled</span>
            <button className="text-button" type="button" onClick={() => navigate("/policies")}>
              Manage policy
              <ArrowUpRight size={14} />
            </button>
          </div>

        </div>

      </div>

      <div className="panel transaction-panel">

        <div className="panel-heading">

          <div>
            <h3>Recent transactions</h3>
            <p>Your latest shopping activity</p>
          </div>

          <button className="text-button" type="button" onClick={() => navigate("/transactions")}>
            View all
            <ArrowUpRight size={15} />
          </button>

        </div>

        <div className="table-wrapper">

          <table>

            <thead>
              <tr>
                <th>PRODUCT</th>
                <th>DATE</th>
                <th>AMOUNT</th>
                <th>STATUS</th>
              </tr>
            </thead>

            <tbody>

              {transactions.map((item, index) => (

                <tr key={item.checkoutSessionId || `${item.productName}-${item.createdAt}-${index}`}>

                  <td>{item.productName}</td>

                  <td>{new Date(item.createdAt).toLocaleDateString()}</td>

                  <td className="amount">
                    {item.currency} {item.amount}
                  </td>

                  <td>
                    <span
                      className={
                        item.status === "CAPTURED"
                          ? "status completed"
                          : "status rejected"
                      }
                    >
                      {item.status}
                    </span>
                  </td>

                </tr>

              ))}

            </tbody>

          </table>

        </div>

        {!loading && !transactions.length && <p className="demo-note">No transactions yet.</p>}
        {loading && <p className="demo-note">Loading your latest activity...</p>}

      </div>

    </div>

  );

}
