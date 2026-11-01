// Tests de la lógica del monitor de dirección. Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { FORMULAS, queryDashboard, semaforoDsi, formatoDias, formatoRotacion, variacion } = require('../js/indicadores.js');
const { DashboardEjemplo } = require('../js/datos-ejemplo-dashboard.js');

const ORDEN_SEMAFORO = { ROJO: 0, AMARILLO: 1, VERDE: 2 };
const GRUPOS = [
    { id: 1, nombre: 'Lácteos' }, { id: 2, nombre: 'Frescos' }, { id: 3, nombre: 'Almacén' },
    { id: 4, nombre: 'Bebidas' }, { id: 5, nombre: 'Limpieza' },
];

test('queryDashboard arma período y grupo, y rechaza otros períodos', () => {
    assert.equal(queryDashboard(30), '?periodo=30');
    assert.equal(queryDashboard('90', '4'), '?periodo=90&grupo_id=4');
    assert.equal(queryDashboard(30, ''), '?periodo=30');
    assert.throws(() => queryDashboard(60), /Período inválido/);
});

test('semaforoDsi muestra ícono y texto para cada estado', () => {
    assert.deepEqual(
        [
            { dsi: 5, semaforo: 'ROJO', motivo: 'RIESGO_QUIEBRE' },
            { dsi: 20, semaforo: 'VERDE', motivo: null },
            { dsi: 60, semaforo: 'AMARILLO', motivo: 'DINERO_ATRAPADO' },
            { dsi: 120, semaforo: 'ROJO', motivo: 'DINERO_ATRAPADO' },
            { dsi: null, semaforo: 'ROJO', motivo: 'SIN_VENTAS' },
        ].map(item => { const s = semaforoDsi(item); return `${s.icono} ${s.texto}`; }),
        ['⛔ Riesgo de quiebre', '✅ Saludable', '⚠️ Dinero atrapado', '⛔ Dinero atrapado', '⛔ Sin ventas'],
    );
});

test('sin ventas: DSI null se muestra como "sin ventas" aunque falte el motivo', () => {
    assert.equal(formatoDias(null), 'sin ventas');
    assert.equal(semaforoDsi({ dsi: null, semaforo: 'ROJO' }).texto, 'Sin ventas');
});

test('formatos en es-AR', () => {
    assert.equal(formatoDias(21.64), '21,6 días');
    assert.equal(formatoRotacion(1.375), '1,38 veces');
    assert.equal(formatoRotacion(null), '-');
});

test('variacion indica ▲, ▼ o = contra el período anterior', () => {
    assert.equal(variacion(21.6, 24.8, formatoDias, 30).texto, '▼ 3,2 días vs. 30 días anteriores');
    assert.equal(variacion(1.37, 1.21, formatoRotacion, 90).texto, '▲ 0,16 veces vs. 90 días anteriores');
    assert.equal(variacion(12.0, 12.01, formatoDias, 30).flecha, '=');
    assert.equal(variacion(null, 24.8, formatoDias, 30).texto, 'sin comparación vs. 30 días anteriores');
});

test('cada tarjeta tiene su fórmula para el tooltip', () => {
    for (const clave of ['dsi', 'rotacion', 'enRiesgo', 'fillRate']) assert.ok(FORMULAS[clave].includes('='), clave);
});

test('datos de ejemplo: forma del contrato y orden de peor a mejor', () => {
    for (const periodo of [30, 90]) {
        const r = DashboardEjemplo.inventario(periodo, null, GRUPOS);
        assert.equal(r.periodo, periodo);
        for (const campo of ['valor_stock', 'costo_ventas', 'dsi', 'rotacion_mensual', 'rotacion_anual', 'semaforo']) {
            assert.ok(campo in r.total, campo);
        }
        assert.deepEqual(Object.keys(r.anterior).sort(), ['dsi', 'rotacion_anual', 'rotacion_mensual']);
        for (const lista of [r.por_grupo, r.por_producto]) {
            const orden = lista.map(x => ORDEN_SEMAFORO[x.semaforo]);
            assert.deepEqual(orden, [...orden].sort((a, b) => a - b));
        }
        assert.ok(r.por_producto.some(p => p.dsi === null && p.motivo === 'SIN_VENTAS'));
    }
});

test('datos de ejemplo: el filtro de grupo deja solo ese grupo y su total', () => {
    const r = DashboardEjemplo.inventario(90, '4', GRUPOS);
    assert.deepEqual(r.por_grupo.map(g => g.grupo), ['Bebidas']);
    assert.ok(r.por_producto.every(p => p.grupo_id === 4));
    assert.equal(r.total.dsi, r.por_grupo[0].dsi);
    assert.notEqual(r.anterior.dsi, null);
});
