import { Bell, Search } from "lucide-react";
import { useLocation } from "react-router-dom";

const pageTitles = {
  "/": "Dashboard",
  "/assistant": "Shopping Assistant",
  "/products": "Products",
  "/policies": "Spending Policies",
  "/transactions": "Transactions",
};

export default function Topbar() {

  const location = useLocation();

  const title =
    pageTitles[location.pathname] || "Dashboard";

  return (

    <header className="topbar">

      <div>
        <p className="breadcrumb">Workspace / Overview</p>
        <h1>{title}</h1>
      </div>

      <div className="topbar-right">

        <div className="search-box">
          <Search size={17} />
          <span>Search</span>
          <kbd>⌘ K</kbd>
        </div>

        <div className="notification">
          <Bell size={19} />
          <span></span>
        </div>

        <div className="avatar">
          U
        </div>

      </div>

    </header>

  );
}