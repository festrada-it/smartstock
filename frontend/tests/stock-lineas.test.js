// Cantidad mayor que el stock en Ventas y Pedidos (#72, plan §10.3). Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { MENSAJE_SIN_STOCK, cantidadIngresada, avisoStockInsuficiente, lineasSinStock, esRechazoPorStock } = require('../js/stock-lineas.js');

const helado = { producto: { id: 1, stockActual: 10 }, cantidad: 80 };
const leche = { producto: { id: 2, stockActual: 45 }, cantidad: 5 };
const error400 = datos => Object.assign(new Error('Stock insuficiente'), { status: 400, datos });

test('la cantidad que escribe el vendedor no se limita al stock', () => {
    assert.equal(cantidadIngresada('80'), 80);
});

test('solo enteros ≥ 1, como pide core', () => {
    assert.equal(cantidadIngresada('2.7'), 2);
    assert.equal(cantidadIngresada('0'), 1);
    assert.equal(cantidadIngresada(''), 1);
    assert.equal(cantidadIngresada('-5'), 1);
});

test('aviso en la línea cuando la cantidad supera el stock', () => {
    assert.equal(avisoStockInsuficiente(80, 10), 'Stock insuficiente: hay 10 disponibles');
    assert.equal(avisoStockInsuficiente(3, 1), 'Stock insuficiente: hay 1 disponible');
    assert.equal(avisoStockInsuficiente(1, 0), 'Stock insuficiente: hay 0 disponibles');
});

test('sin aviso si el stock alcanza, incluso justo', () => {
    assert.equal(avisoStockInsuficiente(10, 10), null);
    assert.equal(avisoStockInsuficiente(1, 10), null);
});

test('lineasSinStock devuelve las que piden más que el stock', () => {
    assert.deepEqual(lineasSinStock([helado, leche]), [helado]);
    assert.deepEqual(lineasSinStock([leche]), []);
});

test('un 400 con faltantes es rechazo por stock, aunque la pantalla no lo viera', () => {
    const e = error400({ faltantes: [{ productoId: 2, cantidadPedida: 5, disponible: 3, faltante: 2 }] });
    assert.equal(esRechazoPorStock(e, [leche]), true);
});

test('un 400 sin detalle es rechazo por stock si la pantalla ya mostraba líneas sin stock', () => {
    assert.equal(esRechazoPorStock(error400(null), [helado, leche]), true);
});

test('otros errores muestran el mensaje de core', () => {
    assert.equal(esRechazoPorStock(error400({ error: 'El descuento debe estar entre 0 y 20' }), [leche]), false);
    assert.equal(esRechazoPorStock(Object.assign(new Error('No encontrado'), { status: 404 }), [helado]), false);
    assert.equal(esRechazoPorStock(new Error('Sin conexión'), [helado]), false);
});

test('el mensaje de la alerta es el acordado', () => {
    assert.equal(MENSAJE_SIN_STOCK, 'No hay stock suficiente. Debe solicitarlo al proveedor y, una vez ingresado, puede hacer el pedido.');
});
