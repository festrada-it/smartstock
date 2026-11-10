// "Ver más" del historial de clientes con doble clic (revisión de #48). Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { crearCargaUnica } = require('../js/carga-unica.js');

function respuestaDiferida() {
    let resolver;
    const promesa = new Promise(r => { resolver = r; });
    return { promesa, resolver };
}

// Misma secuencia que cargarPedidos en clientes.js: pide la página y agrega sus filas a la tabla.
function crearHistorial(pedirPagina) {
    const filas = [];
    const cargas = crearCargaUnica();
    return {
        filas,
        verMas: (clienteId, pagina) => cargas.ejecutar(`${clienteId}-${pagina}`, async () => {
            filas.push(...await pedirPagina(clienteId, pagina));
        }),
    };
}

test('doble clic en "Ver más" carga la página una sola vez', async () => {
    let pedidos = 0;
    const respuesta = respuestaDiferida();
    const historial = crearHistorial(() => { pedidos++; return respuesta.promesa; });

    const primero = historial.verMas(7, 1);
    const segundo = historial.verMas(7, 1);
    respuesta.resolver(['OC-11', 'OC-12']);

    assert.deepEqual(await Promise.all([primero, segundo]), [true, false]);
    assert.equal(pedidos, 1);
    assert.deepEqual(historial.filas, ['OC-11', 'OC-12']);
});

test('terminada la carga, la misma página se puede volver a pedir', async () => {
    const historial = crearHistorial(async () => ['OC-1']);
    assert.equal(await historial.verMas(7, 0), true);
    assert.equal(await historial.verMas(7, 0), true);
});

test('otra página u otro cliente no quedan bloqueados por una carga en curso', async () => {
    const respuesta = respuestaDiferida();
    const historial = crearHistorial((clienteId, pagina) => (pagina === 1 ? respuesta.promesa : Promise.resolve([`${clienteId}-${pagina}`])));

    const lenta = historial.verMas(7, 1);
    assert.equal(await historial.verMas(8, 0), true);
    assert.equal(await historial.verMas(7, 2), true);
    respuesta.resolver(['7-1']);
    assert.equal(await lenta, true);
});

test('si la carga falla, se libera la clave para reintentar', async () => {
    const cargas = crearCargaUnica();
    await assert.rejects(cargas.ejecutar('7-1', async () => { throw new Error('sin conexión'); }));
    assert.equal(await cargas.ejecutar('7-1', async () => {}), true);
});
