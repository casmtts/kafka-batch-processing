import { useEffect, useRef, useState } from 'react';
import { listErrors, listImports, uploadCsv } from './api';
import type { ErrorPage, ImportJob, ImportPage, ImportStatus } from './api';

const HISTORY_SIZE = 10;
const ERROR_SIZE = 10;
const MAX_FILE_SIZE = 20 * 1024 * 1024;

type Notice = { name: string; message: string; kind: 'success' | 'error' };

const statusText: Record<ImportStatus, string> = {
  PROCESSANDO: 'Processando',
  CONCLUIDA: 'Concluída',
  CONCLUIDA_COM_ERROS: 'Concluída com erros',
  FALHA: 'Falha',
};

function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function progress(job: ImportJob): number {
  if (job.totalLinhas === 0) return job.status.startsWith('CONCLUIDA') ? 100 : 0;
  return Math.min(100, Math.round((job.linhasProcessadas / job.totalLinhas) * 100));
}

function messageFrom(error: unknown): string {
  return error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.';
}

export default function App() {
  const fileInput = useRef<HTMLInputElement>(null);
  const [queue, setQueue] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [activeFile, setActiveFile] = useState<string | null>(null);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [history, setHistory] = useState<ImportPage | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [errorPage, setErrorPage] = useState(0);
  const [errors, setErrors] = useState<ErrorPage | null>(null);
  const [detailsError, setDetailsError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const result = await listImports(historyPage, HISTORY_SIZE);
        if (!active) return;
        setHistory(result);
        setHistoryError(null);
      } catch (error) {
        if (active) setHistoryError(messageFrom(error));
      } finally {
        if (active) setHistoryLoading(false);
      }
    }
    setHistoryLoading(true);
    void refresh();
    const timer = window.setInterval(() => void refresh(), 4000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [historyPage, refreshKey]);

  useEffect(() => {
    if (!selectedId) {
      setErrors(null);
      setDetailsError(null);
      return;
    }
    let active = true;
    async function refresh() {
      try {
        const result = await listErrors(selectedId!, errorPage, ERROR_SIZE);
        if (!active) return;
        setErrors(result);
        setDetailsError(null);
      } catch (error) {
        if (active) setDetailsError(messageFrom(error));
      }
    }
    setErrors(null);
    void refresh();
    const timer = window.setInterval(() => void refresh(), 4000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [selectedId, errorPage, refreshKey]);

  const selected = history?.importacoes.find((job) => job.id === selectedId) ?? null;
  const pageCount = Math.max(1, Math.ceil((history?.total ?? 0) / HISTORY_SIZE));
  const errorPageCount = Math.max(1, Math.ceil((errors?.total ?? 0) / ERROR_SIZE));

  function addFiles(files: FileList | File[]) {
    const valid: File[] = [];
    const invalid: Notice[] = [];
    for (const file of Array.from(files)) {
      if (!file.name.toLowerCase().endsWith('.csv')) {
        invalid.push({ name: file.name, message: 'Selecione um arquivo .csv.', kind: 'error' });
      } else if (file.size > MAX_FILE_SIZE) {
        invalid.push({ name: file.name, message: 'O limite por arquivo é 20 MB.', kind: 'error' });
      } else {
        valid.push(file);
      }
    }
    setQueue((current) => {
      const keys = new Set(current.map((file) => `${file.name}:${file.size}:${file.lastModified}`));
      return [...current, ...valid.filter((file) => !keys.has(`${file.name}:${file.size}:${file.lastModified}`))];
    });
    if (invalid.length) setNotices((current) => [...invalid, ...current].slice(0, 6));
    if (fileInput.current) fileInput.current.value = '';
  }

  async function submitFiles() {
    if (!queue.length || uploading) return;
    setUploading(true);
    setNotices([]);
    for (const file of queue) {
      setActiveFile(file.name);
      try {
        await uploadCsv(file);
        setNotices((current) => [{
          name: file.name,
          message: 'Enviado para processamento.',
          kind: 'success' as const,
        }, ...current].slice(0, 6));
      } catch (error) {
        setNotices((current) => [{
          name: file.name,
          message: messageFrom(error),
          kind: 'error' as const,
        }, ...current].slice(0, 6));
      }
      setRefreshKey((value) => value + 1);
    }
    setQueue([]);
    setActiveFile(null);
    setUploading(false);
    setHistoryPage(0);
  }

  function changeHistoryPage(next: number) {
    setSelectedId(null);
    setErrorPage(0);
    setHistoryPage(next);
  }

  function selectJob(id: string) {
    setSelectedId((current) => current === id ? null : id);
    setErrorPage(0);
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark">KB</div>
          <div>
            <strong>Kafka Batch</strong>
            <span>Importação de produtos</span>
          </div>
        </div>
        <span className="environment"><span className="environment-dot" /> Ambiente local</span>
      </header>

      <main className="main-content">
        <div className="intro">
          <div className="eyebrow">PAINEL DE IMPORTAÇÕES</div>
          <h1>Seus arquivos, do envio ao resultado.</h1>
          <p>Envie planilhas CSV de produtos e acompanhe cada etapa do processamento.</p>
        </div>

        <section className="panel upload-panel" aria-labelledby="upload-title">
          <div className="panel-heading">
            <div>
              <h2 id="upload-title">Enviar arquivos</h2>
              <p>Um processamento é criado para cada CSV enviado.</p>
            </div>
            <a className="model-link" href="/exemplo-produtos.csv" download="exemplo-produtos.csv">
              Baixar CSV de exemplo <span aria-hidden="true">↓</span>
            </a>
          </div>

          <p className="sample-help">
            Baixe o exemplo, edite as linhas com seus produtos e envie o arquivo. Mantenha o cabeçalho
            <code>sku,nome,preco,estoque</code> e use ponto nos valores de preço.
          </p>

          <div
            className={`drop-zone${uploading ? ' drop-zone--disabled' : ''}`}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => {
              event.preventDefault();
              if (!uploading) addFiles(event.dataTransfer.files);
            }}
          >
            <input
              id="csv-files"
              ref={fileInput}
              type="file"
              accept=".csv,text/csv"
              multiple
              disabled={uploading}
              onChange={(event) => event.target.files && addFiles(event.target.files)}
            />
            <div className="upload-icon" aria-hidden="true">↑</div>
            <div className="drop-title">Arraste seus arquivos CSV para cá</div>
            <p>ou <label htmlFor="csv-files">escolha arquivos do computador</label></p>
            <small>Colunas: sku, nome, preco, estoque · Até 20 MB por arquivo</small>
          </div>

          {queue.length > 0 && (
            <div className="queue" aria-label="Arquivos selecionados">
              {queue.map((file) => (
                <div className="queue-item" key={`${file.name}:${file.size}:${file.lastModified}`}>
                  <div className="file-icon">CSV</div>
                  <div className="queue-name"><strong>{file.name}</strong><span>{(file.size / 1024).toFixed(1)} KB</span></div>
                  <button
                    type="button"
                    className="icon-button"
                    disabled={uploading}
                    aria-label={`Remover ${file.name}`}
                    onClick={() => setQueue((current) => current.filter((item) => item !== file))}
                  >×</button>
                </div>
              ))}
            </div>
          )}

          <div className="upload-actions">
            <span>{activeFile ? `Enviando ${activeFile}...` : `${queue.length} arquivo(s) selecionado(s)`}</span>
            <button className="primary-button" type="button" onClick={() => void submitFiles()} disabled={!queue.length || uploading}>
              {uploading ? 'Enviando...' : `Processar ${queue.length ? queue.length : ''} arquivo(s)`}
            </button>
          </div>

          {notices.length > 0 && (
            <div className="notices" aria-live="polite">
              {notices.map((notice, index) => (
                <div className={`notice notice--${notice.kind}`} key={`${notice.name}-${index}`}>
                  <strong>{notice.name}</strong><span>{notice.message}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="panel history-panel" aria-labelledby="history-title">
          <div className="panel-heading history-heading">
            <div>
              <h2 id="history-title">Histórico de arquivos</h2>
              <p>{history?.total ?? 0} importação(ões) registrada(s)</p>
            </div>
            <button className="secondary-button" type="button" onClick={() => setRefreshKey((value) => value + 1)}>
              Atualizar
            </button>
          </div>

          {historyError && <div className="inline-error" role="alert">{historyError}</div>}
          {historyLoading && !history && <div className="empty-state">Carregando histórico...</div>}
          {!historyLoading && history?.importacoes.length === 0 && (
            <div className="empty-state">
              <div className="empty-icon" aria-hidden="true">▤</div>
              <strong>Nenhum arquivo processado ainda</strong>
              <span>Envie um CSV acima para iniciar sua primeira importação.</span>
            </div>
          )}
          {history && history.importacoes.length > 0 && (
            <div className="table-scroll">
              <table className="history-table">
                <thead><tr><th>Arquivo</th><th>Enviado em</th><th>Status</th><th>Progresso</th><th>Resultados</th><th><span className="sr-only">Ações</span></th></tr></thead>
                <tbody>
                  {history.importacoes.map((job) => (
                    <tr key={job.id} className={selectedId === job.id ? 'selected-row' : ''}>
                      <td data-label="Arquivo"><div className="table-file"><span className="small-file-icon">CSV</span><strong>{job.arquivo || 'Arquivo sem nome'}</strong></div></td>
                      <td data-label="Enviado em" className="muted-cell">{formatDate(job.criadoEm)}</td>
                      <td data-label="Status"><span className={`status-badge status-badge--${job.status.toLowerCase()}`}><span className="status-dot" />{statusText[job.status]}</span></td>
                      <td data-label="Progresso"><div className="progress-cell"><div className="progress-track"><span style={{ width: `${progress(job)}%` }} /></div><small>{job.linhasProcessadas}/{job.totalLinhas} linhas</small></div></td>
                      <td data-label="Resultados" className={`result-cell result-cell--${job.status.toLowerCase()}`}>
                        <span className="result-imported"><strong>{job.importadas}</strong> importados</span>
                        <span className="result-issues">· {job.rejeitadas + job.duplicadas} erros</span>
                      </td>
                      <td><button type="button" className="text-button" onClick={() => selectJob(job.id)} aria-expanded={selectedId === job.id}>{selectedId === job.id ? 'Fechar' : 'Detalhes'}</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {history && history.total > HISTORY_SIZE && (
            <div className="pagination">
              <span>Página {historyPage + 1} de {pageCount}</span>
              <div>
                <button type="button" disabled={historyPage === 0} onClick={() => changeHistoryPage(historyPage - 1)}>Anterior</button>
                <button type="button" disabled={historyPage + 1 >= pageCount} onClick={() => changeHistoryPage(historyPage + 1)}>Próxima</button>
              </div>
            </div>
          )}
        </section>

        {selected && (
          <section className="panel detail-panel" aria-labelledby="details-title">
            <div className="panel-heading">
              <div>
                <div className="eyebrow">DETALHES DA IMPORTAÇÃO</div>
                <h2 id="details-title">{selected.arquivo || 'Arquivo sem nome'}</h2>
                <p>Iniciada em {formatDate(selected.criadoEm)} · ID {selected.id}</p>
              </div>
              <span className={`status-badge status-badge--${selected.status.toLowerCase()}`}><span className="status-dot" />{statusText[selected.status]}</span>
            </div>
            <div className="metrics">
              <div><strong>{selected.totalLinhas}</strong><span>Linhas no arquivo</span></div>
              <div><strong>{selected.importadas}</strong><span>Importadas</span></div>
              <div><strong>{selected.rejeitadas}</strong><span>Rejeitadas</span></div>
              <div><strong>{selected.duplicadas}</strong><span>Duplicadas</span></div>
            </div>
            {selected.motivoFalha && <div className="inline-error" role="alert">{selected.motivoFalha}</div>}
            <div className="detail-subheading"><h3>Linhas com problemas</h3><span>{errors?.total ?? 0} ocorrência(s)</span></div>
            {detailsError && <div className="inline-error" role="alert">{detailsError}</div>}
            {!errors && !detailsError && <p className="detail-empty">Carregando linhas...</p>}
            {errors?.erros.length === 0 && <p className="detail-empty">Nenhuma linha rejeitada ou duplicada.</p>}
            {errors && errors.erros.length > 0 && (
              <div className="table-scroll">
                <table className="errors-table">
                  <thead><tr><th>Linha</th><th>SKU</th><th>Resultado</th><th>Motivo</th></tr></thead>
                  <tbody>{errors.erros.map((error) => <tr key={error.linha}><td data-label="Linha">{error.linha}</td><td data-label="SKU">{error.sku || '—'}</td><td data-label="Resultado">{error.resultado === 'DUPLICADA' ? 'Duplicada' : 'Rejeitada'}</td><td data-label="Motivo">{error.motivo}</td></tr>)}</tbody>
                </table>
              </div>
            )}
            {errors && errors.total > ERROR_SIZE && (
              <div className="pagination">
                <span>Página {errorPage + 1} de {errorPageCount}</span>
                <div>
                  <button type="button" disabled={errorPage === 0} onClick={() => setErrorPage(errorPage - 1)}>Anterior</button>
                  <button type="button" disabled={errorPage + 1 >= errorPageCount} onClick={() => setErrorPage(errorPage + 1)}>Próxima</button>
                </div>
              </div>
            )}
          </section>
        )}
      </main>
      <footer className="footer">Kafka Batch · Processamento de produtos em lotes</footer>
    </div>
  );
}
