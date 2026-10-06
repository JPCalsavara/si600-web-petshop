import { useEffect, useState } from 'react';
import {
  getClientProject,
  getPdfDownloadUrl,
  submitProjectPdf,
} from '../../services/api';
import type { Project } from '../../types';
import styles from './ClientPdfPage.module.scss';
import { ProjectFees } from './ProjectFees';

interface Props {
  projectId: string;
}

const MAX_SIZE = 10 * 1024 * 1024;

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('pt-BR');
}

function formatBytes(value: number | null) {
  if (!value) return '-';
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
}

export function ClientPdfPage({ projectId }: Props) {
  const [project, setProject] = useState<Project | null>(null);
  const [area, setArea] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [fileInputKey, setFileInputKey] = useState(0);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      setProject(await getClientProject(projectId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível carregar o projeto.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [projectId]);

  function validateFile(selected: File | null) {
    if (!selected) return 'Selecione um arquivo PDF.';
    if (selected.type !== 'application/pdf') return 'O arquivo deve estar no formato PDF.';
    if (selected.size > MAX_SIZE) return 'O PDF deve ter no máximo 10 MB.';
    return null;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    const areaValue = Number(area);
    const fileError = validateFile(file);
    if (!areaValue || areaValue <= 0) {
      setError('Informe a metragem oficial do estande em m².');
      return;
    }
    if (fileError) {
      setError(fileError);
      return;
    }

    try {
      setSending(true);
      const updated = await submitProjectPdf(projectId, areaValue, file!);
      setProject(updated);
      setSuccess('PDF enviado com sucesso e encaminhado para análise.');
      setFile(null);
      setArea('');
      setFileInputKey((key) => key + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível enviar o PDF.');
    } finally {
      setSending(false);
    }
  }

  async function handleDownload() {
    try {
      setError(null);
      const result = await getPdfDownloadUrl(projectId);
      window.open(result.url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Não foi possível gerar o download.');
    }
  }

  if (loading) return <section className={styles.card}>Carregando dados do PDF...</section>;
  if (!project) {
    return (
      <section className={styles.card}>
        {error ? <div className={styles.alertDanger}>{error}</div> : 'Projeto não encontrado.'}
      </section>
    );
  }

  const canSubmit = project.status === 'AGUARDANDO_PDF' || project.status === 'REPROVADO';

  return (
    <main className={styles.page}>
      <section className={styles.header}>
        <div>
          <span className={styles.eyebrow}>PDF</span>
          <h1>Dados Gerais do PDF</h1>
          <p>{project.companyName} · {project.category}</p>
        </div>
        <span className={`${styles.status} ${styles[project.status]}`}>
          {project.status.replace(/_/g, ' ')}
        </span>
      </section>

      {error && <div className={styles.alertDanger}>{error}</div>}
      {success && <div className={styles.alertSuccess}>{success}</div>}

      {project.status === 'REPROVADO' && project.rejectionJustification && (
        <div className={styles.rejection}>
          <strong>Ajustes necessários</strong>
          <p>{project.rejectionJustification}</p>
        </div>
      )}

      <section className={styles.card}>
        <div className={styles.summaryGrid}>
          <div><span>Prazo limite</span><strong>{formatDate(project.pdfDeadline)}</strong></div>
          <div><span>Metragem informada</span><strong>{project.boothAreaM2 ? `${project.boothAreaM2} m²` : 'Não informada'}</strong></div>
          <div><span>Arquivo atual</span><strong>{project.pdfOriginalFilename || 'Nenhum arquivo'}</strong></div>
          <div><span>Tamanho</span><strong>{formatBytes(project.pdfSizeBytes)}</strong></div>
        </div>

        {project.pdfOriginalFilename && (
          <button className={styles.secondaryButton} onClick={handleDownload}>
            Baixar PDF enviado
          </button>
        )}
      </section>

      {canSubmit && (
        <form className={styles.card} onSubmit={handleSubmit}>
          <h2>{project.status === 'REPROVADO' ? 'Reenviar planta do estande' : 'Enviar planta do estande'}</h2>
          <div className={styles.formGrid}>
            <label>
              Metragem oficial do estande (m²) *
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={area}
                onChange={(event) => setArea(event.target.value)}
                placeholder="Ex.: 42,50"
              />
            </label>

            <label>
              Planta do estande (PDF, máximo 10 MB) *
              <input
                key={fileInputKey}
                type="file"
                accept="application/pdf,.pdf"
                onChange={(event) => {
                  const selected = event.target.files?.[0] || null;
                  setFile(selected);
                  setError(validateFile(selected));
                }}
              />
            </label>
          </div>

          <button className={styles.primaryButton} disabled={sending}>
            {sending ? 'Enviando...' : 'Enviar PDF para análise'}
          </button>
        </form>
      )}

      {!canSubmit && project.status === 'PDF_EM_ANALISE' && (
        <div className={styles.info}>Seu PDF está em análise. Aguarde a decisão do administrador.</div>
      )}
      {project.status === 'APROVADO' && (
        <ProjectFees projectId={projectId} />
      )}
    </main>
  );
}
