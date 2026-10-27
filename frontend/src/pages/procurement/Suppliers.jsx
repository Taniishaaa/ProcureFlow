import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Field, Loading, Modal, PageHeader, Pagination, StatusBadge, errorText } from '../../components/ui.jsx';

const blank = { name: '', contactName: '', email: '', phone: '', address: '', taxId: '', categoryIds: [] };

function SupplierModal({ supplier, categories, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(
    supplier
      ? { name: supplier.name, contactName: supplier.contactName, email: supplier.email, phone: supplier.phone || '', address: supplier.address || '', taxId: supplier.taxId || '', categoryIds: supplier.categories.map((c) => c.id) }
      : blank,
  );
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const toggle = (id) => set({ categoryIds: form.categoryIds.includes(id) ? form.categoryIds.filter((x) => x !== id) : [...form.categoryIds, id] });

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const body = {
      name: form.name.trim(), contactName: form.contactName.trim(), email: form.email.trim(),
      phone: form.phone.trim() || null, address: form.address.trim() || null, taxId: form.taxId.trim() || null,
      categoryIds: form.categoryIds,
    };
    try {
      if (supplier) await api.put(`/suppliers/${supplier.id}`, body);
      else await api.post('/suppliers', body);
      toast.success(supplier ? 'Supplier updated' : 'Supplier created');
      onSaved();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={supplier ? 'Edit supplier' : 'New supplier'}
      onClose={onClose}
      wide
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-primary" form="supplier-form" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </>
      }
    >
      <form id="supplier-form" onSubmit={save}>
        <Alert>{errorText(error)}</Alert>
        <div className="form-grid">
          <Field label="Company name" required error={error?.fieldErrors?.name}><input value={form.name} onChange={(e) => set({ name: e.target.value })} required maxLength={200} /></Field>
          <Field label="Contact person" required error={error?.fieldErrors?.contactName}><input value={form.contactName} onChange={(e) => set({ contactName: e.target.value })} required maxLength={150} /></Field>
          <Field label="Email" required error={error?.fieldErrors?.email}><input type="email" value={form.email} onChange={(e) => set({ email: e.target.value })} required /></Field>
          <Field label="Phone"><input value={form.phone} onChange={(e) => set({ phone: e.target.value })} maxLength={40} /></Field>
          <Field label="Tax ID"><input value={form.taxId} onChange={(e) => set({ taxId: e.target.value })} maxLength={50} /></Field>
          <Field label="Address"><input value={form.address} onChange={(e) => set({ address: e.target.value })} maxLength={500} /></Field>
        </div>
        <Field label="Categories supplied" required error={error?.fieldErrors?.categoryIds} hint="A supplier can only quote on requests in these categories">
          <div>
            {categories.map((c) => (
              <label key={c.id} className="tag">
                <input type="checkbox" checked={form.categoryIds.includes(c.id)} onChange={() => toggle(c.id)} /> {c.name}
              </label>
            ))}
          </div>
        </Field>
      </form>
    </Modal>
  );
}

export default function Suppliers() {
  const { user } = useAuth();
  const toast = useToast();
  const canWrite = user.role === 'PROCUREMENT_OFFICER';
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = new, object = edit
  const debounced = useDebounced(search);
  const categories = useFetch(() => api.get('/categories'), []);
  const { data, loading, error, reload } = useFetch(
    () => api.get('/suppliers', { search: debounced, active, categoryId, page, size: 10 }),
    [debounced, active, categoryId, page],
  );

  async function toggleActive(s) {
    try {
      await api.patch(`/suppliers/${s.id}/active`, { active: !s.active });
      toast.success(s.active ? 'Supplier deactivated' : 'Supplier activated');
      reload();
    } catch (err) {
      toast.error(errorText(err));
    }
  }

  return (
    <>
      <PageHeader title="Suppliers" actions={canWrite && <button className="btn btn-primary" onClick={() => setEditing(null)}>New supplier</button>} />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by name, contact or email…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={active} onChange={(e) => { setActive(e.target.value); setPage(0); }}>
            <option value="">Active &amp; inactive</option>
            <option value="true">Active only</option>
            <option value="false">Inactive only</option>
          </select>
          <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
            <option value="">All categories</option>
            {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.content.length === 0 ? <EmptyState>No suppliers found.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Contact</th><th>Categories</th><th>Status</th><th /></tr></thead>
              <tbody>
                {data?.content.map((s) => (
                  <tr key={s.id}>
                    <td><Link to={`/suppliers/${s.id}`}>{s.name}</Link></td>
                    <td>{s.contactName}<div className="muted">{s.email}</div></td>
                    <td>{s.categories.map((c) => <span key={c.id} className="tag">{c.name}</span>)}</td>
                    <td><StatusBadge status={s.active ? 'ACTIVE' : 'INACTIVE'} /></td>
                    <td className="right nowrap">
                      {canWrite && (
                        <>
                          <button className="btn btn-sm" onClick={() => setEditing(s)}>Edit</button>{' '}
                          <button className="btn btn-sm" onClick={() => toggleActive(s)}>{s.active ? 'Deactivate' : 'Activate'}</button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
      {editing !== undefined && (
        <SupplierModal
          supplier={editing}
          categories={categories.data || []}
          onClose={() => setEditing(undefined)}
          onSaved={() => { setEditing(undefined); reload(); }}
        />
      )}
    </>
  );
}
