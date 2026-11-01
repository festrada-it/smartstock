// Presentación de los indicadores de dirección (plan-mejoras §10). Funciones puras, sin DOM:
// las usa dashboard-direccion.js y se prueban en frontend/tests/. No recalcula fórmulas,
// solo da formato a lo que devuelve analytics.

const PERIODOS = [30, 90];

const SEMAFORO_DSI = {
    RIESGO_QUIEBRE: { icono: '⛔', texto: 'Riesgo de quiebre', clase: 'text-danger' },
    DINERO_ATRAPADO_ROJO: { icono: '⛔', texto: 'Dinero atrapado', clase: 'text-danger' },
    DINERO_ATRAPADO: { icono: '⚠️', texto: 'Dinero atrapado', clase: 'tw-text-amber-700' },
    SIN_VENTAS: { icono: '⛔', texto: 'Sin ventas', clase: 'text-danger' },
    SALUDABLE: { icono: '✅', texto: 'Saludable', clase: 'text-success' },
};

// Texto de los tooltips (ⓘ) de las tarjetas, para la defensa oral.
const FORMULAS = {
    dsi: 'DSI = valor del stock a costo ÷ (costo de ventas del período ÷ días del período). '
        + 'El costo de ventas usa el costo actual del producto hasta que existan lotes. '
        + 'Semáforo (configurable): menos de 7 días rojo (riesgo de quiebre), de 45 amarillo y de 90 rojo (dinero atrapado). '
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

/** Semáforo del DSI tal como lo informa analytics (`semaforo` + `motivo`), con ícono y texto. */
function semaforoDsi({ dsi, semaforo, motivo }) {
    if (dsi === null || dsi === undefined || motivo === 'SIN_VENTAS') return SEMAFORO_DSI.SIN_VENTAS;
    if (semaforo === 'VERDE') return SEMAFORO_DSI.SALUDABLE;
    if (semaforo === 'AMARILLO') return SEMAFORO_DSI.DINERO_ATRAPADO;
    return motivo === 'RIESGO_QUIEBRE' ? SEMAFORO_DSI.RIESGO_QUIEBRE : SEMAFORO_DSI.DINERO_ATRAPADO_ROJO;
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
