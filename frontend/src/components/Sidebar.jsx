import { NavLink } from "react-router-dom";
import BrandMark from "./BrandMark";

import {
  LayoutDashboard,
  SlidersHorizontal,
  MessageSquare,
  ShoppingBag,
  ShoppingCart,
  Receipt,
  Sparkles,
  UserRound,
  LogOut,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api";

const menuItems = [
  {
    name: "Dashboard",
    path: "/",
    icon: LayoutDashboard,
  },
  {
    name: "Shopping Assistant",
    path: "/assistant",
    icon: MessageSquare,
  },
  {
    name: "Products",
    path: "/products",
    icon: ShoppingBag,
  },
  {
    name: "Spending Policies",
    path: "/policies",
    icon: SlidersHorizontal,
  },
  {
    name: "Cart",
    path: "/cart",
    icon: ShoppingCart,
  },
  {
    name: "Transactions",
    path: "/transactions",
    icon: Receipt,
  },
  { name: "Spendwise Pro", path: "/pro", icon: Sparkles },
];

export default function Sidebar({ user, onLogout }) {
  const [membership, setMembership] = useState(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef(null);
  useEffect(() => {
    const refreshMembership = () => api.getProMembership().then(setMembership).catch(() => setMembership({ pro: false }));
    refreshMembership();
    window.addEventListener("spendwise:membership-updated", refreshMembership);
    return () => window.removeEventListener("spendwise:membership-updated", refreshMembership);
  }, [user?.id]);
  useEffect(() => {
    function closeOutside(event) {
      if (!profileRef.current?.contains(event.target)) setProfileOpen(false);
    }
    function closeOnEscape(event) { if (event.key === "Escape") setProfileOpen(false); }
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);
  const tier = membership?.pro ? "Pro" : "Free";
  return (
    <aside className="sidebar" aria-label="Primary navigation">

      <Link className="brand" to="/" aria-label="Spendwise home">

        <div className="brand-icon">
          <BrandMark />
        </div>

        <div>
          <h2>Spendwise</h2>
          <span>Protected buying studio</span>
        </div>

      </Link>

      <p className="menu-label">WORKSPACE</p>

      <nav className="sidebar-nav">

        {menuItems.map((item) => {

          const Icon = item.icon;

          return (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === "/"}
              title={item.name}
              aria-label={item.name}
              className={({ isActive }) =>
                isActive
                  ? "nav-link active"
                  : "nav-link"
              }
            >

              <Icon size={19} />

              <span>{item.name}</span>

            </NavLink>
          );

        })}

      </nav>

      <div className="sidebar-profile" ref={profileRef}>
        {profileOpen && <div className="sidebar-profile-menu" role="menu">
          <strong>{user?.displayName || user?.name || user?.username || "Spendwise user"}</strong>
          <span>{user?.email || ""}</span>
          <Link to="/profile" onClick={() => setProfileOpen(false)}><UserRound size={14} /> Edit profile</Link>
          <Link to="/pro" onClick={() => setProfileOpen(false)}><Sparkles size={14} /> Manage Pro</Link>
          <button type="button" onClick={onLogout}><LogOut size={14} /> Sign out</button>
        </div>}
        <button className="sidebar-profile-button" type="button" title={tier}
          aria-label={`Profile, ${tier}`} aria-expanded={profileOpen} onClick={() => setProfileOpen((open) => !open)}>
          <span className="sidebar-profile-avatar">{(user?.displayName || user?.name || user?.username || user?.email || "U").slice(0, 1).toUpperCase()}</span>
          <span className="sidebar-profile-copy"><strong>{user?.displayName || user?.name || user?.username || "Profile"}</strong><small>{tier}</small></span>
          <span className="sidebar-tier-tooltip" role="tooltip">{tier}</span>
        </button>
      </div>

    </aside>
  );
}
