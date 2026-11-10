// Lógica pura del panel del vendedor: qué va en cada tarjeta y el texto para pedir al proveedor.
// Las tarjetas usan GET /api/predicciones (docs/REPOSICION.md) y GET /api/vencimientos (docs/VENCIMIENTOS.md).
// Un lote que vence hoy todavía se vende (dias_restantes = 0); recién al día siguiente se retira.

/** Productos que hay que pedir ya. `comprar_ahora` null es falta de historial: no se afirma que haga falta. */
function productosParaSolicitar(predicciones) {
    return (predicciones ?? []).filter(p => p.comprar_ahora === true);
}

/**
 * Agrupa los productos a solicitar por proveedor, para armar un pedido por proveedor.
 * Los productos sin proveedor activo van juntos al final, con proveedorId null.
 */
function agruparPorProveedor(productos) {
    const grupos = new Map();
    for (const p of productos) {
        const clave = p.proveedor_id ?? null;
        if (!grupos.has(clave)) grupos.set(clave, { proveedorId: clave, proveedor: p.proveedor ?? null, productos: [] });
        grupos.get(clave).productos.push(p);
    }
    return [...grupos.values()].sort((a, b) => {
        if (a.proveedorId === null) return 1;
        if (b.proveedorId === null) return -1;
        return a.proveedor.localeCompare(b.proveedor, 'es');
    });
}

/** Lotes por vencer que conviene vender primero (incluye los que vencen hoy). */
function lotesParaVender(vencimientos) {
    return (vencimientos ?? []).filter(l => l.dias_restantes >= 0 && l.cantidad_disponible > 0);
}

/** Lotes ya vencidos: no se pueden vender y hay que retirarlos. */
function lotesParaRetirar(vencimientos) {
    return (vencimientos ?? []).filter(l => l.dias_restantes < 0 && l.cantidad_disponible > 0);
}

/** Suma del valor en riesgo de una lista de lotes, en pesos. */
function valorEnRiesgo(lotes) {
    return Math.round(lotes.reduce((total, l) => total + (l.valor_en_riesgo ?? 0), 0) * 100) / 100;
}

/** Texto corto para avisar cuándo vence o venció un lote. */
function textoVencimiento(diasRestantes) {
    if (diasRestantes === 0) return 'Vence hoy';
    if (diasRestantes === 1) return 'Vence mañana';
    if (diasRestantes > 1) return `Vence en ${diasRestantes} días`;
    const dias = -diasRestantes;
    return dias === 1 ? 'Venció ayer' : `Venció hace ${dias} días`;
}

/** Mensaje para mandarle al proveedor por fuera del sistema (WhatsApp, mail o teléfono). */
function textoPedidoProveedor(grupo) {
    const saludo = grupo.proveedor ? `Hola, ${grupo.proveedor}.` : 'Hola.';
    const lineas = grupo.productos.map(p => `- ${p.nombre} (${p.sku}): ${p.lote_sugerido} unidades`);
    return [`${saludo} Necesitamos reponer:`, ...lineas, 'Gracias.'].join('\n');
}

if (typeof module !== 'undefined') {
    module.exports = {
        productosParaSolicitar, agruparPorProveedor, lotesParaVender, lotesParaRetirar,
        valorEnRiesgo, textoVencimiento, textoPedidoProveedor,
    };
}
