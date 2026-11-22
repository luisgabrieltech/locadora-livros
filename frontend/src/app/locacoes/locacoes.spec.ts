import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Locacoes } from './locacoes';

describe('Consultas de locações', () => {
  it('consulta, devolve e quita o item preservando o detalhe quando o filtro deixa de incluí-lo', () => {
    TestBed.configureTestingModule({
      imports: [Locacoes],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuario: () => 'teste' } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Locacoes);
    const root: HTMLElement = fixture.nativeElement;
    fixture.detectChanges();

    const vazia = { items: [], page: 0, totalElements: 0, totalPages: 0 };
    http.expectOne('/api/v1/locacoes?page=0&size=10&situacao=ABERTA').flush(vazia);
    fixture.detectChanges();
    expect(root.textContent).toContain('Nenhuma locação nesta situação.');

    const filtro = root.querySelector<HTMLSelectElement>('#situacao')!;
    filtro.value = 'ATRASADA';
    filtro.dispatchEvent(new Event('change'));
    const locacao = {
      id: 'locacao-a',
      clienteId: 'cliente-a',
      dataRetirada: '2026-11-20',
      encerrada: false,
      atrasada: true,
      pagamentoPendente: true,
    };
    http.expectOne('/api/v1/locacoes?page=0&size=10&situacao=ATRASADA').flush({
      items: [locacao],
      page: 0,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    expect(root.querySelector('tbody')?.textContent).toContain('Atrasada');
    expect(root.querySelector('tbody')?.textContent).toContain('Pagamento pendente');

    root.querySelector<HTMLButtonElement>('tbody button')!.click();
    const exemplar = {
      id: 'item-a',
      exemplarId: 'exemplar-a',
      dataPrevistaDevolucao: '2026-11-21',
      dataDevolucao: null,
      dataPagamento: null,
      valorTotal: null,
    };
    const detalhe = { id: locacao.id, encerrada: false, itens: [exemplar] };
    const identificarExemplar = () => {
      http.expectOne('/api/v1/exemplares/exemplar-a').flush({
        id: exemplar.exemplarId,
        livroId: 'livro-a',
        codigo: 'EX-A',
      });
      http.expectOne('/api/v1/livros/livro-a').flush({ id: 'livro-a', titulo: 'Livro de teste' });
    };
    http.expectOne('/api/v1/locacoes/locacao-a').flush(detalhe);
    identificarExemplar();
    fixture.detectChanges();
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('EX-A');
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('Livro de teste');
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('21/11/2026');
    const clicar = (rotulo: string) => {
      const botao = [...root.querySelectorAll('button')].find(
        (item) => item.textContent?.trim() === rotulo,
      );
      expect(botao).toBeDefined();
      botao!.click();
      fixture.detectChanges();
    };
    clicar('Devolver');
    clicar('Confirmar devolução');
    const devolucao = http.expectOne({
      method: 'POST',
      url: '/api/v1/locacoes/locacao-a/devolucoes',
    });
    expect(devolucao.request.body).toEqual({ itens: ['item-a'] });
    devolucao.flush({});
    const devolvido = { ...exemplar, dataDevolucao: '2026-11-22', valorTotal: 4.5 };
    http.expectOne('/api/v1/locacoes/locacao-a').flush({ ...detalhe, itens: [devolvido] });
    identificarExemplar();
    http.expectOne('/api/v1/locacoes?page=0&size=10&situacao=ATRASADA').flush(vazia);
    fixture.detectChanges();
    expect(root.textContent).toContain('Nenhuma locação nesta situação.');
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('4,50');

    clicar('Quitar');
    clicar('Confirmar quitação');
    const pagamento = http.expectOne({
      method: 'POST',
      url: '/api/v1/locacoes/locacao-a/pagamentos',
    });
    expect(pagamento.request.body).toEqual({ itens: ['item-a'] });
    pagamento.flush({});
    http.expectOne('/api/v1/locacoes/locacao-a').flush({
      ...detalhe,
      encerrada: true,
      itens: [{ ...devolvido, dataPagamento: '2026-11-22' }],
    });
    identificarExemplar();
    http.expectOne('/api/v1/locacoes?page=0&size=10&situacao=ATRASADA').flush(vazia);
    fixture.detectChanges();
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('Locação encerrada');
    expect(root.querySelector('button[aria-label="Quitar exemplar EX-A"]')).toBeNull();
    http.verify();
  });
});
