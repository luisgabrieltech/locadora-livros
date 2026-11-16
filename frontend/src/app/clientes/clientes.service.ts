import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface ClienteResumo {
  id: string;
  nome: string;
  telefone: string;
  email: string | null;
}
export interface Cliente extends ClienteResumo {
  cpf: string;
}
export interface ClienteDados {
  nome: string;
  telefone: string;
  cpf: string;
  email: string | null;
}
export interface PaginaClientes {
  items: ClienteResumo[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);
  listar(page: number, size: number) {
    return this.http.get<PaginaClientes>('/api/v1/clientes', { params: { page, size } });
  }
  buscar(id: string) {
    return this.http.get<Cliente>(`/api/v1/clientes/${encodeURIComponent(id)}`);
  }
  criar(dados: ClienteDados) {
    return this.http.post<Cliente>('/api/v1/clientes', dados);
  }
  atualizar(id: string, dados: ClienteDados) {
    return this.http.put<Cliente>(`/api/v1/clientes/${encodeURIComponent(id)}`, dados);
  }
}
