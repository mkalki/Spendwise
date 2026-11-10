import { NavLink } from "react-router-dom";
import BrandMark from "./BrandMark";

import {
  LayoutDashboard,
  SlidersHorizontal,
  MessageSquare,
  ShoppingBag,
  Receipt,
  ShieldCheck,
} from "lucide-react";

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
    name: "Transactions",
    path: "/transactions",
    icon: Receipt,
  },
];

export default function Sidebar() {
  return (
    <aside className="sidebar" aria-label="Primary navigation">

      <div className="brand">

        <div className="brand-icon">
          <BrandMark />
        </div>

        <div>
          <h2>Spendwise</h2>
          <span>Protected buying studio</span>
        </div>

      </div>

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
              className={({ isActive }) =>
                isActive
                  ? "nav-link active"
                  : "nav-link"
              }
            >

              <Icon size={19} />

              <span>{item.name}</span>
              <span className="nav-active-mark" aria-hidden="true" />

            </NavLink>
          );

        })}

      </nav>

      <div className="sidebar-bottom" role="status">

        <div className="security-icon">
          <ShieldCheck size={19} />
        </div>

        <div>
          <h4>Your spending is protected</h4>
          <p>Spending limits enforced</p>
        </div>

      </div>

    </aside>
  );
}
