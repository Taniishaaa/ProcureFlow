import { Link } from 'react-router-dom';
import { Card, PageHeader } from '../../components/ui.jsx';

export function AccessDenied() {
  return (
    <>
      <PageHeader title="Access denied" />
      <Card>
        <p>Your role does not have permission to view this page.</p>
        <Link className="btn" to="/dashboard">Back to dashboard</Link>
      </Card>
    </>
  );
}

export function NotFound() {
  return (
    <>
      <PageHeader title="Page not found" />
      <Card>
        <p>The page you are looking for does not exist.</p>
        <Link className="btn" to="/dashboard">Back to dashboard</Link>
      </Card>
    </>
  );
}
