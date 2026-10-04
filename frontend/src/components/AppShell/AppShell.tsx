import type { ReactNode } from 'react';
import { getCurrentActorRole } from '../../services/api';
import styles from './AppShell.module.scss';

interface Props { active: 'resumo' | 'projetos' | 'pagamentos' | 'usuarios'; children: ReactNode; }

export function AppShell({ active, children }: Props) {
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
              {admin && <NavItem label="Resumo" active={active === 'resumo'} />}
              <NavItem label="Projetos" active={active === 'projetos'} />
              {admin && <NavItem label="Pagamentos" active={active === 'pagamentos'} />}
              {admin && <NavItem label="Usuários" active={active === 'usuarios'} />}
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

function NavItem({ label, active }: { label: string; active: boolean }) {
  return <div className={`${styles.navItem} ${active ? styles.active : ''}`}><span>{label}</span><span>›</span></div>;
}
