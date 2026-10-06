import { useState, type ReactNode } from 'react';
import { getCurrentActorRole } from '../../services/api';
import styles from './AppShell.module.scss';

interface Props { active: 'resumo' | 'projetos' | 'pagamentos' | 'usuarios' | 'taxas'; children: ReactNode; onNavigate?: (section: 'resumo' | 'projetos' | 'pagamentos' | 'usuarios' | 'taxas') => void; }

export function AppShell({ active, children, onNavigate }: Props) {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const role = getCurrentActorRole();
  const admin = role === 'ADMIN';
  const name = admin ? 'Rosângela' : 'Fabiana S. T.';
  const handle = admin ? '@adminRosângela' : '@User94';

  const handleNavigate = (section: any) => {
    if (onNavigate) onNavigate(section);
    setIsMobileMenuOpen(false); // fecha o menu no mobile ao clicar
  };

  return (
    <div className={styles.viewport}>
      <div className={styles.shell}>
        
        {/* Mobile Header (Hamburger + Brand) */}
        <header className={styles.mobileHeader}>
          <button className={styles.hamburger} onClick={() => setIsMobileMenuOpen(true)}>
            ☰
          </button>
          <div className={styles.brandMobile}><span>◉</span><strong>Gestão de Eventos</strong></div>
        </header>

        {/* Backdrop escuro pro mobile */}
        {isMobileMenuOpen && <div className={styles.mobileBackdrop} onClick={() => setIsMobileMenuOpen(false)}></div>}

        <aside className={`${styles.sidebar} ${isMobileMenuOpen ? styles.open : ''}`}>
          <div>
            <div className={styles.mobileCloseWrap}>
              <button className={styles.closeBtn} onClick={() => setIsMobileMenuOpen(false)}>×</button>
            </div>
            <div className={styles.profile}>
              <div className={styles.avatar}>{admin ? '⚙' : 'U'}</div>
              <div><strong>{name}</strong><span>{handle}</span></div>
            </div>
            <nav>
              {admin && <NavItem label="Resumo" active={active === 'resumo'} onClick={() => handleNavigate('resumo')} />}
              <NavItem label="Projetos" active={active === 'projetos'} onClick={admin ? () => handleNavigate('projetos') : undefined} />
              {admin && <NavItem label="Catálogo de Taxas" active={active === 'taxas'} onClick={() => handleNavigate('taxas')} />}
              {admin && <NavItem label="Pagamentos" active={active === 'pagamentos'} onClick={() => handleNavigate('pagamentos')} />}
              {admin && <NavItem label="Usuários" active={active === 'usuarios'} onClick={() => handleNavigate('usuarios')} />}
            </nav>
          </div>
          <button className={styles.logout} type="button">↪ <span>Sair</span></button>
        </aside>

        <section className={styles.contentArea}>
          <header className={styles.brandDesktop}><span>◉</span><strong>Gestão de Eventos</strong></header>
          <main className={styles.content}>{children}</main>
        </section>
      </div>
    </div>
  );
}

function NavItem({ label, active, onClick }: { label: string; active: boolean; onClick?: () => void }) {
  if (onClick) return <button type="button" onClick={onClick} aria-current={active ? 'page' : undefined} className={`${styles.navItem} ${active ? styles.active : ''}`}><span>{label}</span><span>›</span></button>;
  return <div className={`${styles.navItem} ${active ? styles.active : ''}`}><span>{label}</span><span>›</span></div>;
}
