import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { mensagemErro } from '../core/api-error';
import { Sidebar } from '../sidebar';

interface ResumoLocacao {
  id: string;
  clienteId: string;
  dataRetirada: string;
  encerrada: boolean;
  atrasada: boolean;
  pagamentoPendente: boolean;
}

interface PaginaLocacoes {
  items: ResumoLocacao[];
  page: number;
  totalElements: number;
  totalPages: number;
}

interface ItemLocacao {
  id: string;
  exemplarId: string;
  dataPrevistaDevolucao: string;
  dataDevolucao: string | null;
  dataPagamento: string | null;
}

interface DetalheLocacao {
  id: string;
  itens: ItemLocacao[];
}

@Component({
  selector: 'app-locacoes',
  imports: [Sidebar, DatePipe],
  templateUrl: './locacoes.html',
  styles: '.locacao-detalhe { margin-top: 1rem; }',
})
export class Locacoes {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly situacao = signal('ABERTA');
  protected readonly pagina = signal<PaginaLocacoes | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal('');
  protected readonly detalhe = signal<DetalheLocacao | null>(null);
  protected readonly selecionada = signal<string | null>(null);
  protected readonly lendoDetalhe = signal(false);
  protected readonly erroDetalhe = signal('');
  private paginaSolicitada = 0;

  constructor() {
    this.carregar(0);
  }

  protected filtrar(situacao: string) {
    this.situacao.set(situacao);
    this.carregar(0);
  }

  protected carregar(page = this.paginaSolicitada) {
    if (this.carregando() || this.lendoDetalhe()) return;
    this.paginaSolicitada = page;
    this.selecionada.set(null);
    this.detalhe.set(null);
    this.erroDetalhe.set('');
    this.erro.set('');
    this.carregando.set(true);
    const situacao = this.situacao();
    const params: Record<string, string | number> = { page, size: 10 };
    if (situacao) params['situacao'] = situacao;
    this.http
      .get<PaginaLocacoes>('/api/v1/locacoes', { params })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.pagina.set(pagina),
        error: (error: unknown) => this.erro.set(mensagemErro(error)),
      });
  }

  protected abrir(id: string) {
    if (this.lendoDetalhe()) return;
    this.selecionada.set(id);
    this.detalhe.set(null);
    this.erroDetalhe.set('');
    this.lendoDetalhe.set(true);
    this.http
      .get<DetalheLocacao>(`/api/v1/locacoes/${encodeURIComponent(id)}`)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.lendoDetalhe.set(false)),
      )
      .subscribe({
        next: (detalhe) => this.detalhe.set(detalhe),
        error: (error: unknown) =>
          this.erroDetalhe.set(
            error instanceof HttpErrorResponse && error.status === 404
              ? 'Locação não encontrada. Atualize a lista.'
              : mensagemErro(error),
          ),
      });
  }
}
