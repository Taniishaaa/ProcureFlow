import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, Field, Loading, Money, PageHeader, errorText } from '../../components/ui.jsx';

const emptyItem = () => ({ name: '', description: '', quantity: 1, estimatedUnitPrice: '' });

export default function RequestForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const toast = useToast();
  const categories = useFetch(() => api.get('/categories'), []);
  const existing = useFetch(() => (editing ? api.get(`/requests/${id}`) : Promise.resolve(null)), [id]);
  const settings = useFetch(() => api.get('/settings'), []);

  const [form, setForm] = useState({ title: '', categoryId: '', justification: '' });
  const [items, setItems] = useState([emptyItem()]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const r = existing.data;
    if (!r) return;
    setForm({ title: r.title, categoryId: r.category.id, justification: r.justification });
    setItems(r.items.map((i) => ({ name: i.name, description: i.description || '', quantity: i.quantity, estimatedUnitPrice: i.estimatedUnitPrice })));
  }, [existing.data]);

  const total = items.reduce((sum, i) => sum + (Number(i.quantity) || 0) * (Number(i.estimatedUnitPrice) || 0), 0);
  const maxTotal = Number(settings.data?.find((s) => s.key === 'request.max_total')?.value || 0);

  function updateItem(index, patch) {
    setItems((list) => list.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  }

  async function save(submit) {
    setError(null);
    setBusy(true);
    const body = {
      title: form.title.trim(),
      categoryId: Number(form.categoryId),
      justification: form.justification.trim(),
      items: items.map((i) => ({
        name: i.name.trim(),
        description: i.description.trim() || null,
        quantity: Number(i.quantity),
        estimatedUnitPrice: Number(i.estimatedUnitPrice),
      })),
    };
    try {
      let saved;
      if (editing) {
        saved = await api.put(`/requests/${id}`, body);
        if (submit) saved = await api.post(`/requests/${id}/submit`);
      } else {
        saved = await api.post('/requests', { ...body, submit });
      }
      toast.success(submit ? 'Request submitted for approval' : 'Draft saved');
      navigate(`/requests/${saved.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (editing && existing.loading) return <Loading />;
  if (editing && existing.error) return <Alert>{errorText(existing.error)}</Alert>;

  return (
    <>
      <PageHeader title={editing ? `Edit ${existing.data?.requestNumber || 'request'}` : 'New purchase request'} />
      <form onSubmit={(e) => { e.preventDefault(); save(false); }}>
        <Alert>{errorText(error)}</Alert>
        {editing && existing.data?.status === 'REJECTED' && (
          <Alert type="warn">This request was rejected. Edit it and submit again to restart the approval.</Alert>
        )}
        <Card title="Details">
          <div className="form-grid">
            <Field label="Title" required error={error?.fieldErrors?.title}>
              <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required maxLength={200} />
            </Field>
            <Field label="Category" required error={error?.fieldErrors?.categoryId}>
              <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })} required>
                <option value="">Select a category…</option>
                {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <div className="full">
              <Field label="Business justification" required error={error?.fieldErrors?.justification}>
                <textarea value={form.justification} onChange={(e) => setForm({ ...form, justification: e.target.value })} required maxLength={2000} />
              </Field>
            </div>
          </div>
        </Card>

        <Card title="Items" actions={<button type="button" className="btn btn-sm" onClick={() => setItems([...items, emptyItem()])}>+ Add item</button>}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Item</th><th>Description</th><th>Qty</th><th>Est. unit price</th><th className="num">Line total</th><th /></tr>
              </thead>
              <tbody>
                {items.map((item, index) => (
                  <tr key={index}>
                    <td><input value={item.name} onChange={(e) => updateItem(index, { name: e.target.value })} required maxLength={200} aria-label="Item name" /></td>
                    <td><input value={item.description} onChange={(e) => updateItem(index, { description: e.target.value })} maxLength={500} aria-label="Description" /></td>
                    <td><input type="number" min="1" max="100000" step="1" value={item.quantity} onChange={(e) => updateItem(index, { quantity: e.target.value })} required className="inline-input" aria-label="Quantity" /></td>
                    <td><input type="number" min="0.01" step="0.01" value={item.estimatedUnitPrice} onChange={(e) => updateItem(index, { estimatedUnitPrice: e.target.value })} required className="inline-input" aria-label="Estimated unit price" /></td>
                    <td className="num"><Money value={(Number(item.quantity) || 0) * (Number(item.estimatedUnitPrice) || 0)} /></td>
                    <td>
                      {items.length > 1 && (
                        <button type="button" className="icon-btn" aria-label="Remove item" onClick={() => setItems(items.filter((_, i) => i !== index))}>×</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="totals">
            <div className="grand"><span>Estimated total</span><Money value={total} /></div>
          </div>
          {maxTotal > 0 && total > maxTotal && (
            <Alert type="warn">The total exceeds the allowed maximum of <Money value={maxTotal} /> per request.</Alert>
          )}
        </Card>

        <div className="form-actions">
          <button type="button" className="btn" onClick={() => navigate(-1)} disabled={busy}>Cancel</button>
          <button type="submit" className="btn" disabled={busy}>Save as draft</button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => save(true)}>Save &amp; submit for approval</button>
        </div>
      </form>
    </>
  );
}
