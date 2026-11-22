import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Locacoes } from './locacoes';

describe('Consultas de locações', () => {
  it('filtra na API, mostra as duas pendências e consulta os itens da locação', () => {
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
    http.expectOne('/api/v1/locacoes/locacao-a').flush({
      id: locacao.id,
      itens: [
        {
          id: 'item-a',
          exemplarId: 'exemplar-a',
          dataPrevistaDevolucao: '2026-11-21',
          dataDevolucao: null,
          dataPagamento: null,
        },
      ],
    });
    fixture.detectChanges();
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('exemplar-a');
    expect(root.querySelector('.locacao-detalhe')?.textContent).toContain('21/11/2026');
    http.verify();
  });
});
