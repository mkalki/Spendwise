import { Bell, Search } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useEffect, useRef, useState } from "react";

const pageTitles = {
  "/": "Dashboard",
  "/assistant": "Shopping Assistant",
  "/products": "Products",
  "/cart": "Cart",
  "/checkout/return": "Checkout",
  "/checkout/cancel": "Checkout",
  "/policies": "Spending Policies",
  "/transactions": "Transactions",
};

export default function Topbar() {

  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [showNotifications, setShowNotifications] = useState(false);
  const searchInput = useRef(null);
  const header = useRef(null);

  useEffect(() => {
    function keydown(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (searchInput.current?.offsetParent) searchInput.current.focus();
        else navigate("/assistant");
      }
      if (event.key === "Escape") setShowNotifications(false);
    }
    function outside(event) {
      if (!header.current?.contains(event.target)) setShowNotifications(false);
    }
    document.addEventListener("keydown", keydown);
    document.addEventListener("pointerdown", outside);
    return () => { document.removeEventListener("keydown", keydown); document.removeEventListener("pointerdown", outside); };
  }, [navigate]);

  const title =
    pageTitles[location.pathname] || "Dashboard";

  return (

    <header className="topbar" ref={header}>

      <div className="topbar-heading">
        <p className="breadcrumb">Spendwise / Private workspace</p>
        <h1>{title}</h1>
      </div>

      <div className="topbar-right">

        <form
          className="search-box"
          onSubmit={(event) => {
            event.preventDefault();
            if (query.trim()) {
              navigate(`/assistant?query=${encodeURIComponent(query.trim())}`);
              setQuery("");
            }
          }}
        >
          <Search size={17} />
          <input
            ref={searchInput}
            aria-label="Search Spendwise"
            placeholder="Search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <kbd>Ctrl K</kbd>
        </form>

        <div className="topbar-control">
          <button
            className="notification"
            type="button"
            aria-label="Notifications"
            aria-expanded={showNotifications}
            onClick={() => setShowNotifications((visible) => !visible)}
          >
            <Bell size={19} />
            <span className="notification-dot"></span>
          </button>
          {showNotifications && (
            <div className="topbar-menu notification-menu" role="status">
              <strong>Notifications</strong>
              <p>Your workspace, at a glance.</p>
              <button type="button" className="studio-text-link" onClick={() => { navigate("/transactions"); setShowNotifications(false); }}>Review shopping activity</button>
              <button type="button" className="studio-text-link" onClick={() => { navigate("/policies"); setShowNotifications(false); }}>Review spending rules</button>
            </div>
          )}
        </div>

      </div>

    </header>

  );
}
