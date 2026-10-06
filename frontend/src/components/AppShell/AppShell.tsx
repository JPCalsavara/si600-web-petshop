import type { ReactNode } from 'react';
import { getCurrentActorRole } from '../../services/api';
import styles from './AppShell.module.scss';

interface Props { active: 'resumo' | 'projetos' | 'pagamentos' | 'usuarios' | 'taxas'; children: ReactNode; onNavigate?: (section: 'resumo' | 'projetos' | 'pagamentos' | 'usuarios' | 'taxas') => void; }

export function AppShell({ active, children, onNavigate }: Props) {
  const role = getCurrentActorRole();
  const admin = role === 'ADMIN';
  const name = admin ? 'Rosângela' : 'Fabiana S. T.';
  const handle = admin ? '@adminRosângela' : '@User94';

  return (
    <div className={styles.viewport}>
      <div className={styles.shell}>
        <aside className={styles.sidebar}>
          <div>
            <div className={styles.profile}>
              <div className={styles.avatar}>{admin ? '⚙' : 'U'}</div>
              <div><strong>{name}</strong><span>{handle}</span></div>
            </div>
            <nav>
              {admin && <NavItem label="Resumo" active={active === 'resumo'} onClick={onNavigate ? () => onNavigate('resumo') : undefined} />}
              <NavItem label="Projetos" active={active === 'projetos'} onClick={admin && onNavigate ? () => onNavigate('projetos') : undefined} />
              {admin && <NavItem label="Catálogo de Taxas" active={active === 'taxas'} onClick={onNavigate ? () => onNavigate('taxas') : undefined} />}
              {admin && <NavItem label="Pagamentos" active={active === 'pagamentos'} onClick={onNavigate ? () => onNavigate('pagamentos') : undefined} />}
              {admin && <NavItem label="Usuários" active={active === 'usuarios'} onClick={onNavigate ? () => onNavigate('usuarios') : undefined} />}
            </nav>
          </div>
          <button className={styles.logout} type="button">↪ <span>Sair</span></button>
        </aside>
        <section className={styles.contentArea}>
          <header className={styles.brand}><span>◉</span><strong>Gestão de Eventos</strong></header>
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
