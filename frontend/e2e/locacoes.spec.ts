import { APIRequestContext, expect, Page, test } from '@playwright/test';

async function prepararDados(request: APIRequestContext) {
  const headers: Record<string, string> = {
    Authorization: `Basic ${Buffer.from('operador_e2e:OperadorFicticioE2E').toString('base64')}`,
    'X-Requested-With': 'XMLHttpRequest',
  };
  const csrf = await request.get('/api/v1/csrf', { headers });
  await expect(csrf).toBeOK();
  const { headerName, token } = await csrf.json();
  headers[headerName] = token;

  async function criar(path: string, data: object) {
    const response = await request.post('/api/v1/' + path, { data, headers });
    expect(response.status()).toBe(201);
    return response.json();
  }

  await criar('clientes', {
    nome: 'Cliente fictício E2E',
    cpf: '00000000191',
    telefone: '11999990000',
  });
  const livro = await criar('livros', { titulo: 'Livro fictício E2E', autor: 'Autora fictícia' });
  const copiaA = await criar('exemplares', { livroId: livro.id, codigo: 'E2E-01' });
  const copiaB = await criar('exemplares', { livroId: livro.id, codigo: 'E2E-02' });

  async function disponivel(id: string) {
    const response = await request.get('/api/v1/exemplares/' + id, { headers });
    await expect(response).toBeOK();
    return (await response.json()).disponivel;
  }

  return { copiaA, copiaB, disponivel };
}

async function clicarEConfirmarPost(page: Page, name: string, endpoint: string, status: number) {
  const [response] = await Promise.all([
    page.waitForResponse(
      (res) => new URL(res.url()).pathname.endsWith(endpoint) && res.request().method() === 'POST',
    ),
    page.getByRole('button', { name, exact: true }).click(),
  ]);
  expect(response.status()).toBe(status);
  return response.json();
}

test('abre uma locação com duas cópias e só encerra após a última quitação', async ({
  page,
  request,
}) => {
  const { copiaA, copiaB, disponivel } = await prepararDados(request);
  const prazo = new Date(Date.now() + 7 * 86_400_000).toISOString().slice(0, 10);
  const botao = (name: string) => page.getByRole('button', { name, exact: true });

  await page.goto('/entrar');
  await page.getByLabel('Usuário').fill('operador_e2e');
  await page.getByLabel('Senha').fill('OperadorFicticioE2E');
  await botao('Entrar na locadora').click();
  await page.getByRole('link', { name: 'Locações' }).click();
  await botao('Nova locação').click();
  await page.getByRole('button', { name: /Cliente fictício E2E/ }).click();
  await botao('E2E-01').click();
  await botao('E2E-02').click();
  await page.getByLabel('Prazo de E2E-01').fill(prazo);
  await page.getByLabel('Prazo de E2E-02').fill(prazo);

  const locacao = await clicarEConfirmarPost(page, 'Abrir locação', '/api/v1/locacoes', 201);
  expect(locacao.itens).toHaveLength(2);
  expect(await disponivel(copiaA.id)).toBe(false);
  expect(await disponivel(copiaB.id)).toBe(false);

  await botao('Detalhes da locação ' + locacao.id).click();
  const detalhe = page.getByRole('region', { name: 'Exemplares da locação' });
  await expect(detalhe.getByRole('row', { name: /E2E-01 Livro fictício E2E/ })).toBeVisible();
  await expect(detalhe.getByRole('row', { name: /E2E-02 Livro fictício E2E/ })).toBeVisible();

  await botao('Devolver exemplar E2E-01').click();
  await clicarEConfirmarPost(page, 'Confirmar devolução', '/devolucoes', 200);
  expect(await disponivel(copiaA.id)).toBe(true);
  expect(await disponivel(copiaB.id)).toBe(false);
  await botao('Quitar exemplar E2E-01').click();
  const parcial = await clicarEConfirmarPost(page, 'Confirmar quitação', '/pagamentos', 200);
  expect(parcial.locacaoEncerrada).toBe(false);

  await botao('Devolver exemplar E2E-02').click();
  await clicarEConfirmarPost(page, 'Confirmar devolução', '/devolucoes', 200);
  expect(await disponivel(copiaB.id)).toBe(true);
  await botao('Quitar exemplar E2E-02').click();
  const final = await clicarEConfirmarPost(page, 'Confirmar quitação', '/pagamentos', 200);
  expect(final.locacaoEncerrada).toBe(true);
  await expect(detalhe.getByText('Locação encerrada', { exact: true })).toBeVisible();
  await page.getByLabel('Situação', { exact: true }).selectOption('ENCERRADA');
  await expect(botao('Detalhes da locação ' + locacao.id)).toBeVisible();
});
