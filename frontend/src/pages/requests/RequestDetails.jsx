import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, ConfirmModal, Field, Loading, Modal, Money, PageHeader, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDate, formatDateTime } from '../../utils.js';

export default function RequestDetails({ review = false }) {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const { data: r, loading, error, reload } = useFetch(() => api.get(`/requests/${id}`), [id]);
  const canSeeQuotes = ['PROCUREMENT_OFFICER', 'ADMIN'].includes(user.role);
  const quotes = useFetch(
    () => (canSeeQuotes ? api.get('/quotations', { requestId: id, size: 50 }) : Promise.resolve(null)),
    [id, canSeeQuotes],
  );

  const [dialog, setDialog] = useState(null); // 'cancel' | 'approve' | 'reject' | 'submit'
  const [comments, setComments] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);

  function close() {
    setDialog(null);
    setComments('');
    setActionError(null);
  }

  async function run(path, body, message, after) {
    setBusy(true);
    setActionError(null);
    try {
      await api.post(path, body);
      toast.success(message);
      close();
      if (after) after();
      else reload();
    } catch (err) {
      if (dialog === 'approve' || dialog === 'reject') {
        setActionError(err);
      } else {
        toast.error(errorText(err));
        close();
        reload();
      }
    } finally {
      setBusy(false);
    }
  }

  if (loading && !r) return <Loading />;
  if (error) return <Alert>{errorText(error)}</Alert>;
  if (!r) return null;

  const actions = new Set(r.allowedActions || []);
  const total = r.items.reduce((s, i) => s + Number(i.lineTotal), 0);

  return (
    <>
      <PageHeader
        title={`${r.requestNumber} · ${r.title}`}
        subtitle={<StatusBadge status={r.status} />}
        actions={
          <>
            {actions.has('EDIT') && <Link className="btn" to={`/requests/${r.id}/edit`}>Edit</Link>}
            {actions.has('SUBMIT') && <button className="btn btn-primary" onClick={() => setDialog('submit')}>Submit for approval</button>}
            {actions.has('CANCEL') && <button className="btn btn-danger" onClick={() => setDialog('cancel')}>Cancel request</button>}
            {actions.has('APPROVE') && <button className="btn btn-success" onClick={() => setDialog('approve')}>Approve</button>}
            {actions.has('REJECT') && <button className="btn btn-danger" onClick={() => setDialog('reject')}>Reject</button>}
            {actions.has('ADD_QUOTATION') && <Link className="btn" to={`/quotations/new?requestId=${r.id}`}>Add quotation</Link>}
            {actions.has('COMPARE_QUOTATIONS') && <Link className="btn btn-primary" to={`/requests/${r.id}/compare`}>Compare &amp; order</Link>}
            {user.role === 'ADMIN' && r.status === 'APPROVED' && <Link className="btn" to={`/requests/${r.id}/compare`}>View comparison</Link>}
            {review && <Link className="btn" to="/approvals">Back to queue</Link>}
          </>
        }
      />

      {r.status === 'REJECTED' && r.approvals?.length > 0 && (
        <Alert type="error">Rejected: {r.approvals[r.approvals.length - 1].comments}</Alert>
      )}

      <div className="grid grid-2 mb">
        <Card title="Request">
          <dl className="kv">
            <dt>Requester</dt><dd>{r.requester.name} <span className="muted">({r.requester.department || 'no department'})</span></dd>
            <dt>Category</dt><dd>{r.category.name}</dd>
            <dt>Created</dt><dd>{formatDateTime(r.createdAt)}</dd>
            <dt>Submitted</dt><dd>{formatDateTime(r.submittedAt)}</dd>
            <dt>Estimated total</dt><dd><Money value={r.totalEstimate} /></dd>
            {r.purchaseOrderId && (
              <>
                <dt>Purchase order</dt>
                <dd>
                  <Link to={`/orders/${r.purchaseOrderId}`}>{r.purchaseOrderNumber}</Link>{' '}
                  <StatusBadge status={r.purchaseOrderStatus} />
                </dd>
              </>
            )}
          </dl>
        </Card>
        <Card title="Business justification">
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{r.justification}</p>
        </Card>
      </div>

      <Card title="Items">
        <div className="table-wrap">
          <table>
            <thead><tr><th>Item</th><th>Description</th><th className="num">Qty</th><th className="num">Est. unit price</th><th className="num">Line total</th></tr></thead>
            <tbody>
              {r.items.map((i) => (
                <tr key={i.id}>
                  <td>{i.name}</td>
                  <td className="muted">{i.description}</td>
                  <td className="num">{i.quantity}</td>
                  <td className="num"><Money value={i.estimatedUnitPrice} /></td>
                  <td className="num"><Money value={i.lineTotal} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="totals"><div className="grand"><span>Total</span><Money value={total} /></div></div>
      </Card>

      {canSeeQuotes && quotes.data && quotes.data.content.length > 0 && (
        <Card title="Quotations">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Supplier</th><th className="num">Total</th><th>Delivery</th><th>Valid until</th><th>Status</th></tr></thead>
              <tbody>
                {quotes.data.content.map((q) => (
                  <tr key={q.id}>
                    <td><Link to={`/quotations/${q.id}`}>{q.supplier.name}</Link></td>
                    <td className="num"><Money value={q.totalAmount} /></td>
                    <td>{q.deliveryDays} days</td>
                    <td>{formatDate(q.validUntil)} {q.expired && <span className="badge badge-red">Expired</span>}</td>
                    <td><StatusBadge status={q.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <Card title="Approval history">
        {r.approvals?.length ? (
          <ul className="timeline">
            {r.approvals.map((a) => (
              <li key={a.id}>
                <StatusBadge status={a.decision} /> by {a.approver?.name} · {formatDateTime(a.decidedAt)}
                {a.comments && <div>{a.comments}</div>}
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">No decisions recorded yet.</p>
        )}
      </Card>

      {dialog === 'submit' && (
        <ConfirmModal title="Submit for approval" message="Once submitted, you can no longer edit this request." confirmLabel="Submit" busy={busy}
          onClose={close} onConfirm={() => run(`/requests/${r.id}/submit`, undefined, 'Request submitted')} />
      )}
      {dialog === 'cancel' && (
        <ConfirmModal title="Cancel request" message="This will cancel the request. This cannot be undone." confirmLabel="Cancel request" danger busy={busy}
          onClose={close} onConfirm={() => run(`/requests/${r.id}/cancel`, undefined, 'Request cancelled')} />
      )}
      {(dialog === 'approve' || dialog === 'reject') && (
        <Modal
          title={dialog === 'approve' ? 'Approve request' : 'Reject request'}
          onClose={close}
          footer={
            <>
              <button className="btn" onClick={close} disabled={busy}>Cancel</button>
              <button
                className={`btn ${dialog === 'approve' ? 'btn-success' : 'btn-danger'}`}
                disabled={busy || (dialog === 'reject' && !comments.trim())}
                onClick={() =>
                  run(`/requests/${r.id}/${dialog}`, { comments: comments.trim() || null },
                    dialog === 'approve' ? 'Request approved' : 'Request rejected',
                    () => navigate('/approvals'))
                }
              >
                {busy ? 'Working…' : dialog === 'approve' ? 'Approve' : 'Reject'}
              </button>
            </>
          }
        >
          <Alert>{errorText(actionError)}</Alert>
          <p>{r.requestNumber} · <Money value={r.totalEstimate} /> · requested by {r.requester.name}</p>
          <Field label={dialog === 'reject' ? 'Reason for rejection' : 'Comments (optional)'} required={dialog === 'reject'}>
            <textarea value={comments} onChange={(e) => setComments(e.target.value)} maxLength={1000} autoFocus />
          </Field>
        </Modal>
      )}
    </>
  );
}
