import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api.js';
import { useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { formatDateTime } from '../../utils.js';

const notify = () => window.dispatchEvent(new Event('pf:notifications-changed'));

export default function Notifications() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, loading, error, reload } = useFetch(
    () => api.get('/notifications', { page, size: 15, unreadOnly }),
    [page, unreadOnly],
  );

  async function open(n) {
    if (!n.read) {
      await api.post(`/notifications/${n.id}/read`).catch(() => {});
      notify();
    }
    if (n.link) navigate(n.link);
    else reload();
  }

  async function markAll() {
    await api.post('/notifications/read-all');
    notify();
    reload();
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        actions={<button className="btn" onClick={markAll}>Mark all as read</button>}
      />
      <Card
        actions={
          <label className="row">
            <input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
            Unread only
          </label>
        }
      >
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : null}
        {data && data.content.length === 0 && <EmptyState>You have no notifications.</EmptyState>}
        {data?.content.map((n) => (
          <div key={n.id} className={`notification ${n.read ? '' : 'unread'}`}>
            <div className="grow">
              <strong>{n.title}</strong>
              <div>{n.message}</div>
              <small className="muted">{formatDateTime(n.createdAt)}</small>
            </div>
            <button className="btn btn-sm" onClick={() => open(n)}>{n.link ? 'Open' : n.read ? 'Read' : 'Mark read'}</button>
          </div>
        ))}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
