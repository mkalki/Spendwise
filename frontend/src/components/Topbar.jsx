import { Bell, Search, LogOut, UserRound, RotateCcw } from "lucide-react";
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

export default function Topbar({ user, onLogout }) {

  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [showNotifications, setShowNotifications] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const searchInput = useRef(null);
  const header = useRef(null);

  useEffect(() => {
    function keydown(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (searchInput.current?.offsetParent) searchInput.current.focus();
        else navigate("/assistant");
      }
      if (event.key === "Escape") { setShowNotifications(false); setShowProfile(false); }
    }
    function outside(event) {
      if (!header.current?.contains(event.target)) { setShowNotifications(false); setShowProfile(false); }
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
            onClick={() => {
              setShowNotifications((visible) => !visible);
              setShowProfile(false);
            }}
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

        <div className="topbar-control">
          <button
          className="avatar"
          type="button"
          aria-label="Open profile menu"
          aria-expanded={showProfile}
          onClick={() => {
            setShowProfile((visible) => !visible);
            setShowNotifications(false);
          }}
          >
            {(user?.displayName || user?.name || user?.username || user?.email || "U").slice(0, 1).toUpperCase()}
          </button>
          {showProfile && (
            <div className="topbar-menu profile-menu" role="menu">
              <strong>{user?.displayName || user?.name || user?.username || "Spendwise user"}</strong>
              <span>{user?.email || ""}</span>
              <button type="button" onClick={() => navigate("/policies")}>
                <UserRound size={14} /> Spending profile
              </button>
              <button type="button" onClick={() => { window.dispatchEvent(new Event("spendwise:replay-intro")); setShowProfile(false); }}>
                <RotateCcw size={14} /> Replay intro
              </button>
              <button type="button" onClick={onLogout}>
                <LogOut size={14} /> Sign out
              </button>
            </div>
          )}
        </div>

      </div>

    </header>

  );
}
