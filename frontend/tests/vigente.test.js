// Detalle de proveedores con respuestas fuera de orden (revisión de #39). Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { crearVigente } = require('../js/vigente.js');

/** Promesa que se resuelve cuando el test lo decide, para simular respuestas que llegan en otro orden. */
function respuestaDiferida() {
    let resolver;
    const promesa = new Promise(r => { resolver = r; });
    return { promesa, resolver };
}

// Misma secuencia que abrirDetalle en proveedores.js: marca el abierto, espera y descarta si ya no es el actual.
async function abrirDetalle(vigente, pantalla, proveedor, pedirProductos) {
    vigente.abrir(proveedor.id);
    pantalla.titulo = proveedor.nombre;
    const productos = await pedirProductos(proveedor.id);
    if (!vigente.esActual(proveedor.id)) return;
    pantalla.productos = productos;
}

test('si las respuestas llegan en orden inverso, el detalle muestra los productos del último proveedor abierto', async () => {
    const vigente = crearVigente();
    const pantalla = {};
    const respuestas = { 1: respuestaDiferida(), 2: respuestaDiferida() };
    const pedir = id => respuestas[id].promesa;

    const primero = abrirDetalle(vigente, pantalla, { id: 1, nombre: 'Distribuidora del Sur' }, pedir);
    const segundo = abrirDetalle(vigente, pantalla, { id: 2, nombre: 'Alimentos Frescos Norte' }, pedir);
    respuestas[2].resolver(['Tomate']);
    await segundo;
    respuestas[1].resolver(['Leche']);
    await primero;

    assert.deepEqual(pantalla, { titulo: 'Alimentos Frescos Norte', productos: ['Tomate'] });
});

test('esActual solo es verdadero para el último id abierto', () => {
    const vigente = crearVigente();
    assert.equal(vigente.esActual(1), false);
    vigente.abrir(1);
    assert.equal(vigente.esActual(1), true);
    vigente.abrir(2);
    assert.equal(vigente.esActual(1), false);
    assert.equal(vigente.esActual(2), true);
});
