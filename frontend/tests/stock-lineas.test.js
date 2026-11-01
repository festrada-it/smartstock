// Cantidad mayor que el stock en Ventas y Pedidos (#72). Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { cantidadIngresada, avisoStockInsuficiente, lineasSinStock } = require('../js/stock-lineas.js');

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

test('lineasSinStock devuelve las que no se pueden guardar', () => {
    const helado = { producto: { id: 1, stockActual: 10 }, cantidad: 80 };
    const leche = { producto: { id: 2, stockActual: 45 }, cantidad: 5 };
    assert.deepEqual(lineasSinStock([helado, leche]), [helado]);
    assert.deepEqual(lineasSinStock([leche]), []);
});
