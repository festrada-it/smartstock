// Cantidades de las líneas de una venta o pedido frente al stock disponible (#72).
// No se corrige la cantidad que escribió el vendedor: se avisa en la línea y core decide al guardar.
// Si falta stock, core rechaza con 400, registra la venta perdida del faltante y no crea nada (plan §10.3).

const MENSAJE_SIN_STOCK = 'No hay stock suficiente. Debe solicitarlo al proveedor y, una vez ingresado, puede hacer el pedido.';

/** Cantidad escrita por el vendedor: solo enteros ≥ 1 (core rechaza decimales). No se limita al stock. */
function cantidadIngresada(valor) {
    return Math.max(1, Math.trunc(Number(valor)) || 1);
}

/** Aviso de la línea si la cantidad supera el stock; null si alcanza. */
function avisoStockInsuficiente(cantidad, disponible) {
    if (cantidad <= disponible) return null;
    return `Stock insuficiente: hay ${disponible} ${disponible === 1 ? 'disponible' : 'disponibles'}`;
}

/** Líneas que piden más que el stock. */
function lineasSinStock(lineas) {
    return lineas.filter(l => avisoStockInsuficiente(l.cantidad, l.producto.stockActual) !== null);
}

/**
 * ¿Core rechazó por falta de stock? Es un 400 con el detalle `faltantes` (contrato de la tarea 5.5),
 * o un 400 cuando la pantalla ya mostraba líneas sin stock (core actual, que todavía no manda el detalle).
 */
function esRechazoPorStock(error, lineas) {
    if (error?.status !== 400) return false;
    return (error.datos?.faltantes?.length ?? 0) > 0 || lineasSinStock(lineas).length > 0;
}

if (typeof module !== 'undefined') {
    module.exports = { MENSAJE_SIN_STOCK, cantidadIngresada, avisoStockInsuficiente, lineasSinStock, esRechazoPorStock };
}
