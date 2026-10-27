import { useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useToast } from '../../toast.jsx';
import { Alert, Card, Field, PageHeader, errorText } from '../../components/ui.jsx';
import { ROLE_LABELS, formatDateTime } from '../../utils.js';

export default function Profile() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ fullName: user.fullName, department: user.department || '' });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [profileError, setProfileError] = useState(null);
  const [pwError, setPwError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function saveProfile(e) {
    e.preventDefault();
    setBusy(true);
    setProfileError(null);
    try {
      await api.put('/me', { fullName: form.fullName.trim(), department: form.department.trim() || null });
      await refreshUser();
      toast.success('Profile updated');
    } catch (err) {
      setProfileError(err);
    } finally {
      setBusy(false);
    }
  }

  async function changePassword(e) {
    e.preventDefault();
    setPwError(null);
    if (pw.newPassword !== pw.confirm) {
      setPwError({ message: 'The new passwords do not match' });
      return;
    }
    setBusy(true);
    try {
      await api.post('/me/change-password', { currentPassword: pw.currentPassword, newPassword: pw.newPassword });
      setPw({ currentPassword: '', newPassword: '', confirm: '' });
      toast.success('Password changed');
    } catch (err) {
      setPwError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="My profile" subtitle={`${user.email} · ${ROLE_LABELS[user.role]}`} />
      <div className="grid grid-2">
        <Card title="Details">
          <form onSubmit={saveProfile}>
            <Alert>{errorText(profileError)}</Alert>
            <Field label="Full name" required>
              <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required maxLength={150} />
            </Field>
            <Field label="Department">
              <input value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} maxLength={100} />
            </Field>
            <p className="muted">Last sign-in: {formatDateTime(user.lastLoginAt)}</p>
            <div className="form-actions">
              <button className="btn btn-primary" disabled={busy}>Save changes</button>
            </div>
          </form>
        </Card>
        <Card title="Change password">
          <form onSubmit={changePassword}>
            <Alert>{errorText(pwError)}</Alert>
            <Field label="Current password" required>
              <input type="password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} required autoComplete="current-password" />
            </Field>
            <Field label="New password" required hint="At least 8 characters with a letter and a digit">
              <input type="password" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} required autoComplete="new-password" />
            </Field>
            <Field label="Confirm new password" required>
              <input type="password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} required autoComplete="new-password" />
            </Field>
            <div className="form-actions">
              <button className="btn btn-primary" disabled={busy}>Change password</button>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
