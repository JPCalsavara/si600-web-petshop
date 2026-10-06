import { FeeCatalogPage } from './pages/FeeCatalogPage/FeeCatalogPage';
import { useState } from 'react';
import './styles/global.scss';
import { AppShell } from './components/AppShell/AppShell';
import { getCurrentActorRole } from './services/api';
import { ClientPdfPage } from './pages/ClientPdfPage/ClientPdfPage';
import { AdminPdfPage } from './pages/AdminPdfPage/AdminPdfPage';
import { ProjectListPage } from './pages/ProjectListPage/ProjectListPage';
import { ProjectFormPage } from './pages/ProjectFormPage/ProjectFormPage';
import type { ClientProject } from './types';

type AdminView =
  | { name: 'lista' }
  | { name: 'novo' }
  | { name: 'editar'; project: ClientProject }
  | { name: 'analise-pdf' };

function AdminProjects() {
  const [view, setView] = useState<AdminView>({ name: 'lista' });
  const toList = () => setView({ name: 'lista' });

  switch (view.name) {
    case 'novo':
      return <ProjectFormPage onDone={toList} />;
    case 'editar':
      return <ProjectFormPage project={view.project} onDone={toList} />;
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
          onEdit={(project) => setView({ name: 'editar', project })}
          onOpenPdfReview={() => setView({ name: 'analise-pdf' })}
        />
      );
  }
}

export default function App() {
  const role = getCurrentActorRole();
  const projectId = import.meta.env.VITE_PROJECT_ID;
  const [adminSection, setAdminSection] = useState<'resumo' | 'projetos' | 'taxas' | 'pagamentos' | 'usuarios'>('projetos');

  return (
    <AppShell active={role === 'ADMIN' ? adminSection : 'projetos'} onNavigate={setAdminSection}>
      {role === 'ADMIN' ? (
        adminSection === 'taxas' ? <FeeCatalogPage /> :
        adminSection === 'projetos' ? <AdminProjects /> :
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
