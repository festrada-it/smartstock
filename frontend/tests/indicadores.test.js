// Tests de la lógica del monitor de dirección. Correr con: node --test frontend/tests/
const test = require('node:test');
const assert = require('node:assert/strict');
const { FORMULAS, SEMAFORO_DSI, queryDashboard, semaforoDsi, formatoDias, formatoRotacion, variacion } = require('../js/indicadores.js');
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

test('semaforoDsi muestra ícono, nombre y ayuda de cada estado', () => {
    assert.deepEqual(
        [
            { dsi: 5, semaforo: 'ROJO', estado: 'REPONER_YA' },
            { dsi: 20, semaforo: 'VERDE', estado: 'TODO_EN_ORDEN' },
            { dsi: 60, semaforo: 'AMARILLO', estado: 'FRENAR_COMPRAS' },
            { dsi: 120, semaforo: 'ROJO', estado: 'LIQUIDAR_STOCK' },
            { dsi: null, semaforo: 'ROJO', estado: 'SIN_VENTAS' },
        ].map(item => { const s = semaforoDsi(item); return `${s.icono} ${s.texto}: ${s.ayuda}`; }),
        [
            '⛔ Reponer ya: Alcanza para menos de 7 días. Hay que reponer.',
            '✅ Todo en orden: Stock equilibrado con las ventas.',
            '⚠️ Frenar compras: Hay plata inmovilizada. Revisar las próximas compras.',
            '⛔ Liquidar stock: Más de 3 meses de stock. Promocionar o no recomprar.',
            '⛔ Sin ventas: No hubo ventas en el período: el DSI no se puede calcular.',
        ],
    );
});

test('sin ventas: DSI null se muestra como "sin ventas" aunque falte el estado', () => {
    assert.equal(formatoDias(null), 'sin ventas');
    assert.equal(semaforoDsi({ dsi: null, semaforo: 'ROJO' }).texto, 'Sin ventas');
});

test('un estado desconocido se muestra tal cual, sin romper', () => {
    assert.equal(semaforoDsi({ dsi: 30, semaforo: 'VERDE', estado: 'OTRO' }).texto, 'OTRO');
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
        for (const campo of ['valor_stock', 'costo_ventas', 'dsi', 'rotacion_mensual', 'rotacion_anual', 'semaforo', 'estado']) {
            assert.ok(campo in r.total, campo);
        }
        assert.deepEqual(Object.keys(r.anterior).sort(), ['dsi', 'rotacion_anual', 'rotacion_mensual']);
        for (const lista of [r.por_grupo, r.por_producto]) {
            const orden = lista.map(x => ORDEN_SEMAFORO[x.semaforo]);
            assert.deepEqual(orden, [...orden].sort((a, b) => a - b));
        }
        assert.ok(r.por_producto.some(p => p.dsi === null && p.estado === 'SIN_VENTAS'));
        for (const x of [r.total, ...r.por_grupo, ...r.por_producto]) assert.ok(x.estado in SEMAFORO_DSI, x.estado);
    }
});

test('datos de ejemplo: el filtro de grupo deja solo ese grupo y su total', () => {
    const r = DashboardEjemplo.inventario(90, '4', GRUPOS);
    assert.deepEqual(r.por_grupo.map(g => g.grupo), ['Bebidas']);
    assert.ok(r.por_producto.every(p => p.grupo_id === 4));
    assert.equal(r.total.dsi, r.por_grupo[0].dsi);
    assert.notEqual(r.anterior.dsi, null);
});
