import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, Money, PageHeader, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDate } from '../../utils.js';

export default function SupplierDetails() {
  const { id } = useParams();
  const { data, loading, error } = useFetch(() => api.get(`/suppliers/${id}`), [id]);
  if (loading) return <Loading />;
  if (error) return <Alert>{errorText(error)}</Alert>;
  const { supplier: s, quotations, orders } = data;

  return (
    <>
      <PageHeader title={s.name} subtitle={<StatusBadge status={s.active ? 'ACTIVE' : 'INACTIVE'} />} actions={<Link className="btn" to="/suppliers">Back to suppliers</Link>} />
      <div className="grid grid-2 mb">
        <Card title="Contact">
          <dl className="kv">
            <dt>Contact person</dt><dd>{s.contactName}</dd>
            <dt>Email</dt><dd>{s.email}</dd>
            <dt>Phone</dt><dd>{s.phone || '—'}</dd>
            <dt>Address</dt><dd>{s.address || '—'}</dd>
            <dt>Tax ID</dt><dd>{s.taxId || '—'}</dd>
          </dl>
        </Card>
        <Card title="Categories supplied">{s.categories.map((c) => <span key={c.id} className="tag">{c.name}</span>)}</Card>
      </div>
      <Card title="Quotation history">
        {quotations.length === 0 ? <EmptyState>No quotations yet.</EmptyState> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Request</th><th className="num">Total</th><th>Delivery</th><th>Status</th><th>Date</th></tr></thead>
            <tbody>{quotations.map((q) => (
              <tr key={q.id}>
                <td><Link to={`/quotations/${q.id}`}>{q.requestNumber}</Link></td>
                <td className="num"><Money value={q.totalAmount} /></td>
                <td>{q.deliveryDays} days</td>
                <td><StatusBadge status={q.status} /></td>
                <td>{formatDate(q.createdAt)}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </Card>
      <Card title="Purchase orders">
        {orders.length === 0 ? <EmptyState>No purchase orders yet.</EmptyState> : (
          <div className="table-wrap"><table>
            <thead><tr><th>PO number</th><th className="num">Total</th><th>Status</th><th>Issued</th></tr></thead>
            <tbody>{orders.map((o) => (
              <tr key={o.id}>
                <td><Link to={`/orders/${o.id}`}>{o.poNumber}</Link></td>
                <td className="num"><Money value={o.totalAmount} /></td>
                <td><StatusBadge status={o.status} /></td>
                <td>{formatDate(o.issuedAt)}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </Card>
    </>
  );
}
