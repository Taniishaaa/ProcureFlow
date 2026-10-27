import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, Field, Loading, Money, PageHeader, StatusBadge, errorText } from '../../components/ui.jsx';
import { todayIso } from '../../utils.js';

/** Create a quotation (/quotations/new?requestId=) or view / edit an existing one (/quotations/:id). */
export default function QuotationForm() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const isNew = !id;

  const [requestId, setRequestId] = useState(params.get('requestId') || '');
  const approved = useFetch(() => (isNew ? api.get('/requests/approved', { size: 100 }) : Promise.resolve(null)), [isNew]);
  const existing = useFetch(() => (isNew ? Promise.resolve(null) : api.get(`/quotations/${id}`)), [id]);
  const effectiveRequestId = isNew ? requestId : existing.data?.requestId;
  const request = useFetch(() => (effectiveRequestId ? api.get(`/requests/${effectiveRequestId}`) : Promise.resolve(null)), [effectiveRequestId]);
  const eligible = useFetch(
    () => (isNew && requestId ? api.get(`/requests/${requestId}/eligible-suppliers`) : Promise.resolve([])),
    [isNew, requestId],
  );

  const [form, setForm] = useState({ supplierId: '', taxPercent: '18', deliveryDays: '14', paymentTerms: '', validUntil: '', notes: '' });
  const [prices, setPrices] = useState({}); // requestItemId -> unit price
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const q = existing.data;
    if (!q) return;
    setForm({ supplierId: q.supplier.id, taxPercent: String(q.taxPercent), deliveryDays: String(q.deliveryDays), paymentTerms: q.paymentTerms || '', validUntil: q.validUntil || '', notes: q.notes || '' });
    setPrices(Object.fromEntries(q.lines.map((l) => [l.requestItemId, String(l.unitPrice)])));
  }, [existing.data]);

  const items = request.data?.items || [];
  const subtotal = items.reduce((s, i) => s + i.quantity * (Number(prices[i.id]) || 0), 0);
  const tax = (subtotal * (Number(form.taxPercent) || 0)) / 100;
  const readOnly = !isNew && (user.role !== 'PROCUREMENT_OFFICER' || existing.data?.status !== 'SUBMITTED');
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const common = {
      taxPercent: Number(form.taxPercent),
      deliveryDays: Number(form.deliveryDays),
      paymentTerms: form.paymentTerms.trim() || null,
      validUntil: form.validUntil || null,
      notes: form.notes.trim() || null,
      lines: items.map((i) => ({ requestItemId: i.id, unitPrice: Number(prices[i.id]) })),
    };
    try {
      if (isNew) {
        await api.post('/quotations', { requestId: Number(requestId), supplierId: Number(form.supplierId), ...common });
        toast.success('Quotation recorded');
        navigate(`/requests/${requestId}/compare`);
      } else {
        await api.put(`/quotations/${id}`, common);
        toast.success('Quotation updated');
        navigate(`/requests/${existing.data.requestId}/compare`);
      }
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (!isNew && existing.loading) return <Loading />;
  if (!isNew && existing.error) return <Alert>{errorText(existing.error)}</Alert>;

  return (
    <>
      <PageHeader
        title={isNew ? 'New quotation' : `Quotation · ${existing.data.supplier.name}`}
        subtitle={!isNew && <StatusBadge status={existing.data.status} />}
        actions={effectiveRequestId && <Link className="btn" to={`/requests/${effectiveRequestId}/compare`}>Compare quotations</Link>}
      />
      <form onSubmit={save}>
        <Alert>{errorText(error)}</Alert>
        {existing.data?.expired && <Alert type="warn">This quotation has expired and can no longer be ordered.</Alert>}
        <Card title="Quotation details">
          <div className="form-grid">
            <Field label="Approved request" required>
              {isNew ? (
                <select value={requestId} onChange={(e) => { setRequestId(e.target.value); set({ supplierId: '' }); setPrices({}); }} required>
                  <option value="">Select a request…</option>
                  {(approved.data?.content || []).map((r) => <option key={r.id} value={r.id}>{r.requestNumber} · {r.title}</option>)}
                </select>
              ) : (
                <input value={`${existing.data.requestNumber} · ${existing.data.requestTitle}`} disabled />
              )}
            </Field>
            <Field label="Supplier" required error={error?.fieldErrors?.supplierId} hint={isNew ? 'Only active suppliers registered for the request category are listed' : undefined}>
              {isNew ? (
                <select value={form.supplierId} onChange={(e) => set({ supplierId: e.target.value })} required disabled={!requestId}>
                  <option value="">Select a supplier…</option>
                  {(eligible.data || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              ) : (
                <input value={existing.data.supplier.name} disabled />
              )}
            </Field>
            <Field label="Tax (%)" required error={error?.fieldErrors?.taxPercent}>
              <input type="number" min="0" max="100" step="0.01" value={form.taxPercent} onChange={(e) => set({ taxPercent: e.target.value })} required disabled={readOnly} />
            </Field>
            <Field label="Delivery time (days)" required error={error?.fieldErrors?.deliveryDays}>
              <input type="number" min="1" max="365" step="1" value={form.deliveryDays} onChange={(e) => set({ deliveryDays: e.target.value })} required disabled={readOnly} />
            </Field>
            <Field label="Payment terms">
              <input value={form.paymentTerms} onChange={(e) => set({ paymentTerms: e.target.value })} maxLength={200} disabled={readOnly} placeholder="e.g. Net 30" />
            </Field>
            <Field label="Valid until" error={error?.fieldErrors?.validUntil}>
              <input type="date" min={isNew ? todayIso() : undefined} value={form.validUntil} onChange={(e) => set({ validUntil: e.target.value })} disabled={readOnly} />
            </Field>
            <div className="full">
              <Field label="Notes"><textarea value={form.notes} onChange={(e) => set({ notes: e.target.value })} maxLength={1000} disabled={readOnly} /></Field>
            </div>
          </div>
        </Card>

        <Card title="Pricing per item">
          {!effectiveRequestId ? <p className="muted">Select a request to enter prices.</p> : request.loading ? <Loading /> : (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Item</th><th className="num">Qty</th><th className="num">Estimated unit price</th><th>Quoted unit price</th><th className="num">Line total</th></tr></thead>
                  <tbody>
                    {items.map((i) => (
                      <tr key={i.id}>
                        <td>{i.name}</td>
                        <td className="num">{i.quantity}</td>
                        <td className="num"><Money value={i.estimatedUnitPrice} /></td>
                        <td><input type="number" min="0.01" step="0.01" className="inline-input" value={prices[i.id] || ''} required disabled={readOnly}
                          onChange={(e) => setPrices({ ...prices, [i.id]: e.target.value })} aria-label={`Unit price for ${i.name}`} /></td>
                        <td className="num"><Money value={i.quantity * (Number(prices[i.id]) || 0)} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="totals">
                <div><span>Subtotal</span><Money value={subtotal} /></div>
                <div><span>Tax ({Number(form.taxPercent) || 0}%)</span><Money value={tax} /></div>
                <div className="grand"><span>Total</span><Money value={subtotal + tax} /></div>
              </div>
              <p className="muted">Estimated total for comparison: <Money value={request.data?.totalEstimate} /></p>
            </>
          )}
        </Card>

        {!readOnly && (
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => navigate(-1)} disabled={busy}>Cancel</button>
            <button className="btn btn-primary" disabled={busy || !effectiveRequestId}>{busy ? 'Saving…' : isNew ? 'Record quotation' : 'Save changes'}</button>
          </div>
        )}
        {readOnly && !isNew && <p className="muted">Only submitted quotations can be edited, and only by a procurement officer.</p>}
      </form>
    </>
  );
}
