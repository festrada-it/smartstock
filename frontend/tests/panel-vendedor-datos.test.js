// Panel del vendedor: qué va en cada tarjeta. Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const {
    productosParaSolicitar, agruparPorProveedor, lotesParaVender, lotesParaRetirar,
    valorEnRiesgo, textoVencimiento, textoPedidoProveedor,
} = require('../js/panel-vendedor-datos.js');

const lote = (id, dias, extra = {}) => ({ lote_id: id, dias_restantes: dias, cantidad_disponible: 10, valor_en_riesgo: 100, ...extra });

test('solicitar a proveedor solo incluye comprar_ahora true, no null ni false', () => {
    const predicciones = [
        { id: 1, comprar_ahora: true },
        { id: 2, comprar_ahora: false },
        { id: 3, comprar_ahora: null },
    ];
    assert.deepEqual(productosParaSolicitar(predicciones).map(p => p.id), [1]);
    assert.deepEqual(productosParaSolicitar(undefined), []);
});

test('agrupa por proveedor en orden alfabético y deja los sin proveedor al final', () => {
    const grupos = agruparPorProveedor([
        { id: 1, proveedor_id: null, proveedor: null },
        { id: 2, proveedor_id: 7, proveedor: 'Lácteos Sur' },
        { id: 3, proveedor_id: 4, proveedor: 'Alimentos Norte' },
        { id: 4, proveedor_id: 7, proveedor: 'Lácteos Sur' },
    ]);
    assert.deepEqual(grupos.map(g => [g.proveedorId, g.productos.map(p => p.id)]), [
        [4, [3]],
        [7, [2, 4]],
        [null, [1]],
    ]);
});

test('el lote que vence hoy se intenta vender; el que venció ayer se retira', () => {
    const vencimientos = [lote(1, 5), lote(2, 0), lote(3, -1), lote(4, -10)];
    assert.deepEqual(lotesParaVender(vencimientos).map(l => l.lote_id), [1, 2]);
    assert.deepEqual(lotesParaRetirar(vencimientos).map(l => l.lote_id), [3, 4]);
});

test('los lotes sin unidades disponibles no aparecen en ninguna tarjeta', () => {
    const vencimientos = [lote(1, 3, { cantidad_disponible: 0 }), lote(2, -3, { cantidad_disponible: 0 })];
    assert.deepEqual(lotesParaVender(vencimientos), []);
    assert.deepEqual(lotesParaRetirar(vencimientos), []);
});

test('valor en riesgo suma los lotes y redondea a centavos', () => {
    assert.equal(valorEnRiesgo([lote(1, 1, { valor_en_riesgo: 0.1 }), lote(2, 1, { valor_en_riesgo: 0.2 })]), 0.3);
    assert.equal(valorEnRiesgo([]), 0);
});

test('texto de vencimiento para hoy, mañana, futuro y vencidos', () => {
    assert.equal(textoVencimiento(0), 'Vence hoy');
    assert.equal(textoVencimiento(1), 'Vence mañana');
    assert.equal(textoVencimiento(4), 'Vence en 4 días');
    assert.equal(textoVencimiento(-1), 'Venció ayer');
    assert.equal(textoVencimiento(-6), 'Venció hace 6 días');
});

test('el pedido al proveedor lista cada producto con el lote sugerido', () => {
    const texto = textoPedidoProveedor({
        proveedor: 'Lácteos Sur',
        productos: [{ nombre: 'Leche 1L', sku: 'LAC-001', lote_sugerido: 120 }],
    });
    assert.equal(texto, 'Hola, Lácteos Sur. Necesitamos reponer:\n- Leche 1L (LAC-001): 120 unidades\nGracias.');
});
