import { useEffect, useState } from "react";
import { ArrowUpRight, CircleDollarSign, Clock3, ReceiptText, WalletCards } from "lucide-react";

import { api } from "../services/api";

export default function Transactions() {
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    Promise.all([api.getTransactionSummary(), api.getTransactions()])
      .then(([summaryData, historyData]) => {
        setSummary(summaryData);
        setTransactions(historyData.transactions || []);
      })
      .catch((error) => setErrorMessage(error.message || "Unable to load transactions."))
      .finally(() => setLoading(false));
  }, []);

  const currency = summary?.currency || transactions[0]?.currency || "USD";
  const formatAmount = (value) => `${currency} ${Number(value || 0).toFixed(2)}`;
  const formatDate = (value) => {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? "—"
      : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date);
  };

  return (
    <div className="transactions-page">
      <header className="transactions-heading">
        <div>
          <span className="eyebrow">MONEY MOVEMENT</span>
          <h2>Transactions</h2>
          <p>Review every protected purchase and keep a clear view of your weekly activity.</p>
        </div>
        <div className="transaction-heading-mark"><ReceiptText size={22} /></div>
      </header>

      {errorMessage && <div className="policy-message error-message">{errorMessage}</div>}

      <div className="transaction-summary transaction-summary-cards" aria-busy={loading}>
        <SummaryCard icon={CircleDollarSign} label="Weekly spending" value={loading ? "—" : formatAmount(summary?.completedSpend)} note="Completed spend this week" />
        <SummaryCard icon={WalletCards} label="Remaining budget" value={loading ? "—" : formatAmount(summary?.remainingBudget)} note="Available under your policy" />
        <SummaryCard icon={ReceiptText} label="Completed orders" value={loading ? "—" : String(summary?.completedOrders || 0)} note="Protected purchases completed" />
      </div>

      <section className="panel transaction-history">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">ACTIVITY LOG</span>
            <h3>Transaction history</h3>
            <p>Your latest purchase activity and payment states.</p>
          </div>
          <Clock3 size={20} />
        </div>

        {loading ? (
          <div className="transaction-skeletons" aria-label="Loading transactions">
            {[1, 2, 3].map((item) => <div className="transaction-skeleton" key={item} />)}
          </div>
        ) : transactions.length ? (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr><th>PRODUCT / MERCHANT</th><th>AMOUNT</th><th>STATUS</th><th>DATE</th><th>REFERENCE</th></tr>
              </thead>
              <tbody>
                {transactions.map((transaction, index) => (
                  <tr key={transaction.checkoutSessionId || `${transaction.productName}-${transaction.createdAt}-${index}`}>
                    <td>
                      <strong className="transaction-product">{transaction.productName || "Product unavailable"}</strong>
                      <span className="transaction-merchant">{transaction.merchantName || "Merchant unavailable"}</span>
                    </td>
                    <td className="transaction-amount">{transaction.currency || currency} {Number(transaction.amount || 0).toFixed(2)}</td>
                    <td><span className={`status ${transaction.status === "CAPTURED" ? "completed" : "rejected"}`}>{transaction.status || "UNKNOWN"}</span></td>
                    <td>{formatDate(transaction.createdAt)}</td>
                    <td className="transaction-reference">{transaction.checkoutSessionId || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="transaction-empty">
            <ReceiptText size={24} />
            <strong>No transactions yet</strong>
            <p>Completed purchases will appear here once you use protected checkout.</p>
          </div>
        )}
      </section>

      <div className="transaction-footnote">
        <span>Transactions are recorded after payment confirmation.</span>
        <ArrowUpRight size={14} />
      </div>
    </div>
  );
}

function SummaryCard({ icon: Icon, label, value, note }) {
  return (
    <article className="transaction-summary-card">
      <div className="transaction-summary-icon"><Icon size={19} /></div>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{note}</small>
    </article>
  );
}