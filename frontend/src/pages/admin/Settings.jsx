import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { useToast } from '../../toast.jsx';
import { useFetch } from '../../hooks.js';
import { Alert, Card, Field, Loading, PageHeader, errorText } from '../../components/ui.jsx';

export default function Settings() {
  const toast = useToast();
  const { data, loading, error } = useFetch(() => api.get('/settings'), []);
  const [values, setValues] = useState({});
  const [saveError, setSaveError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (data) setValues(Object.fromEntries(data.map((s) => [s.key, s.value])));
  }, [data]);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setSaveError(null);
    try {
      const updated = await api.put('/settings', values);
      setValues(Object.fromEntries(updated.map((s) => [s.key, s.value])));
      toast.success('Settings saved');
    } catch (err) {
      setSaveError(err);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading />;
  return (
    <>
      <PageHeader title="Settings" subtitle="Application-wide configuration." />
      <Card>
        <form onSubmit={save} style={{ maxWidth: 560 }}>
          <Alert>{errorText(error) || errorText(saveError)}</Alert>
          {data?.map((s) => (
            <Field key={s.key} label={s.key} hint={s.description} error={saveError?.fieldErrors?.[s.key]}>
              <input value={values[s.key] ?? ''} onChange={(e) => setValues({ ...values, [s.key]: e.target.value })} required />
            </Field>
          ))}
          <div className="form-actions"><button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save settings'}</button></div>
        </form>
      </Card>
    </>
  );
}
