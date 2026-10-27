import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { Alert, Field, errorText } from '../../components/ui.jsx';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(await api.post('/auth/forgot-password', { email: email.trim() }));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>Reset your password</h1>
        <p className="muted">Enter your account email and we will issue a reset link.</p>
        <Alert>{errorText(error)}</Alert>
        {result && <Alert type="success">{result.message}</Alert>}
        {result?.resetToken && (
          <Alert type="warn">
            Development mode: no email server is configured, so the reset token is shown here.{' '}
            <Link to={`/reset-password?token=${encodeURIComponent(result.resetToken)}`}>Continue to reset</Link>
          </Alert>
        )}
        <Field label="Email" required>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </Field>
        <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={busy}>
          {busy ? 'Sending…' : 'Send reset link'}
        </button>
        <p style={{ textAlign: 'center' }}>
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </div>
  );
}
