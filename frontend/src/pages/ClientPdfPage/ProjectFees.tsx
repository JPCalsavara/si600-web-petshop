import { useEffect, useState } from 'react';
import { getProjectFees } from '../../services/api';
import styles from './ProjectFees.module.scss';

interface Fee {
  feeId: string;
  name: string;
  type: string;
  calculatedValue: number | null;
  measurementUnit: string;
  status: string;
  paymentDeadline: string | null;
}

interface Props {
  projectId: string;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

function formatDate(value: string | null) {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('pt-BR');
}

export function ProjectFees({ projectId }: Props) {
  const [fees, setFees] = useState<Fee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const data = await getProjectFees(projectId);
        setFees(data);
      } catch (err: any) {
        setError(err.message || 'Erro ao carregar taxas.');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [projectId]);

  if (loading) return <div>Carregando taxas...</div>;
  if (error) return <div className="alert-danger">{error}</div>;
  if (fees.length === 0) return <div>Nenhuma taxa encontrada para este projeto.</div>;

  return (
    <section className={styles.container}>
      <h2 className={styles.title}>Taxas e Pagamentos</h2>
      <div className={styles.feeList}>
        {fees.map(fee => (
          <div key={fee.feeId} className={styles.feeItem}>
            <div className={styles.info}>
              <h3>{fee.name}</h3>
              <p>Tipo: {fee.type.replace(/_/g, ' ')}</p>
              <p>Status: {fee.status} | Prazo: {formatDate(fee.paymentDeadline)}</p>
            </div>
            
            <div className={styles.actions}>
              {fee.type === 'VARIAVEL' ? (
                <>
                  <input type="number" className={styles.variableInput} placeholder={`Qtd em ${fee.measurementUnit}`} />
                  <button className={styles.saveButton}>Salvar</button>
                </>
              ) : (
                <span className={styles.value}>
                  {fee.calculatedValue !== null ? formatCurrency(fee.calculatedValue) : 'A calcular'}
                </span>
              )}
              
              <button 
                className={styles.payButton} 
                disabled={fee.calculatedValue === null}
                onClick={() => alert('Geração de boleto/PIX será implementada em breve!')}
              >
                Gerar PIX/Boleto
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
