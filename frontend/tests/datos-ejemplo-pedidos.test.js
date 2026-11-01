// Datos de ejemplo de Pedidos con la regla del 01-11 (plan §10.3). Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/** Carga los scripts como en pedidos.html y devuelve PedidosEjemplo inicializado con un catálogo chico. */
function crearEjemplo() {
    const contexto = vm.createContext({ CONFIG: { DATOS_EJEMPLO: { pedidos: true } }, coreApi: () => {}, structuredClone, setTimeout, URLSearchParams });
    for (const archivo of ['datos-ejemplo-pedidos.js', 'pedidos-fuente.js']) {
        vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', archivo), 'utf8'), contexto);
    }
    const ejemplo = vm.runInContext('PedidosEjemplo', contexto);
    ejemplo.inicializar(
        [{ id: 1, nombre: 'Helado 1L', precio: 3000, stockActual: 10, stockMinimo: 2 },
         { id: 2, nombre: 'Leche 1L', precio: 1800, stockActual: 45, stockMinimo: 30 }],
        [{ id: 7, nombre: 'Chino Almagro', calidad: 'BUENA', clasificacion: 4 }],
    );
    return ejemplo;
}
const pedido = items => ({ clienteId: 7, formaPago: 'EFECTIVO', descuentoPct: 0, items });

test('sin stock suficiente rechaza con 400 y el faltante, y no crea el pedido', async () => {
    const ejemplo = crearEjemplo();
    const antes = (await ejemplo.listar()).totalElementos;
    await assert.rejects(ejemplo.crear(pedido([{ productoId: 1, cantidad: 80 }, { productoId: 2, cantidad: 5 }])), e => {
        assert.equal(e.status, 400);
        assert.deepEqual(JSON.parse(JSON.stringify(e.datos.faltantes)),
            [{ productoId: 1, producto: 'Helado 1L', cantidadPedida: 80, disponible: 10, faltante: 70 }]);
        return true;
    });
    assert.equal((await ejemplo.listar()).totalElementos, antes);
});

test('con stock crea PENDIENTE, reserva y confirmar no vuelve a descontar', async () => {
    const ejemplo = crearEjemplo();
    const creado = await ejemplo.crear(pedido([{ productoId: 1, cantidad: 10 }]));
    assert.equal(creado.estado, 'PENDIENTE');
    // Con las 10 reservadas, otro pedido de 1 ya no tiene stock.
    await assert.rejects(ejemplo.crear(pedido([{ productoId: 1, cantidad: 1 }])), e => e.datos.faltantes[0].disponible === 0);
    assert.equal((await ejemplo.confirmar(creado.id)).estado, 'CONFIRMADO');
    await assert.rejects(ejemplo.crear(pedido([{ productoId: 1, cantidad: 1 }])), e => e.status === 400);
});

test('cancelar un PENDIENTE o un CONFIRMADO devuelve el stock reservado', async () => {
    const ejemplo = crearEjemplo();
    const pendiente = await ejemplo.crear(pedido([{ productoId: 1, cantidad: 10 }]));
    await ejemplo.cancelar(pendiente.id);
    const confirmado = await ejemplo.crear(pedido([{ productoId: 1, cantidad: 10 }]));
    await ejemplo.confirmar(confirmado.id);
    await ejemplo.cancelar(confirmado.id);
    assert.equal((await ejemplo.crear(pedido([{ productoId: 1, cantidad: 10 }]))).estado, 'PENDIENTE');
});
