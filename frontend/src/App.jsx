import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import { Loading } from './components/ui.jsx';

import Login from './pages/common/Login.jsx';
import ForgotPassword from './pages/common/ForgotPassword.jsx';
import ResetPassword from './pages/common/ResetPassword.jsx';
import Profile from './pages/common/Profile.jsx';
import Notifications from './pages/common/Notifications.jsx';
import { AccessDenied, NotFound } from './pages/common/Errors.jsx';
import Dashboard from './pages/Dashboard.jsx';

import RequestForm from './pages/requests/RequestForm.jsx';
import RequestList from './pages/requests/RequestList.jsx';
import RequestDetails from './pages/requests/RequestDetails.jsx';

import ApprovalQueue from './pages/manager/ApprovalQueue.jsx';
import ApprovalHistory from './pages/manager/ApprovalHistory.jsx';

import ApprovedRequests from './pages/procurement/ApprovedRequests.jsx';
import Suppliers from './pages/procurement/Suppliers.jsx';
import SupplierDetails from './pages/procurement/SupplierDetails.jsx';
import Quotations from './pages/procurement/Quotations.jsx';
import QuotationForm from './pages/procurement/QuotationForm.jsx';
import QuotationComparison from './pages/procurement/QuotationComparison.jsx';
import CreatePurchaseOrder from './pages/procurement/CreatePurchaseOrder.jsx';
import { DeliveryDetail, DeliveryList } from './pages/procurement/Deliveries.jsx';

import OrderList from './pages/orders/OrderList.jsx';
import OrderDetails from './pages/orders/OrderDetails.jsx';

import Users from './pages/admin/Users.jsx';
import Categories from './pages/admin/Categories.jsx';
import AuditLog from './pages/admin/AuditLog.jsx';
import Settings from './pages/admin/Settings.jsx';
import Reports from './pages/Reports.jsx';

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading label="Signing you in…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Layout />;
}

function Role({ roles, children }) {
  const { user } = useAuth();
  return roles.includes(user.role) ? children : <Navigate to="/forbidden" replace />;
}

function RequestsHome() {
  const { user } = useAuth();
  if (user.role === 'EMPLOYEE') return <RequestList mode="mine" />;
  if (user.role === 'ADMIN') return <RequestList mode="all" />;
  return <Navigate to="/forbidden" replace />;
}

export default function App() {
  const { user, loading } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={loading ? <Loading /> : user ? <Navigate to="/dashboard" replace /> : <Login />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      <Route element={<RequireAuth />}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/notifications" element={<Notifications />} />

        {/* Purchase requests */}
        <Route path="/requests" element={<RequestsHome />} />
        <Route
          path="/requests/new"
          element={
            <Role roles={['EMPLOYEE']}>
              <RequestForm />
            </Role>
          }
        />
        <Route
          path="/requests/:id/edit"
          element={
            <Role roles={['EMPLOYEE']}>
              <RequestForm />
            </Role>
          }
        />
        <Route path="/requests/:id" element={<RequestDetails />} />
        <Route
          path="/requests/:id/compare"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <QuotationComparison />
            </Role>
          }
        />

        {/* Orders (employee view) and purchase order register (procurement / admin) */}
        <Route
          path="/orders"
          element={
            <Role roles={['EMPLOYEE']}>
              <OrderList variant="mine" />
            </Role>
          }
        />
        <Route path="/orders/:id" element={<OrderDetails />} />
        <Route
          path="/purchase-orders"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <OrderList variant="register" />
            </Role>
          }
        />
        <Route
          path="/purchase-orders/new"
          element={
            <Role roles={['PROCUREMENT_OFFICER']}>
              <CreatePurchaseOrder />
            </Role>
          }
        />

        {/* Manager */}
        <Route
          path="/approvals"
          element={
            <Role roles={['MANAGER', 'ADMIN']}>
              <ApprovalQueue />
            </Role>
          }
        />
        <Route
          path="/approvals/history"
          element={
            <Role roles={['MANAGER', 'ADMIN']}>
              <ApprovalHistory />
            </Role>
          }
        />
        <Route
          path="/approvals/:id"
          element={
            <Role roles={['MANAGER', 'ADMIN']}>
              <RequestDetails review />
            </Role>
          }
        />

        {/* Procurement */}
        <Route
          path="/procurement/requests"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <ApprovedRequests />
            </Role>
          }
        />
        <Route
          path="/suppliers"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <Suppliers />
            </Role>
          }
        />
        <Route
          path="/suppliers/:id"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <SupplierDetails />
            </Role>
          }
        />
        <Route
          path="/quotations"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <Quotations />
            </Role>
          }
        />
        <Route
          path="/quotations/new"
          element={
            <Role roles={['PROCUREMENT_OFFICER']}>
              <QuotationForm />
            </Role>
          }
        />
        <Route
          path="/quotations/:id"
          element={
            <Role roles={['PROCUREMENT_OFFICER', 'ADMIN']}>
              <QuotationForm />
            </Role>
          }
        />
        <Route
          path="/deliveries"
          element={
            <Role roles={['PROCUREMENT_OFFICER']}>
              <DeliveryList />
            </Role>
          }
        />
        <Route
          path="/deliveries/:id"
          element={
            <Role roles={['PROCUREMENT_OFFICER']}>
              <DeliveryDetail />
            </Role>
          }
        />

        {/* Reports (manager, procurement, admin) */}
        <Route
          path="/reports"
          element={
            <Role roles={['MANAGER', 'PROCUREMENT_OFFICER', 'ADMIN']}>
              <Reports />
            </Role>
          }
        />

        {/* Administrator */}
        <Route
          path="/admin/users"
          element={
            <Role roles={['ADMIN']}>
              <Users />
            </Role>
          }
        />
        <Route
          path="/admin/categories"
          element={
            <Role roles={['ADMIN']}>
              <Categories />
            </Role>
          }
        />
        <Route
          path="/admin/audit"
          element={
            <Role roles={['ADMIN']}>
              <AuditLog />
            </Role>
          }
        />
        <Route
          path="/admin/settings"
          element={
            <Role roles={['ADMIN']}>
              <Settings />
            </Role>
          }
        />

        <Route path="/forbidden" element={<AccessDenied />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
