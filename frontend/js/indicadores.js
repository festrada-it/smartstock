// Presentación de los indicadores de dirección (plan-mejoras §10). Funciones puras, sin DOM:
// las usa dashboard-direccion.js y se prueban en frontend/tests/. No recalcula fórmulas,
// solo da formato a lo que devuelve analytics.

const PERIODOS = [30, 90];

// Estados del semáforo del DSI, con los mismos nombres que analytics y docs/plan-mejoras.md §10.2.
// `ayuda` es el texto del tooltip.
const SEMAFORO_DSI = {
    REPONER_YA: { icono: '⛔', texto: 'Reponer ya', ayuda: 'Alcanza para menos de 7 días. Hay que reponer.', clase: 'text-danger' },
    TODO_EN_ORDEN: { icono: '✅', texto: 'Todo en orden', ayuda: 'Stock equilibrado con las ventas.', clase: 'text-success' },
    FRENAR_COMPRAS: { icono: '⚠️', texto: 'Frenar compras', ayuda: 'Hay plata inmovilizada. Revisar las próximas compras.', clase: 'tw-text-amber-700' },
    LIQUIDAR_STOCK: { icono: '⛔', texto: 'Liquidar stock', ayuda: 'Más de 3 meses de stock. Promocionar o no recomprar.', clase: 'text-danger' },
    SIN_VENTAS: { icono: '⛔', texto: 'Sin ventas', ayuda: 'No hubo ventas en el período: el DSI no se puede calcular.', clase: 'text-danger' },
};

// Texto de los tooltips (ⓘ) de las tarjetas, para la defensa oral.
const FORMULAS = {
    dsi: 'DSI = valor del stock a costo ÷ (costo de ventas del período ÷ días del período). '
        + 'El costo de ventas usa el costo actual del producto hasta que existan lotes. '
        + 'Semáforo (configurable): menos de 7 días Reponer ya, de 7 a 45 Todo en orden, de 45 a 90 Frenar compras y más de 90 Liquidar stock. '
        + 'Sin ventas en el período no se puede calcular.',
    rotacion: 'Rotación a costo = costo de ventas del período ÷ stock promedio a costo. '
        + 'Mensual = × 30 ÷ días del período; anualizada = × 365 ÷ días del período. '
        + 'Es un indicador financiero en pesos, distinto de la "baja rotación" en unidades del grupo.',
    enRiesgo: '$ en riesgo = valor a costo de los productos vencidos, próximos a vencer, sin ventas y de baja rotación, '
        + 'contando cada producto una sola vez. Semáforo según el % del valor del stock: menos de 5% verde, hasta 10% amarillo, más rojo. '
        + 'La variación solo cubre sin ventas y baja rotación (no hay historia de lotes).',
    fillRate: 'Fill Rate = unidades entregadas ÷ unidades solicitadas × 100, en pedidos confirmados, enviados y entregados. '
        + 'Semáforo: 98% o más verde, de 95% a 98% amarillo, menos de 95% rojo.',
};

/** Arma el query string de los endpoints /api/dashboard/*. Sin grupo, no manda grupo_id. */
function queryDashboard(periodo, grupoId) {
    if (!PERIODOS.includes(Number(periodo))) throw new Error(`Período inválido: ${periodo}. Usá 30 o 90 días.`);
    const params = [`periodo=${Number(periodo)}`];
    if (grupoId) params.push(`grupo_id=${Number(grupoId)}`);
    return `?${params.join('&')}`;
}

/** Semáforo del DSI tal como lo informa analytics (`estado`), con ícono, texto y ayuda. */
function semaforoDsi({ dsi, estado }) {
    if (dsi === null || dsi === undefined) return SEMAFORO_DSI.SIN_VENTAS;
    return SEMAFORO_DSI[estado] ?? { icono: '', texto: estado ?? '-', ayuda: '', clase: 'text-muted' };
}

function formatoNumero(valor, decimales) {
    return new Intl.NumberFormat('es-AR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }).format(valor);
}

const formatoDias = dsi => (dsi === null || dsi === undefined ? 'sin ventas' : `${formatoNumero(dsi, 1)} días`);
const formatoRotacion = rotacion => (rotacion === null || rotacion === undefined ? '-' : `${formatoNumero(rotacion, 2)} veces`);

/**
 * Variación ▲/▼ del valor actual contra el período anterior. `formato` da formato a la diferencia
 * (en valor absoluto). Si la diferencia redondeada es cero, se muestra "=".
 */
function variacion(actual, anterior, formato, periodo) {
    const referencia = `vs. ${periodo} días anteriores`;
    if (actual === null || actual === undefined || anterior === null || anterior === undefined) {
        return { flecha: '', texto: `sin comparación ${referencia}` };
    }
    const diferencia = actual - anterior;
    const texto = formato(Math.abs(diferencia));
    if (diferencia === 0 || texto === formato(0)) return { flecha: '=', texto: `= sin cambios ${referencia}` };
    const flecha = diferencia > 0 ? '▲' : '▼';
    return { flecha, texto: `${flecha} ${texto} ${referencia}` };
}

if (typeof module !== 'undefined') {
    module.exports = { PERIODOS, SEMAFORO_DSI, FORMULAS, queryDashboard, semaforoDsi, formatoDias, formatoRotacion, variacion };
}
