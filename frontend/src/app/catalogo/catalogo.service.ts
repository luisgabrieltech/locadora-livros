import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface Pagina<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LivroDados {
  titulo: string;
  autor: string;
  isbn: string | null;
}

export interface Livro extends LivroDados {
  id: string;
}

export interface ExemplarDados {
  livroId: string;
  codigo: string;
}

export interface Exemplar extends ExemplarDados {
  id: string;
  disponivel: boolean;
}

@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly http = inject(HttpClient);

  livros(page: number, size: number) {
    return this.http.get<Pagina<Livro>>('/api/v1/livros', { params: { page, size } });
  }

  exemplares(page: number, size: number) {
    return this.http.get<Pagina<Exemplar>>('/api/v1/exemplares', { params: { page, size } });
  }

  exemplar(id: string) {
    return this.http.get<Exemplar>(`/api/v1/exemplares/${encodeURIComponent(id)}`);
  }

  livro(id: string) {
    return this.http.get<Livro>(`/api/v1/livros/${encodeURIComponent(id)}`);
  }

  salvarLivro(id: string | null, dados: LivroDados) {
    return id
      ? this.http.put<Livro>(`/api/v1/livros/${encodeURIComponent(id)}`, dados)
      : this.http.post<Livro>('/api/v1/livros', dados);
  }

  salvarExemplar(id: string | null, dados: ExemplarDados) {
    return id
      ? this.http.put<Exemplar>(`/api/v1/exemplares/${encodeURIComponent(id)}`, dados)
      : this.http.post<Exemplar>('/api/v1/exemplares', dados);
  }
}
