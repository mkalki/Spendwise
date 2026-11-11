import { useLocation } from "react-router-dom";
import { Link } from "react-router-dom";

const pageTitles = {
  "/": "Dashboard",
  "/assistant": "Shopping Assistant",
  "/products": "Products",
  "/cart": "Cart",
  "/checkout/return": "Checkout",
  "/checkout/cancel": "Checkout",
  "/policies": "Spending Policies",
  "/transactions": "Transactions",
  "/pro": "Spendwise Pro",
};

export default function Topbar() {
  const location = useLocation();
  const title = pageTitles[location.pathname] || "Dashboard";

  return (
    <header className="topbar">
      <div className="topbar-heading">
        <p className="breadcrumb"><Link to="/" aria-label="Spendwise home">Spendwise</Link> / Private workspace</p>
        <h1>{title}</h1>
      </div>
    </header>
  );
}
