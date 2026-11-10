import { FeeCatalogPage } from './pages/FeeCatalogPage/FeeCatalogPage';
import { useEffect, useState } from 'react';
import './styles/global.scss';
import { AppShell } from './components/AppShell/AppShell';
import { SESSION_EXPIRED_EVENT, getSession, logout } from './services/api';
import { LoginPage } from './pages/LoginPage/LoginPage';
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
  const [session, setSession] = useState(getSession());
  const [adminSection, setAdminSection] = useState<'resumo' | 'projetos' | 'taxas' | 'cotacoes' | 'pagamentos' | 'usuarios'>('resumo');
  const [projectView, setProjectView] = useState<AdminView>({ name: 'lista' });

  // Sessão expirada em qualquer chamada da API: volta para a tela de login.
  useEffect(() => {
    const onExpired = () => setSession(null);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  if (!session) {
    return <LoginPage onLoggedIn={() => setSession(getSession())} />;
  }

  const role = session.role;
  const projectId = session.projectId;

  async function handleLogout() {
    await logout();
    setSession(null);
    setAdminSection('resumo');
    setProjectView({ name: 'lista' });
  }

  return (
    <AppShell active={role === 'ADMIN' ? adminSection : 'projetos'} onNavigate={setAdminSection} onLogout={handleLogout}>
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
        adminSection === 'taxas' ? <FeeCatalogPage /> :
        adminSection === 'cotacoes' ? <QuoteFeesPage /> :
        adminSection === 'projetos' ? <AdminProjects view={projectView} setView={setProjectView} /> :
        <div style={{ padding: '4rem', textAlign: 'center', color: '#666' }}>
          <h2>Em breve</h2>
          <p>A seção de {adminSection} ainda não foi implementada.</p>
        </div>
      ) : projectId ? <ClientPdfPage projectId={projectId} /> : (
        <div>Nenhum projeto está vinculado ao seu acesso. Fale com o administrador.</div>
      )}
    </AppShell>
  );
}
