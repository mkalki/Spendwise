import {
  Wallet,
  ShoppingBag,
  ShieldCheck,
  TrendingUp,
  ArrowUpRight,
  ArrowDownRight,
} from "lucide-react";

const stats = [
  {
    title: "Weekly Budget",
    value: "$150.00",
    note: "Your spending allowance",
    icon: Wallet,
  },
  {
    title: "Total Spent",
    value: "$45.00",
    note: "This week's spending",
    icon: ShoppingBag,
  },
  {
    title: "Remaining Budget",
    value: "$105.00",
    note: "70% of your budget left",
    icon: TrendingUp,
  },
  {
    title: "Protected Purchases",
    value: "2",
    note: "Demo blocked attempts",
    icon: ShieldCheck,
  },
];

const transactions = [
  {
    name: "Wireless Headphones",
    date: "Nov 06, 2026",
    amount: "$45.00",
    status: "Completed",
  },
  {
    name: "Premium Gaming Mouse",
    date: "Nov 05, 2026",
    amount: "$75.00",
    status: "Rejected",
  },
  {
    name: "Mechanical Keyboard",
    date: "Nov 04, 2026",
    amount: "$55.00",
    status: "Completed",
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

  return (

    <div className="dashboard">

      <div className="welcome-section">

        <div>
          <h2>Welcome back!</h2>

          <p>
            Here's an overview of your shopping
            and spending activity.
          </p>
        </div>

        <div className="demo-label">
          Demo preview
        </div>

      </div>

      <div className="stats-grid">

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

            <div className="budget-ring">

              <div className="budget-ring-inner">

                <span>70%</span>
                <small>Remaining</small>

              </div>

            </div>

            <div className="budget-details">

              <div>
                <span className="dot spent-dot"></span>
                <span>Spent</span>
                <strong>$45.00</strong>
              </div>

              <div>
                <span className="dot remaining-dot"></span>
                <span>Remaining</span>
                <strong>$105.00</strong>
              </div>

              <div>
                <span className="dot total-dot"></span>
                <span>Total budget</span>
                <strong>$150.00</strong>
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

            <ShieldCheck color="#16a34a" size={22} />

          </div>

          <div className="rule-item">
            <span>Maximum per item</span>
            <strong>$60.00</strong>
          </div>

          <div className="rule-item">
            <span>Maximum per order</span>
            <strong>$100.00</strong>
          </div>

          <div className="rule-item">
            <span>Weekly spending limit</span>
            <strong>$150.00</strong>
          </div>

          <div className="rule-item">
            <span>Maximum orders/week</span>
            <strong>1</strong>
          </div>

          <div className="protection-status">
            <ShieldCheck size={17} />
            Spending protection enabled
          </div>

        </div>

      </div>

      <div className="panel transaction-panel">

        <div className="panel-heading">

          <div>
            <h3>Recent transactions</h3>
            <p>Your latest shopping activity</p>
          </div>

          <button className="text-button">
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

              {transactions.map((item) => (

                <tr key={item.name}>

                  <td>{item.name}</td>

                  <td>{item.date}</td>

                  <td className="amount">
                    {item.amount}
                  </td>

                  <td>
                    <span
                      className={
                        item.status === "Completed"
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

        <p className="demo-note">
          Illustrative data only. Live data will come from
          the Spring Boot backend in a later step.
        </p>

      </div>

    </div>

  );

}