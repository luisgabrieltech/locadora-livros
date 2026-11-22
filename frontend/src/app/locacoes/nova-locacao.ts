import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, map } from 'rxjs';
import { CatalogoService } from '../catalogo/catalogo.service';
import { ClientesService } from '../clientes/clientes.service';
import { mensagemErro, ProblemaApi } from '../core/api-error';

interface Opcao {
  id: string;
  rotulo: string;
  disponivel?: boolean;
}

interface Item extends Opcao {
  prazo: string;
}

@Component({
  selector: 'app-nova-locacao',
  templateUrl: './nova-locacao.html',
  styles: [
    'ul { justify-content: flex-start; list-style: none; margin: 0; } fieldset { border: 0; padding: 0; margin: 0; } legend { padding: 0 1.5rem; }',
  ],
})
export class NovaLocacao {
  private readonly http = inject(HttpClient);
  private readonly clientes = inject(ClientesService);
  private readonly catalogo = inject(CatalogoService);
  private readonly destroyRef = inject(DestroyRef);
  readonly aberta = output<void>();
  readonly cancelada = output<void>();
  protected readonly cliente = signal<Opcao | null>(null);
  protected readonly itens = signal<Item[]>([]);
  protected readonly etapa = signal<'cliente' | 'exemplar'>('cliente');
  protected readonly opcoes = signal<Opcao[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly erroOpcoes = signal('');
  protected readonly erro = signal('');

  constructor() {
    this.carregar(0);
  }

  protected carregar(page: number) {
    if (this.carregando() || this.salvando()) return;
    this.pagina.set(page);
    this.carregando.set(true);
    this.erroOpcoes.set('');
    const request =
      this.etapa() === 'cliente'
        ? this.clientes.listar(page, 10).pipe(
            map((dados) => ({
              ...dados,
              items: dados.items.map(({ id, nome, telefone }): Opcao => ({
                id,
                rotulo: `${nome} (${telefone})`,
              })),
            })),
          )
        : this.catalogo.exemplares(page, 10).pipe(
            map((dados) => ({
              ...dados,
              items: dados.items.map(({ id, codigo, disponivel }): Opcao => ({
                id,
                rotulo: codigo,
                disponivel,
              })),
            })),
          );
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (dados) => {
          this.opcoes.set(dados.items);
          this.totalPaginas.set(dados.totalPages);
        },
        error: (error: unknown) => {
          this.opcoes.set([]);
          this.totalPaginas.set(0);
          this.erroOpcoes.set(mensagemErro(error));
        },
      });
  }

  protected escolher(opcao: Opcao) {
    if (this.etapa() === 'cliente') {
      this.cliente.set(opcao);
      this.etapa.set('exemplar');
      this.carregar(0);
    } else if (opcao.disponivel && !this.jaSelecionado(opcao.id)) {
      this.itens.update((itens) => [...itens, { ...opcao, prazo: '' }]);
    }
    this.erro.set('');
  }

  protected trocarCliente() {
    this.etapa.set('cliente');
    this.carregar(0);
  }

  protected jaSelecionado(id: string): boolean {
    return this.itens().some((item) => item.id === id);
  }

  protected definirPrazo(id: string, prazo: string) {
    this.itens.update((itens) => itens.map((item) => (item.id === id ? { ...item, prazo } : item)));
    this.erro.set('');
  }

  protected remover(id: string) {
    this.itens.update((itens) => itens.filter((item) => item.id !== id));
    this.erro.set('');
  }

  protected salvar() {
    if (this.salvando()) return;
    const cliente = this.cliente();
    const itens = this.itens();
    if (!cliente || !itens.length || itens.some((item) => !item.prazo)) {
      this.erro.set('Escolha um cliente e informe o prazo de cada exemplar selecionado.');
      return;
    }
    this.erro.set('');
    this.salvando.set(true);
    this.http
      .post<unknown>('/api/v1/locacoes', {
        clienteId: cliente.id,
        itens: itens.map(({ id, prazo }) => ({ exemplarId: id, dataPrevistaDevolucao: prazo })),
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.salvando.set(false)),
      )
      .subscribe({
        next: () => this.aberta.emit(),
        error: (error: unknown) => this.erro.set(this.mensagemAbertura(error)),
      });
  }

  private mensagemAbertura(error: unknown): string {
    if (error instanceof HttpErrorResponse && [400, 404, 409].includes(error.status)) {
      const problema = error.error as ProblemaApi | null;
      const erros = Object.values(problema?.errors ?? {});
      if (erros.length) return erros.join(' ');
      if (problema?.detail) return problema.detail;
      if (error.status === 404) return 'Cliente ou exemplar não encontrado.';
      if (error.status === 409) return 'O cliente está bloqueado ou um exemplar já foi locado.';
    }
    return mensagemErro(error);
  }
}
