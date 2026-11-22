import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { CatalogoService } from '../catalogo/catalogo.service';
import { mensagemErro, ProblemaApi } from '../core/api-error';
import { Sidebar } from '../sidebar';
import { NovaLocacao } from './nova-locacao';

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
  valorTotal: number | null;
  codigo?: string;
  titulo?: string;
}

interface DetalheLocacao {
  id: string;
  itens: ItemLocacao[];
  encerrada: boolean;
}

type Operacao = 'devolucoes' | 'pagamentos';

@Component({
  selector: 'app-locacoes',
  imports: [Sidebar, DatePipe, NovaLocacao],
  templateUrl: './locacoes.html',
  styles: '.locacao-detalhe { margin-top: 1rem; }',
})
export class Locacoes {
  private readonly http = inject(HttpClient);
  private readonly catalogo = inject(CatalogoService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly criando = signal(false);
  protected readonly sucesso = signal('');
  protected readonly situacao = signal('ABERTA');
  protected readonly pagina = signal<PaginaLocacoes | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal('');
  protected readonly detalhe = signal<DetalheLocacao | null>(null);
  protected readonly selecionada = signal<string | null>(null);
  protected readonly lendoDetalhe = signal(false);
  protected readonly erroDetalhe = signal('');
  protected readonly erroOperacao = signal('');
  protected readonly confirmacao = signal<{ operacao: Operacao; item: ItemLocacao } | null>(null);
  protected readonly salvando = signal(false);
  protected readonly moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  private paginaSolicitada = 0;

  constructor() {
    this.carregar(0);
  }

  protected registrarAbertura() {
    this.criando.set(false);
    this.sucesso.set('Locação aberta com sucesso.');
    this.filtrar('ABERTA');
  }

  protected filtrar(situacao: string) {
    this.situacao.set(situacao);
    this.carregar(0);
  }

  protected carregar(page = this.paginaSolicitada, preservarDetalhe = false) {
    if (this.carregando() || this.salvando()) return;
    this.paginaSolicitada = page;
    if (!preservarDetalhe) {
      this.selecionada.set(null);
      this.detalhe.set(null);
      this.erroDetalhe.set('');
      this.erroOperacao.set('');
      this.confirmacao.set(null);
    }
    this.erro.set('');
    this.carregando.set(true);
    const situacao = this.situacao();
    const params: Record<string, string | number> = { page, size: 10 };
    if (situacao) params['situacao'] = situacao;
    const listar = (page: number) =>
      this.http.get<PaginaLocacoes>('/api/v1/locacoes', { params: { ...params, page } });
    listar(page)
      .pipe(
        switchMap((pagina) => {
          const ultima = Math.max(0, pagina.totalPages - 1);
          if (page <= ultima) return of(pagina);
          this.paginaSolicitada = ultima;
          return listar(ultima);
        }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (pagina) => {
          this.pagina.set(pagina);
        },
        error: (error: unknown) => this.erro.set(mensagemErro(error)),
      });
  }

  protected abrir(id: string) {
    if (this.lendoDetalhe() || this.salvando()) return;
    this.confirmacao.set(null);
    this.erroOperacao.set('');
    this.selecionada.set(id);
    this.detalhe.set(null);
    this.erroDetalhe.set('');
    this.lendoDetalhe.set(true);
    this.http
      .get<DetalheLocacao>(`/api/v1/locacoes/${encodeURIComponent(id)}`)
      .pipe(
        switchMap((detalhe) => {
          if (!detalhe.itens.length) return of(detalhe);
          return forkJoin(detalhe.itens.map((item) => this.identificarExemplar(item))).pipe(
            map((itens) => ({ ...detalhe, itens })),
          );
        }),
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

  private identificarExemplar(item: ItemLocacao) {
    return this.catalogo.exemplar(item.exemplarId).pipe(
      switchMap(({ codigo, livroId }) =>
        this.catalogo.livro(livroId).pipe(
          map(({ titulo }) => ({ ...item, codigo, titulo })),
          catchError(() => of({ ...item, codigo })),
        ),
      ),
      catchError(() => of(item)),
    );
  }

  protected preparar(operacao: Operacao, item: ItemLocacao) {
    this.sucesso.set('');
    this.erroOperacao.set('');
    this.confirmacao.set({ operacao, item });
  }

  protected confirmar() {
    const id = this.selecionada();
    const confirmacao = this.confirmacao();
    if (!id || !confirmacao || this.salvando() || this.carregando()) return;

    this.salvando.set(true);
    this.erroOperacao.set('');
    const { operacao, item } = confirmacao;
    this.http
      .post(`/api/v1/locacoes/${encodeURIComponent(id)}/${operacao}`, { itens: [item.id] })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.salvando.set(false)),
      )
      .subscribe({
        next: () => {
          this.salvando.set(false);
          this.sucesso.set(
            operacao === 'devolucoes' ? 'Devolução registrada.' : 'Quitação registrada.',
          );
          this.abrir(id);
          this.carregar(this.paginaSolicitada, true);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && [400, 404, 409].includes(error.status)) {
            const detalhe = (error.error as ProblemaApi | null)?.detail;
            if (detalhe) {
              this.erroOperacao.set(detalhe);
              return;
            }
            if (error.status !== 400) {
              this.erroOperacao.set('O item mudou ou não foi encontrado. Atualize os itens.');
              return;
            }
          }
          this.erroOperacao.set(mensagemErro(error));
        },
      });
  }
}
