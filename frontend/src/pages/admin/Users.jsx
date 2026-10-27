import { useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Field, Loading, Modal, PageHeader, Pagination, StatusBadge, errorText } from '../../components/ui.jsx';
import { ROLE_LABELS, formatDateTime } from '../../utils.js';

function UserModal({ user, onClose, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState(
    user
      ? { email: user.email, fullName: user.fullName, department: user.department || '', role: user.role, password: '' }
      : { email: '', fullName: '', department: '', role: 'EMPLOYEE', password: '' },
  );
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (user) {
        await api.put(`/users/${user.id}`, { fullName: form.fullName.trim(), department: form.department.trim() || null, role: form.role });
      } else {
        await api.post('/users', { email: form.email.trim(), fullName: form.fullName.trim(), department: form.department.trim() || null, role: form.role, password: form.password });
      }
      toast.success(user ? 'User updated' : 'User created');
      onSaved();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      title={user ? 'Edit user' : 'New user'}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-primary" form="user-form" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </>
      }
    >
      <form id="user-form" onSubmit={save}>
        <Alert>{errorText(error)}</Alert>
        <Field label="Email" required error={error?.fieldErrors?.email}>
          <input type="email" value={form.email} onChange={(e) => set({ email: e.target.value })} required disabled={Boolean(user)} />
        </Field>
        <Field label="Full name" required error={error?.fieldErrors?.fullName}>
          <input value={form.fullName} onChange={(e) => set({ fullName: e.target.value })} required maxLength={150} />
        </Field>
        <Field label="Department"><input value={form.department} onChange={(e) => set({ department: e.target.value })} maxLength={100} /></Field>
        <Field label="Role" required>
          <select value={form.role} onChange={(e) => set({ role: e.target.value })}>
            {Object.entries(ROLE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </Field>
        {!user && (
          <Field label="Initial password" required error={error?.fieldErrors?.password} hint="At least 8 characters with a letter and a digit">
            <input type="password" value={form.password} onChange={(e) => set({ password: e.target.value })} required autoComplete="new-password" />
          </Field>
        )}
      </form>
    </Modal>
  );
}

export default function Users() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [active, setActive] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(undefined);
  const debounced = useDebounced(search);
  const { data, loading, error, reload } = useFetch(
    () => api.get('/users', { search: debounced, role, active, page, size: 10 }),
    [debounced, role, active, page],
  );

  async function toggleActive(u) {
    try {
      await api.patch(`/users/${u.id}/active`, { active: !u.active });
      toast.success(u.active ? 'User deactivated' : 'User activated');
      reload();
    } catch (err) {
      toast.error(errorText(err));
    }
  }

  return (
    <>
      <PageHeader title="Users" actions={<button className="btn btn-primary" onClick={() => setEditing(null)}>New user</button>} />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by name or email…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
            <option value="">All roles</option>
            {Object.entries(ROLE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <select value={active} onChange={(e) => { setActive(e.target.value); setPage(0); }}>
            <option value="">Active &amp; inactive</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.content.length === 0 ? <EmptyState>No users found.</EmptyState> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Email</th><th>Department</th><th>Role</th><th>Status</th><th>Last sign-in</th><th /></tr></thead>
              <tbody>
                {data?.content.map((u) => (
                  <tr key={u.id}>
                    <td>{u.fullName}</td>
                    <td>{u.email}</td>
                    <td>{u.department || '—'}</td>
                    <td>{ROLE_LABELS[u.role]}</td>
                    <td><StatusBadge status={u.active ? 'ACTIVE' : 'INACTIVE'} /></td>
                    <td className="nowrap">{formatDateTime(u.lastLoginAt)}</td>
                    <td className="right nowrap">
                      <button className="btn btn-sm" onClick={() => setEditing(u)}>Edit</button>{' '}
                      <button className="btn btn-sm" disabled={u.id === me.id} title={u.id === me.id ? 'You cannot deactivate yourself' : undefined} onClick={() => toggleActive(u)}>
                        {u.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
      {editing !== undefined && <UserModal user={editing} onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); reload(); }} />}
    </>
  );
}
