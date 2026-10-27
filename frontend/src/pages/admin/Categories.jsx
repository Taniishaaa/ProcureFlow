import { useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Field, Loading, Modal, PageHeader, StatusBadge, errorText } from '../../components/ui.jsx';

function CategoryModal({ category, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState({ name: category?.name || '', description: category?.description || '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = { name: form.name.trim(), description: form.description.trim() || null };
    try {
      if (category) await api.put(`/categories/${category.id}`, body);
      else await api.post('/categories', body);
      toast.success(category ? 'Category updated' : 'Category created');
      onSaved();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={category ? 'Edit category' : 'New category'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-primary" form="category-form" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </>
      }
    >
      <form id="category-form" onSubmit={save}>
        <Alert>{errorText(error)}</Alert>
        <Field label="Name" required error={error?.fieldErrors?.name}>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required maxLength={100} autoFocus />
        </Field>
        <Field label="Description">
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={300} />
        </Field>
      </form>
    </Modal>
  );
}

export default function Categories() {
  const toast = useToast();
  const [editing, setEditing] = useState(undefined);
  const { data, loading, error, reload } = useFetch(() => api.get('/categories', { includeInactive: true }), []);

  async function toggle(c) {
    try {
      await api.patch(`/categories/${c.id}/active`, { active: !c.active });
      toast.success(c.active ? 'Category deactivated' : 'Category activated');
      reload();
    } catch (err) {
      toast.error(errorText(err));
    }
  }

  return (
    <>
      <PageHeader title="Categories" subtitle="Inactive categories cannot be used on new requests." actions={<button className="btn btn-primary" onClick={() => setEditing(null)}>New category</button>} />
      <Card>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.length === 0 ? <EmptyState>No categories yet.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Description</th><th>Status</th><th /></tr></thead>
              <tbody>
                {data?.map((c) => (
                  <tr key={c.id}>
                    <td>{c.name}</td>
                    <td className="muted">{c.description || '—'}</td>
                    <td><StatusBadge status={c.active ? 'ACTIVE' : 'INACTIVE'} /></td>
                    <td className="right nowrap">
                      <button className="btn btn-sm" onClick={() => setEditing(c)}>Edit</button>{' '}
                      <button className="btn btn-sm" onClick={() => toggle(c)}>{c.active ? 'Deactivate' : 'Activate'}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {editing !== undefined && <CategoryModal category={editing} onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); reload(); }} />}
    </>
  );
}
