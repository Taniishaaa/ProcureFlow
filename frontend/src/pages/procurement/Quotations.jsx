import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, Money, PageHeader, Pagination, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDate } from '../../utils.js';

export default function Quotations() {
  const { user } = useAuth();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const { data, loading, error } = useFetch(() => api.get('/quotations', { status, page, size: 10 }), [status, page]);

  return (
    <>
      <PageHeader
        title="Quotations"
        actions={user.role === 'PROCUREMENT_OFFICER' && <Link className="btn btn-primary" to="/quotations/new">New quotation</Link>}
      />
      <Card>
        <div className="filters">
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            <option value="SUBMITTED">Submitted</option>
            <option value="SELECTED">Selected</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.content.length === 0 ? <EmptyState>No quotations found.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Request</th><th>Supplier</th><th className="num">Total</th><th>Delivery</th><th>Valid until</th><th>Status</th></tr></thead>
              <tbody>
                {data?.content.map((q) => (
                  <tr key={q.id}>
                    <td><Link to={`/quotations/${q.id}`}>{q.requestNumber}</Link> · {q.requestTitle}</td>
                    <td>{q.supplier.name}</td>
                    <td className="num"><Money value={q.totalAmount} /></td>
                    <td>{q.deliveryDays} days</td>
                    <td>{formatDate(q.validUntil)} {q.expired && <span className="badge badge-red">Expired</span>}</td>
                    <td><StatusBadge status={q.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
