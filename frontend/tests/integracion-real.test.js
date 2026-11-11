const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function cargar(contexto, archivo) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', archivo), 'utf8'), contexto);
}

test('la configuración de entrega crea pedidos en core y propaga sus rechazos', async () => {
    const llamadas = [];
    const rechazo = Object.assign(new Error('No hay stock suficiente'), { status: 400 });
    const contexto = vm.createContext({
        URLSearchParams,
        PedidosEjemplo: { crear: () => assert.fail('El pedido debe llegar a core') },
        coreApi: async (ruta, opciones) => {
            llamadas.push({ ruta, opciones });
            throw rechazo;
        },
    });
    cargar(contexto, 'config.js');
    cargar(contexto, 'pedidos-fuente.js');
    await assert.rejects(vm.runInContext(
        'fuentePedidos.crear({clienteId: 7, items: [{productoId: 1, cantidad: 3}]})', contexto),
        error => error === rechazo);
    assert.equal(llamadas[0].ruta, '/pedidos');
    assert.equal(llamadas[0].opciones.method, 'POST');
    assert.equal(llamadas[0].opciones.body.items[0].cantidad, 3);
});

test('el monitor conserva los valores de reposición calculados por analytics', async () => {
    const contexto = vm.createContext({
        Sesion: { requerir: () => null },
        Chart: { defaults: { font: {} } },
        document: { getElementById: () => ({ classList: { remove: () => assert.fail('No debe activar datos de reposición de ejemplo') } }) },
        coreApi: async () => [],
    });
    cargar(contexto, 'config.js');
    cargar(contexto, 'dashboard.js');
    const completar = vm.runInContext('completarReposicion', contexto);
    const respuesta = [{ id: 1, punto_reposicion: 37, comprar_ahora: true, lead_time_dias: 4 }];
    assert.strictEqual(await completar(respuesta), respuesta);
    assert.equal(respuesta[0].punto_reposicion, 37);
});
