// Cantidades de las líneas de una venta o pedido frente al stock disponible (#72).
// No se corrige la cantidad que escribió el vendedor: se avisa en la línea y no se deja guardar.

/** Cantidad escrita por el vendedor: solo enteros ≥ 1 (core rechaza decimales). No se limita al stock. */
function cantidadIngresada(valor) {
    return Math.max(1, Math.trunc(Number(valor)) || 1);
}

/** Aviso de la línea si la cantidad supera el stock; null si alcanza. */
function avisoStockInsuficiente(cantidad, disponible) {
    if (cantidad <= disponible) return null;
    return `Stock insuficiente: hay ${disponible} ${disponible === 1 ? 'disponible' : 'disponibles'}`;
}

/** Líneas que no se pueden guardar porque piden más que el stock. */
function lineasSinStock(lineas) {
    return lineas.filter(l => avisoStockInsuficiente(l.cantidad, l.producto.stockActual) !== null);
}

if (typeof module !== 'undefined') module.exports = { cantidadIngresada, avisoStockInsuficiente, lineasSinStock };
