import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, Field, Loading, Money, PageHeader, errorText } from '../../components/ui.jsx';
import { formatDate } from '../../utils.js';

export default function CreatePurchaseOrder() {
  const [params] = useSearchParams();
  const requestId = params.get('requestId');
  const quotationId = params.get('quotationId');
  const navigate = useNavigate();
  const toast = useToast();
  const [notes, setNotes] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const quote = useFetch(() => (quotationId ? api.get(`/quotations/${quotationId}`) : Promise.resolve(null)), [quotationId]);

  if (!requestId || !quotationId) {
    return <Alert>Choose a quotation from the <Link to="/procurement/requests">approved requests</Link> comparison first.</Alert>;
  }
  if (quote.loading) return <Loading />;
  if (quote.error) return <Alert>{errorText(quote.error)}</Alert>;
  const q = quote.data;

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const po = await api.post('/purchase-orders', { requestId: Number(requestId), quotationId: Number(quotationId), notes: notes.trim() || null });
      toast.success(`Purchase order ${po.poNumber} created`);
      navigate(`/orders/${po.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Create purchase order" subtitle="The order is created as a draft; review it and issue it to the supplier." />
      <Alert>{errorText(error)}</Alert>
      <Card title="Selected quotation">
        <dl className="kv">
          <dt>Request</dt><dd><Link to={`/requests/${q.requestId}`}>{q.requestNumber}</Link> · {q.requestTitle}</dd>
          <dt>Supplier</dt><dd>{q.supplier.name}</dd>
          <dt>Delivery</dt><dd>{q.deliveryDays} days after issue</dd>
          <dt>Payment terms</dt><dd>{q.paymentTerms || '—'}</dd>
          <dt>Valid until</dt><dd>{formatDate(q.validUntil)}</dd>
        </dl>
        <div className="table-wrap mt">
          <table>
            <thead><tr><th>Item</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Line total</th></tr></thead>
            <tbody>
              {q.lines.map((l) => (
                <tr key={l.requestItemId}><td>{l.itemName}</td><td className="num">{l.quantity}</td><td className="num"><Money value={l.unitPrice} /></td><td className="num"><Money value={l.lineTotal} /></td></tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="totals">
          <div><span>Subtotal</span><Money value={q.subtotal} /></div>
          <div><span>Tax ({q.taxPercent}%)</span><Money value={q.taxAmount} /></div>
          <div className="grand"><span>Total</span><Money value={q.totalAmount} /></div>
        </div>
      </Card>
      <Card>
        <Field label="Notes for the order"><textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} /></Field>
        <div className="form-actions">
          <Link className="btn" to={`/requests/${requestId}/compare`}>Back to comparison</Link>
          <button className="btn btn-primary" onClick={create} disabled={busy}>{busy ? 'Creating…' : 'Create purchase order'}</button>
        </div>
      </Card>
    </>
  );
}
