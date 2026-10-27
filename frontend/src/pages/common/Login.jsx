import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth.jsx';
import { Alert, Field, errorText } from '../../components/ui.jsx';

const DEMO = [
  ['Employee', 'employee@procureflow.com'],
  ['Manager', 'manager@procureflow.com'],
  ['Procurement Officer', 'procurement@procureflow.com'],
  ['Administrator', 'admin@procureflow.com'],
];

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(location.state?.from || '/dashboard', { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>ProcureFlow</h1>
        <p className="muted">Sign in to manage purchase requests and orders.</p>
        <Alert>{errorText(error)}</Alert>
        <Field label="Email" required>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required autoComplete="username" />
        </Field>
        <Field label="Password" required>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
        </Field>
        <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p style={{ textAlign: 'center' }}>
          <Link to="/forgot-password">Forgot your password?</Link>
        </p>
        <div className="demo-box">
          <strong>Demo accounts</strong> (password <code>Password@123</code>) — click to fill:
          {DEMO.map(([label, mail]) => (
            <button type="button" key={mail} onClick={() => { setEmail(mail); setPassword('Password@123'); }}>
              {label} · {mail}
            </button>
          ))}
        </div>
      </form>
    </div>
  );
}
