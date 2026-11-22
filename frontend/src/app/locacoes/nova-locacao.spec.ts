import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NovaLocacao } from './nova-locacao';

describe('Abertura de locação', () => {
  it('envia cada prazo, preserva o formulário em conflito e evita envio duplicado', () => {
    TestBed.configureTestingModule({
      imports: [NovaLocacao],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(NovaLocacao);
    const http = TestBed.inject(HttpTestingController);
    const root: HTMLElement = fixture.nativeElement;
    const pagina = { page: 0, size: 10, totalPages: 1 };
    const botao = (rotulo: string) =>
      [...root.querySelectorAll('button')].find((item) => item.textContent?.includes(rotulo))!;
    const escolher = (rotulo: string) => {
      botao(rotulo).click();
      fixture.detectChanges();
    };
    fixture.detectChanges();
    http.expectOne('/api/v1/clientes?page=0&size=10').flush({
      ...pagina,
      items: [{ id: 'cliente-a', nome: 'Ana', telefone: '11900000000' }],
    });
    fixture.detectChanges();
    escolher('Ana');
    http.expectOne('/api/v1/exemplares?page=0&size=10').flush({
      ...pagina,
      items: [
        { id: 'ex-a', codigo: 'EX-A', disponivel: true },
        { id: 'ex-b', codigo: 'EX-B', disponivel: true },
        { id: 'ex-c', codigo: 'EX-C', disponivel: false },
      ],
    });
    fixture.detectChanges();
    expect(botao('EX-C').disabled).toBe(true);
    escolher('EX-A');
    expect(botao('EX-A').disabled).toBe(true);
    escolher('EX-B');
    escolher('Abrir locação');
    http.expectNone('/api/v1/locacoes');

    for (const [id, prazo] of [
      ['ex-a', '2026-12-01'],
      ['ex-b', '2026-12-02'],
    ]) {
      const campo = root.querySelector<HTMLInputElement>('#prazo-' + id)!;
      campo.value = prazo;
      campo.dispatchEvent(new Event('input'));
    }
    const abrir = botao('Abrir locação');
    abrir.click();
    abrir.click();
    const request = http.expectOne({ method: 'POST', url: '/api/v1/locacoes' });
    expect(request.request.body).toEqual({
      clienteId: 'cliente-a',
      itens: [
        { exemplarId: 'ex-a', dataPrevistaDevolucao: '2026-12-01' },
        { exemplarId: 'ex-b', dataPrevistaDevolucao: '2026-12-02' },
      ],
    });
    request.flush(
      { detail: 'Cliente com pagamento pendente.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'Cliente com pagamento pendente.',
    );
    expect(root.querySelector<HTMLInputElement>('#prazo-ex-a')?.value).toBe('2026-12-01');
    http.verify();
  });
});
