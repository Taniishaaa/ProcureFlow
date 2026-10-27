import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useFetch } from '../hooks.js';
import { Alert, BarList, Card, Loading, Money, PageHeader, StatCard, errorText } from '../components/ui.jsx';
import { OrderTable, RequestTable } from '../components/tables.jsx';
import { formatDateTime, statusLabel } from '../utils.js';

function statusItems(map) {
  return Object.entries(map || {}).map(([label, value]) => ({ label: statusLabel(label), value }));
}

function Employee({ d }) {
  return (
    <>
      <div className="grid grid-4 mb">
        <StatCard label="My requests" value={d.totalRequests} />
        <StatCard label="Awaiting approval" value={d.requestsByStatus?.SUBMITTED ?? 0} tone="amber" />
        <StatCard label="Approved / ordered" value={(d.requestsByStatus?.APPROVED ?? 0) + (d.requestsByStatus?.ORDERED ?? 0)} tone="green" />
        <StatCard label="Orders in progress" value={d.activeOrders} tone="purple" />
      </div>
      <div className="grid grid-2">
        <Card title="Recent requests" actions={<Link to="/requests/new" className="btn btn-primary btn-sm">New request</Link>}>
          <RequestTable rows={d.recentRequests} showRequester={false} />
        </Card>
        <Card title="Recent orders">
          <OrderTable rows={d.recentOrders} showRequester={false} />
        </Card>
      </div>
    </>
  );
}

function Manager({ d }) {
  return (
    <>
      <div className="grid grid-4 mb">
        <StatCard label="Pending approvals" value={d.pendingCount} tone="amber" />
        <StatCard label="Pending value" value={<Money value={d.pendingValue} />} tone="amber" />
        <StatCard label="Approved by me" value={d.approvedByMe} tone="green" />
        <StatCard label="Rejected by me" value={d.rejectedByMe} tone="red" />
      </div>
      <Card title="Awaiting your decision" actions={<Link to="/approvals" className="btn btn-sm">View all</Link>}>
        <RequestTable rows={d.pendingRequests} linkBase="/approvals" />
      </Card>
      <Card title="My recent decisions" actions={<Link to="/approvals/history" className="btn btn-sm">History</Link>}>
        {d.recentDecisions?.length ? (
          <ul className="timeline">
            {d.recentDecisions.map((x) => (
              <li key={x.id}>
                <strong>{statusLabel(x.decision)}</strong> {x.requestNumber} · {x.requestTitle}
                <div className="muted">{formatDateTime(x.decidedAt)}{x.comments ? ` — ${x.comments}` : ''}</div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">No decisions yet.</p>
        )}
      </Card>
    </>
  );
}

function Procurement({ d }) {
  return (
    <>
      <div className="grid grid-4 mb">
        <StatCard label="Approved, awaiting sourcing" value={d.awaitingProcurement} tone="amber" />
        <StatCard label="Active orders" value={d.activeOrders} />
        <StatCard label="Awaiting delivery" value={d.pendingDeliveries} tone="purple" />
        <StatCard label="Open order value" value={<Money value={d.openOrderValue} />} tone="green" />
      </div>
      <div className="grid grid-2">
        <Card title="Approved requests" actions={<Link to="/procurement/requests" className="btn btn-sm">View all</Link>}>
          <RequestTable rows={d.approvedRequests} />
        </Card>
        <Card title="Recent purchase orders" actions={<Link to="/purchase-orders" className="btn btn-sm">View all</Link>}>
          <OrderTable rows={d.recentOrders} />
        </Card>
      </div>
    </>
  );
}

function Admin({ d }) {
  const p = d.pendingActions || {};
  return (
    <>
      <div className="grid grid-4 mb">
        <StatCard label="Users" value={d.totalUsers} hint={`${d.activeUsers} active`} />
        <StatCard label="Requests" value={d.totalRequests} tone="purple" />
        <StatCard label="Purchase orders" value={d.totalOrders} tone="green" />
        <StatCard label="Awaiting approval" value={p.requestsAwaitingApproval} tone="amber" />
      </div>
      <div className="grid grid-3 mb">
        <Card title="Requests by status"><BarList items={statusItems(d.requestsByStatus)} /></Card>
        <Card title="Orders by status"><BarList items={statusItems(d.ordersByStatus)} /></Card>
        <Card title="Pending work">
          <dl className="kv">
            <dt>Awaiting approval</dt><dd>{p.requestsAwaitingApproval}</dd>
            <dt>Approved, not ordered</dt><dd>{p.approvedAwaitingProcurement}</dd>
            <dt>Orders awaiting delivery</dt><dd>{p.ordersAwaitingDelivery}</dd>
          </dl>
        </Card>
      </div>
      <Card title="Recent activity" actions={<Link to="/admin/audit" className="btn btn-sm">Audit log</Link>}>
        {d.recentActivity?.length ? (
          <ul className="timeline">
            {d.recentActivity.map((a) => (
              <li key={a.id}>
                <strong>{a.action}</strong> {a.entityType} #{a.entityId} — {a.details}
                <div className="muted">{a.actorEmail} · {formatDateTime(a.createdAt)}</div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">No activity yet.</p>
        )}
      </Card>
    </>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const { data, loading, error } = useFetch(() => api.get('/dashboard'), []);

  return (
    <>
      <PageHeader title={`Welcome, ${user.fullName.split(' ')[0]}`} subtitle="Here is what is happening in procurement." />
      <Alert>{errorText(error)}</Alert>
      {loading && <Loading />}
      {data && user.role === 'EMPLOYEE' && <Employee d={data} />}
      {data && user.role === 'MANAGER' && <Manager d={data} />}
      {data && user.role === 'PROCUREMENT_OFFICER' && <Procurement d={data} />}
      {data && user.role === 'ADMIN' && <Admin d={data} />}
    </>
  );
}
