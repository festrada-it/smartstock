// Acceso a pedidos: la API real de core o los datos de ejemplo, con la misma interfaz.
// Contrato: docs/API-PEDIDOS.md (#24, PR #52). Envío, entrega, cancelación y fecha de envío son de #25:
// sus rutas salen del plan (docs/plan-mejoras.md §5) hasta que se publique su contrato.

const PedidosCore = {
    listar: (filtros = {}, pagina = 0, tamanio = 20) => {
        const p = new URLSearchParams({ page: pagina, size: tamanio });
        Object.entries(filtros).forEach(([clave, valor]) => { if (valor) p.set(clave, valor); });
        return coreApi(`/pedidos?${p}`);
    },
    obtener: id => coreApi(`/pedidos/${id}`),
    crear: datos => coreApi('/pedidos', { method: 'POST', body: datos }),
    confirmar: id => coreApi(`/pedidos/${id}/confirmar`, { method: 'POST' }),
    // #25
    enviar: id => coreApi(`/pedidos/${id}/enviar`, { method: 'POST' }),
    entregar: id => coreApi(`/pedidos/${id}/entregar`, { method: 'POST' }),
    cancelar: id => coreApi(`/pedidos/${id}/cancelar`, { method: 'POST' }),
    pactarFecha: (id, fechaEnvio) => coreApi(`/pedidos/${id}/fecha-envio`, { method: 'PUT', body: { fechaEnvio } }),
};

const fuentePedidos = CONFIG.DATOS_EJEMPLO?.pedidos ? PedidosEjemplo : PedidosCore;

const FORMAS_PAGO = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia', TARJETA: 'Tarjeta', CUENTA_CORRIENTE: 'Cuenta corriente' };
const ESTADOS_PEDIDO = {
    PENDIENTE: { texto: 'Pendiente', clase: 'text-bg-secondary' },
    CONFIRMADO: { texto: 'Confirmado', clase: 'text-bg-primary' },
    ENVIADO: { texto: 'Enviado', clase: 'text-bg-info' },
    ENTREGADO: { texto: 'Entregado', clase: 'text-bg-success' },
    CANCELADO: { texto: 'Cancelado', clase: 'text-bg-dark' },
};
const ESTADOS_ENTREGA = {
    SIN_PACTAR: { texto: 'Sin pactar', clase: 'text-bg-light tw-border' },
    PACTADA: { texto: 'Pactada', clase: 'text-bg-warning' },
    ENTREGADA: { texto: 'Entregada', clase: 'text-bg-success' },
};
const DESCUENTO_MAXIMO = 20;

/** Mismo cálculo que core (docs/API-PEDIDOS.md): total = subtotal × (1 − d/100) redondeado HALF_UP. */
function calcularTotales(lineas, descuentoPct) {
    const redondear = n => Math.round((n + Number.EPSILON) * 100) / 100;
    const subtotal = redondear(lineas.reduce((s, l) => s + l.cantidad * l.precioUnitario, 0));
    const total = redondear(subtotal * (1 - descuentoPct / 100));
    return { subtotal, total, importeDescuento: redondear(subtotal - total) };
}
