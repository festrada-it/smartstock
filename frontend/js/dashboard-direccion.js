// Filtros, tarjetas y pestaña "Salud del inventario" del monitor (plan-mejoras §10.5, #60).
// Solo muestra lo que calcula analytics; el formato sale de indicadores.js.

const fuenteDashboard = {
    inventario: (periodo, grupoId, grupos) => (CONFIG.DATOS_EJEMPLO.dashboardInventario
        ? Promise.resolve(DashboardEjemplo.inventario(periodo, grupoId, grupos))
        : analyticsApi(`/dashboard/inventario${queryDashboard(periodo, grupoId)}`)),
};

let gruposDireccion = [];
let tooltipsDireccion = [];

async function iniciarDireccion() {
    if (CONFIG.DATOS_EJEMPLO.dashboardInventario) document.getElementById('aviso-ejemplo-dashboard').classList.remove('d-none');
    document.getElementById('filtro-periodo').addEventListener('change', cargarDireccion);
    document.getElementById('filtro-grupo').addEventListener('change', cargarDireccion);
    try {
        gruposDireccion = await coreApi('/categorias');
        document.getElementById('filtro-grupo').insertAdjacentHTML('beforeend',
            gruposDireccion.map(g => `<option value="${g.id}">${escaparHtml(g.nombre)}</option>`).join(''));
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los grupos para el filtro: ${e.message}`, 'warning');
    }
    await cargarDireccion();
}

async function cargarDireccion() {
    const periodo = Number(document.getElementById('filtro-periodo').value);
    const grupoId = document.getElementById('filtro-grupo').value || null;
    try {
        const inventario = await fuenteDashboard.inventario(periodo, grupoId, gruposDireccion);
        renderTarjetas(inventario, periodo);
        renderSalud(inventario);
    } catch (e) {
        renderTarjetas(null, periodo);
        document.getElementById('salud-totales').innerHTML = '';
        document.getElementById('tabla-salud-grupos').innerHTML = filaVacia(7, 'No se pudo cargar la salud del inventario.');
        document.getElementById('tabla-salud-productos').innerHTML = filaVacia(7, 'No se pudo cargar la salud del inventario.');
        mostrarMensaje(`No se pudieron cargar los indicadores de dirección: ${e.message}`, 'danger');
    }
}

function filaVacia(columnas, texto) {
    return `<tr><td colspan="${columnas}" class="text-center text-muted">${texto}</td></tr>`;
}

function etiquetaSemaforo(item) {
    const s = semaforoDsi(item);
    return `<span class="${s.clase} tw-whitespace-nowrap">${s.icono} ${s.texto}</span>`;
}

function botonFormula(titulo, formula) {
    return `<button type="button" class="btn btn-link btn-sm tw-p-0 tw-align-baseline tw-text-slate-400 tw-no-underline"
                    data-bs-toggle="tooltip" data-bs-placement="top" title="${escaparHtml(formula)}"
                    aria-label="Fórmula de ${escaparHtml(titulo)}: ${escaparHtml(formula)}">ⓘ</button>`;
}

function tarjeta({ titulo, formula, valor, detalle = '', semaforo = '', variacion = null }) {
    return `
        <div class="tarjeta">
            <div class="tw-flex tw-items-center tw-gap-1 tw-text-sm tw-text-slate-500">${titulo} ${botonFormula(titulo, formula)}</div>
            <div class="tw-text-2xl tw-font-bold tw-text-slate-800">${valor}</div>
            ${detalle ? `<div class="tw-text-sm tw-text-slate-500">${detalle}</div>` : ''}
            ${semaforo ? `<div class="tw-mt-1 tw-text-sm tw-font-semibold">${semaforo}</div>` : ''}
            ${variacion ? `<div class="tw-mt-1 tw-text-xs tw-text-slate-600">${variacion.texto}</div>` : ''}
        </div>`;
}

function tarjetaProximamente(titulo, formula, issue) {
    return tarjeta({ titulo, formula, valor: '<span class="tw-text-base tw-font-semibold tw-text-slate-400">Disponible próximamente</span>',
                     detalle: `Se completa en ${issue}.` });
}

function renderTarjetas(inventario, periodo) {
    const sinDatos = { titulo: '', valor: '-' };
    const dsi = inventario
        ? { valor: formatoDias(inventario.total.dsi), semaforo: etiquetaSemaforo(inventario.total),
            variacion: variacion(inventario.total.dsi, inventario.anterior.dsi, formatoDias, periodo) }
        : sinDatos;
    // Rotación sin semáforo (P12): solo el valor y la variación.
    const rotacion = inventario
        ? { valor: `${formatoRotacion(inventario.total.rotacion_mensual)} <span class="tw-text-sm tw-font-normal tw-text-slate-500">por mes</span>`,
            detalle: `Anualizada: ${formatoRotacion(inventario.total.rotacion_anual)} por año`,
            variacion: variacion(inventario.total.rotacion_mensual, inventario.anterior.rotacion_mensual, formatoRotacion, periodo) }
        : sinDatos;

    tooltipsDireccion.forEach(t => t.dispose());
    document.getElementById('tarjetas-direccion').innerHTML = [
        tarjeta({ ...dsi, titulo: 'DSI (días de inventario)', formula: FORMULAS.dsi }),
        tarjeta({ ...rotacion, titulo: 'Rotación del inventario', formula: FORMULAS.rotacion }),
        tarjetaProximamente('$ en riesgo', FORMULAS.enRiesgo, '#61'),
        tarjetaProximamente('Fill Rate', FORMULAS.fillRate, '#62'),
    ].join('');
    tooltipsDireccion = [...document.querySelectorAll('#tarjetas-direccion [data-bs-toggle="tooltip"]')]
        .map(el => new bootstrap.Tooltip(el));
}

function totalSalud(titulo, valor, extra = '') {
    return `
        <div class="tw-rounded-lg tw-border tw-border-solid tw-border-slate-200 tw-p-3">
            <div class="tw-text-xs tw-text-slate-500">${titulo}</div>
            <div class="tw-text-lg tw-font-bold tw-text-slate-800">${valor}</div>
            ${extra ? `<div class="tw-text-xs">${extra}</div>` : ''}
        </div>`;
}

// analytics ya devuelve por_grupo y por_producto de peor a mejor: se muestran en ese orden.
function renderSalud({ total, por_grupo: porGrupo, por_producto: porProducto }) {
    document.getElementById('salud-totales').innerHTML = [
        totalSalud('Valor del stock a costo', formatoMoneda(total.valor_stock)),
        totalSalud('Costo de ventas del período', formatoMoneda(total.costo_ventas)),
        totalSalud('DSI total', formatoDias(total.dsi), etiquetaSemaforo(total)),
        totalSalud('Rotación total', `${formatoRotacion(total.rotacion_mensual)} / mes`,
                   `<span class="tw-text-slate-500">${formatoRotacion(total.rotacion_anual)} / año</span>`),
    ].join('');

    document.getElementById('tabla-salud-grupos').innerHTML = porGrupo.length
        ? porGrupo.map(g => `
            <tr>
                <td class="fw-semibold">${escaparHtml(g.grupo)}</td>
                <td>${etiquetaSemaforo(g)}</td>
                <td class="text-end">${formatoDias(g.dsi)}</td>
                <td class="text-end">${formatoRotacion(g.rotacion_mensual)}</td>
                <td class="text-end">${formatoRotacion(g.rotacion_anual)}</td>
                <td class="text-end">${formatoMoneda(g.valor_stock)}</td>
                <td class="text-end">${formatoMoneda(g.costo_ventas)}</td>
            </tr>`).join('')
        : filaVacia(7, 'Sin datos para el filtro elegido.');

    document.getElementById('tabla-salud-productos').innerHTML = porProducto.length
        ? porProducto.map(p => `
            <tr>
                <td class="tw-font-mono">${escaparHtml(p.sku)}</td>
                <td>${escaparHtml(p.nombre)}</td>
                <td>${escaparHtml(p.grupo)}</td>
                <td>${etiquetaSemaforo(p)}</td>
                <td class="text-end">${formatoDias(p.dsi)}</td>
                <td class="text-end">${formatoRotacion(p.rotacion_mensual)}</td>
                <td class="text-end">${formatoMoneda(p.valor_stock)}</td>
            </tr>`).join('')
        : filaVacia(7, 'Sin datos para el filtro elegido.');
}

if (sesion) iniciarDireccion();
