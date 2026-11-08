import { Bell, Search, LogOut, UserRound } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { useState } from "react";

const pageTitles = {
  "/": "Dashboard",
  "/assistant": "Shopping Assistant",
  "/products": "Products",
  "/policies": "Spending Policies",
  "/transactions": "Transactions",
};

export default function Topbar({ user, onLogout }) {

  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [showNotifications, setShowNotifications] = useState(false);
  const [showProfile, setShowProfile] = useState(false);

  const title =
    pageTitles[location.pathname] || "Dashboard";

  return (

    <header className="topbar">

      <div className="topbar-heading">
        <p className="breadcrumb">Workspace / Overview</p>
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
              <p>Your spending protection is active.</p>
              <p>Review your latest transactions.</p>
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
            {(user?.displayName || user?.username || user?.email || "U").slice(0, 1).toUpperCase()}
          </button>
          {showProfile && (
            <div className="topbar-menu profile-menu" role="menu">
              <strong>{user?.displayName || user?.username || "Spendwise user"}</strong>
              <span>{user?.email || ""}</span>
              <button type="button" onClick={() => navigate("/policies")}>
                <UserRound size={14} /> Spending profile
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