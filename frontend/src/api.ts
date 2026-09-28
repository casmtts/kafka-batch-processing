export type ImportStatus =
  | 'PROCESSANDO'
  | 'CONCLUIDA'
  | 'CONCLUIDA_COM_ERROS'
  | 'FALHA';

export type ImportJob = {
  id: string;
  status: ImportStatus;
  arquivo: string | null;
  totalLinhas: number;
  linhasProcessadas: number;
  importadas: number;
  rejeitadas: number;
  duplicadas: number;
  motivoFalha: string | null;
  criadoEm: string;
  finalizadoEm: string | null;
};

export type ImportPage = {
  pagina: number;
  tamanho: number;
  total: number;
  importacoes: ImportJob[];
};

export type ImportError = {
  linha: number;
  sku: string | null;
  resultado: 'REJEITADA' | 'DUPLICADA';
  motivo: string;
};

export type ErrorPage = {
  pagina: number;
  tamanho: number;
  total: number;
  erros: ImportError[];
};

type ApiError = { erro?: string };

async function readResponse<T>(response: Response): Promise<T> {
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const message = (body as ApiError | null)?.erro;
    throw new Error(message || `A solicitação falhou (${response.status}).`);
  }
  return body as T;
}

export async function listImports(page: number, size: number): Promise<ImportPage> {
  const response = await fetch(`/api/imports?page=${page}&size=${size}`);
  return readResponse<ImportPage>(response);
}

export async function listErrors(id: string, page: number, size: number): Promise<ErrorPage> {
  const response = await fetch(`/api/imports/${id}/errors?page=${page}&size=${size}`);
  return readResponse<ErrorPage>(response);
}

export async function uploadCsv(file: File): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch('/api/imports', { method: 'POST', body: form });
  await readResponse<unknown>(response);
}
