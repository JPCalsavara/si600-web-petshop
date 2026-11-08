import { lazy, Suspense } from 'react';
const PaymentTestPage = import.meta.env.DEV ? lazy(() => import('./pages/PaymentTestPage/PaymentTestPage')) : null;
import { FeeCatalogPage } from './pages/FeeCatalogPage/FeeCatalogPage';
import { useState } from 'react';
import './styles/global.scss';
import { AppShell } from './components/AppShell/AppShell';
import { getCurrentActorRole, getCurrentClientProjectId } from './services/api';
import { ClientPdfPage } from './pages/ClientPdfPage/ClientPdfPage';
import { AdminPdfPage } from './pages/AdminPdfPage/AdminPdfPage';
import { ProjectListPage } from './pages/ProjectListPage/ProjectListPage';
import { ProjectFormPage } from './pages/ProjectFormPage/ProjectFormPage';
import { QuoteFeesPage } from './pages/QuoteFeesPage/QuoteFeesPage';
import { AdminDashboardPage } from './pages/AdminDashboardPage/AdminDashboardPage';

type AdminView =
  | { name: 'lista' }
  | { name: 'novo' }
  | { name: 'analise-pdf' };

function AdminProjects({ view, setView }: { view: AdminView; setView: (v: AdminView) => void }) {
  const toList = () => setView({ name: 'lista' });

  switch (view.name) {
    case 'novo':
      return <ProjectFormPage onDone={toList} />;
    case 'analise-pdf':
      return <AdminPdfPage onBack={toList} />;
    default:
      return (
        <ProjectListPage
          onAdd={() => setView({ name: 'novo' })}
          onOpenPdfReview={() => setView({ name: 'analise-pdf' })}
        />
      );
  }
}

export default function App() {
  const role = getCurrentActorRole();
  const projectId = getCurrentClientProjectId();
  const [adminSection, setAdminSection] = useState<'resumo' | 'projetos' | 'taxas' | 'cotacoes' | 'pagamentos' | 'usuarios'>('resumo');
  const [projectView, setProjectView] = useState<AdminView>({ name: 'lista' });

  if (PaymentTestPage && window.location.pathname === '/dev/pagamentos') {
    return <Suspense fallback={<p>Carregando...</p>}><PaymentTestPage /></Suspense>;
  }
  return (
    <AppShell active={role === 'ADMIN' ? adminSection : 'projetos'} onNavigate={setAdminSection}>
      {role === 'ADMIN' ? (
        adminSection === 'resumo' ? (
          <AdminDashboardPage
            onOpenPdfReview={() => {
              setAdminSection('projetos');
              setProjectView({ name: 'analise-pdf' });
            }}
            onNavigateToProjects={() => {
              setAdminSection('projetos');
              setProjectView({ name: 'lista' });
            }}
            onNavigateToQuotes={() => {
              setAdminSection('cotacoes');
            }}
          />
        ) :
        adminSection === 'pagamentos' && PaymentTestPage ? <Suspense fallback={<p>Carregando...</p>}><PaymentTestPage /></Suspense> :
        adminSection === 'taxas' ? <FeeCatalogPage /> :
        adminSection === 'cotacoes' ? <QuoteFeesPage /> :
        adminSection === 'projetos' ? <AdminProjects view={projectView} setView={setProjectView} /> :
        <div style={{ padding: '4rem', textAlign: 'center', color: '#666' }}>
          <h2>Em breve</h2>
          <p>A seção de {adminSection} ainda não foi implementada.</p>
        </div>
      ) : projectId ? <ClientPdfPage projectId={projectId} /> : (
        <div>Configure <code>VITE_PROJECT_ID</code> para visualizar o projeto.</div>
      )}
    </AppShell>
  );
}
