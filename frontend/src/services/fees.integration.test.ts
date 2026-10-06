import { describe, expect, it } from 'vitest';
import { ApiRequestError, createFee, deactivateFee, getFee, listFees, updateFee } from './api';
import type { FeeRequest } from '../types';

// Integração real: execute com VITE_API_URL apontando para o backend e VITE_USER_ROLE=ADMIN.
const configured = Boolean(import.meta.env.VITE_API_URL) && import.meta.env.VITE_USER_ROLE === 'ADMIN';
describe.skipIf(!configured)('US-02 — cliente HTTP integrado ao backend real', () => {
  const bodies: FeeRequest[] = [
    { name: 'Integração fixa', description: 'Valor único', type: 'FIXA', amount: 12.50 },
    { name: 'Integração m²', description: 'Por metragem', type: 'POR_METRAGEM', areaPricingMode: 'VALOR_POR_M2', amountPerM2: 5.25 },
    { name: 'Integração unidades', description: 'Extintor', type: 'POR_METRAGEM', areaPricingMode: 'UNIDADES_POR_INTERVALO', areaPerUnitM2: 25, unitAmount: 30 },
    { name: 'Integração variável', description: 'Consumo', type: 'VARIAVEL', measurementUnit: 'kVA' },
  ];

  it.each(bodies)('cria, consulta e lista $name', async (body) => {
    const saved = await createFee(body);
    expect(saved).toMatchObject({ ...body, active: true });
    expect(await getFee(saved.id)).toEqual(saved);
    expect(await listFees()).toContainEqual(saved);
  });

  it('edita o tipo, elimina configuração anterior e desativa sem excluir', async () => {
    const saved = await createFee(bodies[0]);
    const edited = await updateFee(saved.id, bodies[3]);
    expect(edited).toMatchObject({ ...bodies[3], amount: null, active: true });
    const disabled = await deactivateFee(saved.id);
    expect(disabled.active).toBe(false);
    expect((await getFee(saved.id)).active).toBe(false);
    expect(await listFees()).toContainEqual(disabled);
    expect((await updateFee(saved.id, bodies[0])).active).toBe(false);
  });

  it('preserva invalidFields e não altera uma taxa após rejeição da API', async () => {
    const saved = await createFee(bodies[0]);
    await expect(updateFee(saved.id, { name: '', description: '', type: 'FIXA', amount: 0 }))
      .rejects.toMatchObject({ status: 400, invalidFields: { name: expect.any(String), amount: expect.any(String) } });
    expect(await getFee(saved.id)).toEqual(saved);
    await expect(createFee({ name: 'Inválida', description: '', type: 'FIXA', amount: -1 }))
      .rejects.toMatchObject({ status: 400, invalidFields: { amount: expect.any(String) } });
  });

  it('transforma falhas de integração em erro HTTP controlado', async () => {
    await expect(getFee('id-invalido')).rejects.toBeInstanceOf(ApiRequestError);
    await expect(getFee('00000000-0000-0000-0000-000000000000')).rejects.toMatchObject({ status: 404 });
  });
});
