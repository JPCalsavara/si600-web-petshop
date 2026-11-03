import { FeeCatalogPage } from './pages/FeeCatalogPage/FeeCatalogPage';
import { useState } from 'react';
import './styles/global.scss';
import { AppShell } from './components/AppShell/AppShell';
import { getCurrentActorRole } from './services/api';
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
      return (
        <>
          <button type="button" onClick={toList}>← Voltar aos projetos</button>
          <AdminPdfPage />
        </>
      );
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
  const projectId = (typeof window !== 'undefined' ? localStorage.getItem('MOCK_PROJECT_ID') : null) || import.meta.env.VITE_PROJECT_ID;
  const [adminSection, setAdminSection] = useState<'resumo' | 'projetos' | 'taxas' | 'cotacoes' | 'pagamentos' | 'usuarios'>('resumo');
  const [projectView, setProjectView] = useState<AdminView>({ name: 'lista' });

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
          />
        ) :
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
