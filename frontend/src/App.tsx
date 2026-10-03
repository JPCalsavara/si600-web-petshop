import { useState } from 'react';
import './styles/global.scss';
import './App.scss';

// Tipagens baseadas nos Critérios de Aceite
type AreaEstande = 'B2C' | 'Music Hub' | 'Music Sport' | 'Internacional';
type StatusProjeto = 'Aguardando PDF' | 'Aguardando Pagamento' | 'Concluído';

interface Taxa {
  id: string;
  nome: string;
  valor: number;
}

interface Projeto {
  id: string;
  nome: string;
  documento: string; // CPF/CNPJ
  endereco: string;
  area: AreaEstande;
  email: string;
  dataLimitePdf: string;
  dataLimitePagamento: string;
  taxasSelecionadas: string[];
  status: StatusProjeto;
}

// Catálogo de taxas mockado (simulando retorno do backend)
const CATALOGO_TAXAS: Taxa[] = [
  { id: 't1', nome: 'TFE', valor: 0 },
  { id: 't2', nome: 'Energia Eletrica Medida por KVA', valor: 0 },
  { id: 't3', nome: 'Taxa de Limpeza', valor: 0 },
  { id: 't4', nome: 'Extintor de Incêndio', valor: 0 },
  { id: 't5', nome: 'Taxa de Emissão', valor: 0 },
  { id: 't6', nome: 'Credencial Extra', valor: 0 }
];

export default function App() {
  const [abaAtiva, setAbaAtiva] = useState<'formulario' | 'lista'>('formulario');
  const [projetos, setProjetos] = useState<Projeto[]>([]);
  
  // Estados do Formulário
  const [nome, setNome] = useState('');
  const [documento, setDocumento] = useState('');
  const [endereco, setEndereco] = useState('');
  const [area, setArea] = useState<AreaEstande>('B2C');
  const [email, setEmail] = useState('');
  const [dataLimitePdf, setDataLimitePdf] = useState('');
  const [dataLimitePagamento, setDataLimitePagamento] = useState('');
  const [taxasSelecionadas, setTaxasSelecionadas] = useState<string[]>([]);
  
  // Feedback visual
  const [senhaTemporaria, setSenhaTemporaria] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  
  // Filtro da lista
  const [filtroStatus, setFiltroStatus] = useState<StatusProjeto | 'Todos'>('Todos');

  const handleToggleTaxa = (id: string) => {
    setTaxasSelecionadas(prev => 
      prev.includes(id) ? prev.filter(t => t !== id) : [...prev, id]
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);
    setSenhaTemporaria(null);

    // Validação de datas (Pagamento não pode ser anterior ao PDF)
    if (new Date(dataLimitePagamento) < new Date(dataLimitePdf)) {
      setErro('A data limite de pagamento não pode ser anterior à data de envio do PDF.');
      return;
    }

    // Validação de unicidade (E-mail e Documento)
    const documentoExiste = projetos.some(p => p.documento === documento);
    const emailExiste = projetos.some(p => p.email === email);
    
    if (documentoExiste || emailExiste) {
      setErro('Já existe um projeto cadastrado com este E-mail ou CPF/CNPJ.');
      return;
    }

    const novoProjeto: Projeto = {
      id: crypto.randomUUID(),
      nome,
      documento,
      endereco,
      area,
      email,
      dataLimitePdf,
      dataLimitePagamento,
      taxasSelecionadas,
      status: 'Aguardando PDF' // O Projeto novo nasce como "Aguardando PDF"
    };

    setProjetos([...projetos, novoProjeto]);
    
    // Gera senha temporária (O sistema cria o acesso e mostra a senha)
    const senhaGerada = Math.random().toString(36).slice(-8).toUpperCase();
    setSenhaTemporaria(senhaGerada);
    
    // Limpa os campos principais após sucesso
    setNome('');
    setDocumento('');
    setEndereco('');
    setEmail('');
  };

  const projetosFiltrados = projetos.filter(p => 
    filtroStatus === 'Todos' ? true : p.status === filtroStatus
  );

  return (
    <main className="app-container">
      <header className="page-header">
        <h1>Gestão de Projetos de Clientes</h1>
        <div className="tabs">
          <button 
            className={abaAtiva === 'formulario' ? 'active' : ''} 
            onClick={() => setAbaAtiva('formulario')}
          >
            Novo Projeto
          </button>
          <button 
            className={abaAtiva === 'lista' ? 'active' : ''} 
            onClick={() => setAbaAtiva('lista')}
          >
            Lista de Projetos ({projetos.length})
          </button>
        </div>
      </header>

      <div className="content-area">
        {abaAtiva === 'formulario' ? (
          <form className="project-form card-surface" onSubmit={handleSubmit}>
            <h2>Criar Novo Projeto</h2>
            
            {erro && <div className="alert-danger">{erro}</div>}
            {senhaTemporaria && (
              <div className="alert-success">
                Projeto e acesso criados com sucesso! <br/>
                <strong>Senha temporária para o cliente:</strong> <code>{senhaTemporaria}</code>
              </div>
            )}

            <div className="form-grid">
              <div className="form-group">
                <label>Nome / Razão Social *</label>
                <input required value={nome} onChange={e => setNome(e.target.value)} />
              </div>
              
              <div className="form-group">
                <label>CPF / CNPJ *</label>
                <input required value={documento} onChange={e => setDocumento(e.target.value)} />
              </div>

              <div className="form-group">
                <label>E-mail (Login) *</label>
                <input required type="email" value={email} onChange={e => setEmail(e.target.value)} />
              </div>

              <div className="form-group">
                <label>Área do Estande *</label>
                <select value={area} onChange={e => setArea(e.target.value as AreaEstande)}>
                  <option value="B2C">B2C</option>
                  <option value="Music Hub">Music Hub</option>
                  <option value="Music Sport">Music Sport</option>
                  <option value="Internacional">Internacional</option>
                </select>
              </div>

              <div className="form-group full-width">
                <label>Endereço Completo *</label>
                <input required value={endereco} onChange={e => setEndereco(e.target.value)} />
              </div>

              <div className="form-group">
                <label>Data Limite do PDF *</label>
                <input required type="date" value={dataLimitePdf} onChange={e => setDataLimitePdf(e.target.value)} />
              </div>

              <div className="form-group">
                <label>Data Limite de Pagamento *</label>
                <input required type="date" value={dataLimitePagamento} onChange={e => setDataLimitePagamento(e.target.value)} />
              </div>
            </div>

            <div className="fees-section">
              <h3>Taxas Aplicáveis do Catálogo</h3>
              <div className="checkbox-grid">
                {CATALOGO_TAXAS.map(taxa => (
                  <label key={taxa.id} className="checkbox-label">
                    <input 
                      type="checkbox" 
                      checked={taxasSelecionadas.includes(taxa.id)}
                      onChange={() => handleToggleTaxa(taxa.id)}
                    />
                    {taxa.nome} - R$ {taxa.valor.toFixed(2)}
                  </label>
                ))}
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary">Criar Projeto e Gerar Acesso</button>
            </div>
          </form>
        ) : (
          <div className="project-list card-surface">
            <div className="list-header">
              <h2>Projetos Cadastrados</h2>
              <select 
                value={filtroStatus} 
                onChange={e => setFiltroStatus(e.target.value as any)}
                className="status-filter"
              >
                <option value="Todos">Todos os Status</option>
                <option value="Aguardando PDF">Aguardando PDF</option>
                <option value="Aguardando Pagamento">Aguardando Pagamento</option>
                <option value="Concluído">Concluído</option>
              </select>
            </div>

            {projetosFiltrados.length === 0 ? (
              <p className="empty-state">Nenhum projeto encontrado para este filtro.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Nome do Cliente</th>
                    <th>Área</th>
                    <th>Prazo PDF</th>
                    <th>Prazo Pagamento</th>
                    <th>Status</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {projetosFiltrados.map(projeto => (
                    <tr key={projeto.id}>
                      <td>
                        <strong>{projeto.nome}</strong><br/>
                        <small>{projeto.documento}</small>
                      </td>
                      <td>{projeto.area}</td>
                      <td>{new Date(projeto.dataLimitePdf).toLocaleDateString('pt-BR')}</td>
                      <td>{new Date(projeto.dataLimitePagamento).toLocaleDateString('pt-BR')}</td>
                      <td>
                        <span className={`badge badge-${projeto.status.replace(/\s+/g, '-').toLowerCase()}`}>
                          {projeto.status}
                        </span>
                      </td>
                      <td>
                        <button className="btn-secondary btn-sm">Editar</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </main>
  );
}