import styles from './Header.module.scss';

interface HeaderProps {
  title?: string;
  subtitle?: string;
}

export const Header: React.FC<HeaderProps> = ({
  title = 'SI600 - Sistema de Eventos',
  subtitle = 'Gestão e Acompanhamento de Pagamento de Estandes',
}) => {
  return (
    <header className={styles.header}>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.subtitle}>{subtitle}</p>
    </header>
  );
};
