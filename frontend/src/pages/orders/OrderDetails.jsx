import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, ConfirmModal, EmptyState, Field, Loading, Modal, Money, PageHeader, ProgressBar, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDate, formatDateTime, todayIso } from '../../utils.js';

function ReceiveModal({ order, onClose, onDone }) {
  const toast = useToast();
  const open = order.items.filter((i) => i.outstanding > 0);
  const [qty, setQty] = useState(Object.fromEntries(open.map((i) => [i.id, ''])));
  const [receivedDate, setReceivedDate] = useState(todayIso());
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function save(e) {
    e.preventDefault();
    const lines = open
      .filter((i) => Number(qty[i.id]) > 0)
      .map((i) => ({ poItemId: i.id, quantity: Number(qty[i.id]) }));
    if (lines.length === 0) {
      setError({ message: 'Enter a received quantity for at least one item' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.post(`/purchase-orders/${order.id}/deliveries`, { receivedDate, notes: notes.trim() || null, lines });
      toast.success('Delivery recorded');
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Record delivery"
      wide
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-primary" form="delivery-form" disabled={busy}>{busy ? 'Saving…' : 'Record delivery'}</button>
        </>
      }
    >
      <form id="delivery-form" onSubmit={save}>
        <Alert>{errorText(error)}</Alert>
        <Field label="Received on" required>
          <input type="date" max={todayIso()} value={receivedDate} onChange={(e) => setReceivedDate(e.target.value)} required />
        </Field>
        <table>
          <thead><tr><th>Item</th><th className="num">Ordered</th><th className="num">Received</th><th className="num">Outstanding</th><th>Receiving now</th></tr></thead>
          <tbody>
            {open.map((i) => (
              <tr key={i.id}>
                <td>{i.name}</td>
                <td className="num">{i.quantityOrdered}</td>
                <td className="num">{i.quantityReceived}</td>
                <td className="num">{i.outstanding}</td>
                <td>
                  <input type="number" className="inline-input" min="0" max={i.outstanding} step="1" value={qty[i.id]}
                    onChange={(e) => setQty({ ...qty, [i.id]: e.target.value })} aria-label={`Quantity received for ${i.name}`} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Field label="Notes">
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} />
        </Field>
      </form>
    </Modal>
  );
}

export default function OrderDetails() {
  const { id } = useParams();
  const toast = useToast();
  const { data: o, loading, error, reload } = useFetch(() => api.get(`/purchase-orders/${id}`), [id]);
  const history = useFetch(() => api.get(`/purchase-orders/${id}/history`), [id, o?.status, o?.deliveries?.length]);
  const [dialog, setDialog] = useState(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);

  function close() {
    setDialog(null);
    setReason('');
    setActionError(null);
  }

  async function run(path, body, message) {
    setBusy(true);
    setActionError(null);
    try {
      await api.post(path, body);
      toast.success(message);
      close();
      reload();
    } catch (err) {
      if (dialog === 'cancel') setActionError(err);
      else {
        toast.error(errorText(err));
        close();
        reload();
      }
    } finally {
      setBusy(false);
    }
  }

  if (loading && !o) return <Loading />;
  if (error) return <Alert>{errorText(error)}</Alert>;
  if (!o) return null;
  const actions = new Set(o.allowedActions || []);

  return (
    <>
      <PageHeader
        title={o.poNumber}
        subtitle={<><StatusBadge status={o.status} /> · {o.supplier.name}</>}
        actions={
          <>
            <button className="btn" onClick={() => window.print()}>Print</button>
            {actions.has('ISSUE') && <button className="btn btn-primary" onClick={() => setDialog('issue')}>Issue to supplier</button>}
            {actions.has('RECEIVE') && <button className="btn btn-success" onClick={() => setDialog('receive')}>Record delivery</button>}
            {actions.has('CANCEL') && <button className="btn btn-danger" onClick={() => setDialog('cancel')}>Cancel order</button>}
          </>
        }
      />

      <div className="grid grid-2 mb">
        <Card title="Order">
          <dl className="kv">
            <dt>Request</dt><dd><Link to={`/requests/${o.request.id}`}>{o.request.requestNumber}</Link> · {o.request.title}</dd>
            <dt>Requester</dt><dd>{o.request.requesterName}</dd>
            <dt>Created by</dt><dd>{o.createdByName} · {formatDateTime(o.createdAt)}</dd>
            <dt>Issued</dt><dd>{formatDateTime(o.issuedAt)}</dd>
            <dt>Expected delivery</dt><dd>{formatDate(o.expectedDeliveryDate)} <span className="muted">({o.deliveryDays} days)</span></dd>
            <dt>Payment terms</dt><dd>{o.paymentTerms || '—'}</dd>
            <dt>Notes</dt><dd>{o.notes || '—'}</dd>
            <dt>Delivery progress</dt><dd><ProgressBar percent={o.progressPercent} /> {o.progressPercent}%</dd>
          </dl>
        </Card>
        <Card title="Supplier">
          <dl className="kv">
            <dt>Name</dt><dd>{o.supplier.name}</dd>
            <dt>Contact</dt><dd>{o.supplier.contactName}</dd>
            <dt>Email</dt><dd>{o.supplier.email}</dd>
            <dt>Phone</dt><dd>{o.supplier.phone || '—'}</dd>
            <dt>Address</dt><dd>{o.supplier.address || '—'}</dd>
          </dl>
        </Card>
      </div>

      <Card title="Items">
        <div className="table-wrap">
          <table>
            <thead><tr><th>Item</th><th className="num">Ordered</th><th className="num">Received</th><th className="num">Outstanding</th><th className="num">Unit price</th><th className="num">Line total</th></tr></thead>
            <tbody>
              {o.items.map((i) => (
                <tr key={i.id}>
                  <td>{i.name}{i.description && <div className="muted">{i.description}</div>}</td>
                  <td className="num">{i.quantityOrdered}</td>
                  <td className="num">{i.quantityReceived}</td>
                  <td className="num">{i.outstanding}</td>
                  <td className="num"><Money value={i.unitPrice} /></td>
                  <td className="num"><Money value={i.lineTotal} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="totals">
          <div><span>Subtotal</span><Money value={o.subtotal} /></div>
          <div><span>Tax ({o.taxPercent}%)</span><Money value={o.taxAmount} /></div>
          <div className="grand"><span>Total</span><Money value={o.totalAmount} /></div>
        </div>
      </Card>

      <Card title="Deliveries received">
        {o.deliveries.length === 0 ? <EmptyState>No deliveries recorded yet.</EmptyState> : (
          <ul className="timeline">
            {o.deliveries.map((d) => (
              <li key={d.id}>
                <strong>{formatDate(d.receivedDate)}</strong> · recorded by {d.receivedByName}
                <div>{d.lines.map((l) => `${l.quantity} × ${l.itemName}`).join(', ')}</div>
                {d.notes && <div className="muted">{d.notes}</div>}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="History">
        {history.data?.length ? (
          <ul className="timeline">
            {history.data.map((h) => (
              <li key={h.id}><strong>{h.action}</strong> — {h.details}<div className="muted">{h.actorEmail} · {formatDateTime(h.createdAt)}</div></li>
            ))}
          </ul>
        ) : <EmptyState>No history available.</EmptyState>}
      </Card>

      {dialog === 'issue' && (
        <ConfirmModal title="Issue purchase order" message={`Issue ${o.poNumber} to ${o.supplier.name}? The requester will be notified and the order can no longer be changed.`}
          confirmLabel="Issue order" busy={busy} onClose={close} onConfirm={() => run(`/purchase-orders/${o.id}/issue`, undefined, 'Purchase order issued')} />
      )}
      {dialog === 'receive' && <ReceiveModal order={o} onClose={close} onDone={() => { close(); reload(); }} />}
      {dialog === 'cancel' && (
        <Modal title="Cancel purchase order" onClose={close}
          footer={
            <>
              <button className="btn" onClick={close} disabled={busy}>Keep order</button>
              <button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={() => run(`/purchase-orders/${o.id}/cancel`, { reason: reason.trim() }, 'Purchase order cancelled')}>
                {busy ? 'Working…' : 'Cancel order'}
              </button>
            </>
          }
        >
          <Alert>{errorText(actionError)}</Alert>
          <Field label="Reason for cancellation" required>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} autoFocus />
          </Field>
        </Modal>
      )}
    </>
  );
}
