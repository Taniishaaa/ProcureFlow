import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { ROLE_LABELS } from '../utils.js';

const NAV = {
  EMPLOYEE: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/requests', label: 'My Requests', end: true },
    { to: '/requests/new', label: 'New Request' },
    { to: '/orders', label: 'My Orders' },
  ],
  MANAGER: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/approvals', label: 'Approval Queue', end: true },
    { to: '/approvals/history', label: 'Approval History' },
    { to: '/reports', label: 'Reports' },
  ],
  PROCUREMENT_OFFICER: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/procurement/requests', label: 'Approved Requests' },
    { to: '/suppliers', label: 'Suppliers' },
    { to: '/quotations', label: 'Quotations' },
    { to: '/purchase-orders', label: 'Purchase Orders', end: true },
    { to: '/deliveries', label: 'Deliveries' },
    { to: '/reports', label: 'Reports' },
  ],
  ADMIN: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/admin/users', label: 'Users' },
    { to: '/admin/categories', label: 'Categories' },
    { to: '/requests', label: 'All Requests', end: true },
    { to: '/purchase-orders', label: 'Purchase Orders', end: true },
    { to: '/suppliers', label: 'Suppliers' },
    { to: '/admin/audit', label: 'Audit Log' },
    { to: '/reports', label: 'Reports' },
    { to: '/admin/settings', label: 'Settings' },
  ],
};

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [company, setCompany] = useState('ProcureFlow');
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let active = true;
    const load = () =>
      api
        .get('/notifications/unread-count')
        .then((r) => active && setUnread(r.count))
        .catch(() => {});
    load();
    const timer = setInterval(load, 30000);
    window.addEventListener('pf:notifications-changed', load);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener('pf:notifications-changed', load);
    };
  }, []);

  useEffect(() => {
    api
      .get('/settings')
      .then((settings) => {
        const name = settings.find((s) => s.key === 'company.name');
        if (name) setCompany(name.value);
      })
      .catch(() => {});
  }, []);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  const items = NAV[user.role] || [];

  return (
    <div className="shell">
      <aside className={`sidebar ${menuOpen ? 'open' : ''}`}>
        <Link to="/dashboard" className="brand">
          <span className="brand-mark">P</span>
          <span>
            <strong>ProcureFlow</strong>
            <small>{company}</small>
          </span>
        </Link>
        <nav>
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'active' : '')}>
              {item.label}
            </NavLink>
          ))}
          <div className="nav-divider" />
          <NavLink to="/notifications" className={({ isActive }) => (isActive ? 'active' : '')}>
            Notifications
            {unread > 0 && <span className="count-pill">{unread}</span>}
          </NavLink>
          <NavLink to="/profile" className={({ isActive }) => (isActive ? 'active' : '')}>
            My Profile
          </NavLink>
        </nav>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-toggle" onClick={() => setMenuOpen((o) => !o)} aria-label="Toggle menu">
            ☰
          </button>
          <div className="topbar-spacer" />
          <Link to="/notifications" className="bell" aria-label={`Notifications (${unread} unread)`}>
            🔔
            {unread > 0 && <span className="bell-count">{unread > 99 ? '99+' : unread}</span>}
          </Link>
          <div className="user-chip">
            <div className="avatar">{user.fullName.charAt(0).toUpperCase()}</div>
            <div className="user-meta">
              <strong>{user.fullName}</strong>
              <small>{ROLE_LABELS[user.role]}</small>
            </div>
          </div>
          <button
            className="btn btn-sm"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Sign out
          </button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
