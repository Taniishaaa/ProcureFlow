import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, Money, PageHeader, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDate } from '../../utils.js';

export default function QuotationComparison() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const { data, loading, error } = useFetch(() => api.get(`/requests/${id}/comparison`), [id]);

  if (loading) return <Loading />;
  if (error) return <Alert>{errorText(error)}</Alert>;
  const canOrder = user.role === 'PROCUREMENT_OFFICER' && data.requestStatus === 'APPROVED';

  return (
    <>
      <PageHeader
        title={`Compare quotations · ${data.requestNumber}`}
        subtitle={<>{data.requestTitle} · estimate <Money value={data.totalEstimate} /> · <StatusBadge status={data.requestStatus} /></>}
        actions={
          <>
            <Link className="btn" to={`/requests/${id}`}>View request</Link>
            {user.role === 'PROCUREMENT_OFFICER' && data.requestStatus === 'APPROVED' && (
              <Link className="btn" to={`/quotations/new?requestId=${id}`}>Add quotation</Link>
            )}
          </>
        }
      />
      {data.requestStatus !== 'APPROVED' && (
        <Alert type="info">Orders can only be created from approved requests. This request is {data.requestStatus.toLowerCase()}.</Alert>
      )}
      <Card>
        {data.rows.length === 0 ? <EmptyState>No quotations have been recorded for this request yet.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Supplier</th><th className="num">Subtotal</th><th className="num">Tax</th><th className="num">Total</th><th className="num">vs estimate</th><th>Delivery</th><th>Terms</th><th>Valid until</th><th className="num">Score</th><th /></tr>
              </thead>
              <tbody>
                {data.rows.map((row) => (
                  <tr key={row.quotationId} style={row.recommended ? { background: '#f0fdf4' } : undefined}>
                    <td>
                      <Link to={`/quotations/${row.quotationId}`}><strong>{row.supplier.name}</strong></Link>
                      <div>
                        {row.recommended && <span className="flag flag-green">Recommended</span>}
                        {row.lowestPrice && <span className="flag flag-blue">Lowest price</span>}
                        {row.fastestDelivery && <span className="flag flag-purple">Fastest</span>}
                      </div>
                    </td>
                    <td className="num"><Money value={row.subtotal} /></td>
                    <td className="num"><Money value={row.taxAmount} /> <small className="muted">({row.taxPercent}%)</small></td>
                    <td className="num"><strong><Money value={row.totalAmount} /></strong></td>
                    <td className="num" style={{ color: Number(row.savingsVsEstimate) >= 0 ? 'var(--green)' : 'var(--red)' }}>
                      {Number(row.savingsVsEstimate) >= 0 ? 'Saves ' : 'Over by '}<Money value={Math.abs(Number(row.savingsVsEstimate))} />
                    </td>
                    <td>{row.deliveryDays} days</td>
                    <td>{row.paymentTerms || '—'}</td>
                    <td>{formatDate(row.validUntil)} {row.expired && <span className="badge badge-red">Expired</span>}</td>
                    <td className="num">{row.orderable ? row.score : '—'}</td>
                    <td className="right nowrap">
                      <StatusBadge status={row.status} />{' '}
                      {canOrder && row.orderable && (
                        <button className="btn btn-sm btn-primary" onClick={() => navigate(`/purchase-orders/new?requestId=${id}&quotationId=${row.quotationId}`)}>
                          Create PO
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="muted mt">
          Score weights price 60% and delivery time 40% across orderable quotations (open, unexpired, active supplier). The recommendation is advisory — you choose the supplier.
        </p>
      </Card>
    </>
  );
}
