// Marca y umbral de rotación que el catálogo y Grupos envían a core (revisión de #46).
// Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { marcaParaEnviar, porcentajeDesdeUmbral, formatoPorcentaje, umbralParaEnviar } = require('../js/catalogo-datos.js');

test('al editar, vaciar la marca manda "" para borrarla (null la conservaría)', () => {
    assert.equal(marcaParaEnviar('', true), '');
    assert.equal(marcaParaEnviar('   ', true), '');
});

test('al crear, una marca vacía va como null', () => {
    assert.equal(marcaParaEnviar('', false), null);
    assert.equal(marcaParaEnviar(undefined, false), null);
});

test('la marca con texto se manda sin espacios de más', () => {
    assert.equal(marcaParaEnviar('  SanCor ', true), 'SanCor');
    assert.equal(marcaParaEnviar('SanCor', false), 'SanCor');
});

test('el umbral se muestra con hasta 2 decimales del porcentaje', () => {
    assert.equal(porcentajeDesdeUmbral(0.1234), 12.34);
    assert.equal(porcentajeDesdeUmbral(0.1), 10);
    assert.equal(formatoPorcentaje(0.1234), '12,34%');
    assert.equal(formatoPorcentaje(0.1), '10%');
    assert.equal(formatoPorcentaje(0.125), '12,5%');
});

test('si el umbral no cambió, se reenvía el valor original de core sin redondear', () => {
    assert.equal(umbralParaEnviar('12.34', 0.1234), 0.1234);
    assert.equal(umbralParaEnviar(porcentajeDesdeUmbral(0.1), 0.1), 0.1);
});

test('si el umbral cambió, se convierte el porcentaje a fracción con 4 decimales', () => {
    assert.equal(umbralParaEnviar('12.35', 0.1234), 0.1235);
    assert.equal(umbralParaEnviar('7.5', 0.1), 0.075);
    assert.equal(umbralParaEnviar('15', undefined), 0.15);
});
