const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);
const esAdmin = Sesion.esAdmin();
const TAMANIO_PAGINA = 20;
const NOMBRE_DISTRIBUIDOR = 'SmartStock · Distribuidora mayorista';

let productos = [];
let clientes = [];
// productoId -> { producto, cantidad }
const lineas = new Map();
let pedidos = [];
let pagina = 0;
let totalPaginas = 0;
let modalDetalle;
let modalFecha;

async function iniciar() {
    renderLayout(sesion);
    modalDetalle = new bootstrap.Modal('#modal-detalle');
    modalFecha = new bootstrap.Modal('#modal-fecha');
    if (fuentePedidos === PedidosEjemplo) document.getElementById('aviso-ejemplo').classList.remove('d-none');

    document.getElementById('forma-pago').innerHTML = Object.entries(FORMAS_PAGO).map(([v, t]) => `<option value="${v}">${t}</option>`).join('');
    document.getElementById('filtro-estado').insertAdjacentHTML('beforeend', Object.entries(ESTADOS_PEDIDO).map(([v, e]) => `<option value="${v}">${e.texto}</option>`).join(''));
    const hoy = new Date().toISOString().slice(0, 10);
    document.getElementById('fecha-envio').min = hoy;
    document.getElementById('fecha-nueva').min = hoy;

    const buscador = document.getElementById('buscador');
    buscador.addEventListener('input', renderResultados);
    buscador.addEventListener('keydown', e => {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        const primero = filtrarProductos()[0];
        if (primero) agregar(primero.id);
    });
    document.getElementById('cliente').addEventListener('change', renderCliente);
    document.getElementById('con-descuento').addEventListener('change', e => {
        document.getElementById('descuento').disabled = !e.target.checked;
        renderLineas();
    });
    document.getElementById('descuento').addEventListener('input', renderLineas);
    document.getElementById('btn-vaciar').addEventListener('click', vaciar);
    document.getElementById('btn-crear').addEventListener('click', crearPedido);
    document.getElementById('filtros').addEventListener('submit', e => { e.preventDefault(); cargarPedidos(0); });
    document.getElementById('btn-mas').addEventListener('click', () => cargarPedidos(pagina + 1));
    document.getElementById('form-fecha').addEventListener('submit', guardarFecha);
    document.getElementById('btn-imprimir').addEventListener('click', () => window.print());

    await Promise.all([cargarProductos(), cargarClientes()]);
    if (fuentePedidos === PedidosEjemplo) PedidosEjemplo.inicializar(productos, clientes);
    renderLineas();
    await cargarPedidos(0);
}

// ---------- Armado ----------

async function cargarProductos() {
    try {
        productos = await coreApi('/productos');
        renderResultados();
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los productos: ${e.message}`, 'danger');
    }
}

async function cargarClientes() {
    const selector = document.getElementById('cliente');
    try {
        clientes = await fuenteClientes.listar();
        const opciones = clientes.map(c => `<option value="${c.id}">${escaparHtml(c.nombre)}</option>`).join('');
        selector.innerHTML = '<option value="">Elegí un cliente</option>' + opciones;
        document.getElementById('filtro-cliente').insertAdjacentHTML('beforeend', opciones);
    } catch (e) {
        selector.innerHTML = '<option value="">No se pudieron cargar los clientes</option>';
        mostrarMensaje(`No se pudieron cargar los clientes: ${e.message}`, 'warning');
    }
    renderCliente();
}

const clienteSeleccionado = () => clientes.find(c => c.id === Number(document.getElementById('cliente').value)) ?? null;

/** Clasificación bien visible para decidir cuánto venderle. */
function renderCliente() {
    const cliente = clienteSeleccionado();
    const calificacion = document.getElementById('cliente-calificacion');
    const aviso = document.getElementById('cliente-aviso');
    calificacion.innerHTML = cliente ? `${estrellas(cliente.clasificacion, 'tw-text-2xl')} ${badgeCalidad(cliente.calidad)}` : '';
    aviso.innerHTML = !cliente ? '<span class="tw-text-slate-500">El cliente es obligatorio.</span>'
        : esDeRiesgo(cliente) ? '<span class="tw-font-semibold tw-text-amber-700">⚠️ Cliente de riesgo: conviene limitar la cantidad o pedir pago por adelantado.</span>'
        : `<span class="tw-text-slate-500">${cliente.clasificacion >= 4 ? 'Cliente confiable.' : 'Cliente regular.'}</span>`;
    renderLineas();
}

function filtrarProductos() {
    const texto = document.getElementById('buscador').value.trim().toLowerCase();
    return productos.filter(p => !texto || [p.nombre, p.sku, p.marca].some(c => c?.toLowerCase().includes(texto)));
}

function renderResultados() {
    const contenedor = document.getElementById('resultados');
    contenedor.innerHTML = filtrarProductos().map(p => `
        <button type="button" class="list-group-item list-group-item-action tw-flex tw-items-center tw-justify-between" data-id="${p.id}">
            <span>
                <span class="tw-font-semibold">${escaparHtml(p.nombre)}</span>
                <span class="tw-block tw-text-xs tw-text-slate-500">${escaparHtml(p.sku)}${p.marca ? ` · ${escaparHtml(p.marca)}` : ''} · stock ${p.stockActual}</span>
            </span>
            <span class="tw-tabular-nums">${formatoMoneda(p.precio)}</span>
        </button>`).join('') || '<div class="tw-text-sm tw-text-slate-500">Sin resultados</div>';
    contenedor.querySelectorAll('[data-id]').forEach(b => b.addEventListener('click', () => agregar(Number(b.dataset.id))));
}

// Al crear no se descuenta stock (queda PENDIENTE): no se limita por el stock actual; core lo valida al confirmar.
function agregar(productoId) {
    const producto = productos.find(p => p.id === productoId);
    const linea = lineas.get(productoId) ?? { producto, cantidad: 0 };
    linea.cantidad += 1;
    lineas.set(productoId, linea);
    renderLineas();
}

function descuentoActual() {
    if (!document.getElementById('con-descuento').checked) return 0;
    const valor = Math.trunc(Number(document.getElementById('descuento').value));
    return Number.isFinite(valor) ? valor : 0;
}

function renderLineas() {
    const cuerpo = document.getElementById('lineas');
    const items = [...lineas.values()];
    cuerpo.innerHTML = items.map(({ producto, cantidad }) => `
        <tr>
            <td>${escaparHtml(producto.nombre)}<span class="tw-block tw-text-xs tw-text-slate-500">${escaparHtml(producto.marca || '')}</span></td>
            <td><input type="number" class="form-control form-control-sm" min="1" step="1" value="${cantidad}" data-cantidad="${producto.id}" aria-label="Cantidad de ${escaparHtml(producto.nombre)}"></td>
            <td class="text-end">${formatoMoneda(producto.precio)}</td>
            <td class="text-end">${formatoMoneda(producto.precio * cantidad)}</td>
            <td class="text-end"><button class="btn btn-sm btn-link text-danger" data-quitar="${producto.id}" title="Quitar">✕</button></td>
        </tr>`).join('') || '<tr><td colspan="5" class="text-center text-muted">Buscá un producto y hacé click para agregarlo</td></tr>';

    const descuento = descuentoActual();
    const descuentoValido = descuento === 0 || (descuento >= 1 && descuento <= DESCUENTO_MAXIMO);
    document.getElementById('descuento').classList.toggle('is-invalid', !descuentoValido);
    const totales = calcularTotales(items.map(l => ({ cantidad: l.cantidad, precioUnitario: l.producto.precio })), descuentoValido ? descuento : 0);
    document.getElementById('subtotal').textContent = formatoMoneda(totales.subtotal);
    document.getElementById('importe-descuento').textContent = `− ${formatoMoneda(totales.importeDescuento)}${descuento ? ` (${descuento}%)` : ''}`;
    document.getElementById('total').textContent = formatoMoneda(totales.total);
    document.getElementById('btn-crear').disabled = !items.length || !clienteSeleccionado() || !descuentoValido;

    cuerpo.querySelectorAll('[data-cantidad]').forEach(input => input.addEventListener('change', () => {
        // Solo enteros: core rechaza cantidades con decimales.
        lineas.get(Number(input.dataset.cantidad)).cantidad = Math.max(1, Math.trunc(Number(input.value)) || 1);
        renderLineas();
    }));
    cuerpo.querySelectorAll('[data-quitar]').forEach(b => b.addEventListener('click', () => { lineas.delete(Number(b.dataset.quitar)); renderLineas(); }));
}

function vaciar() {
    lineas.clear();
    document.getElementById('con-descuento').checked = false;
    document.getElementById('descuento').disabled = true;
    document.getElementById('fecha-envio').value = '';
    renderLineas();
}

async function crearPedido() {
    const boton = document.getElementById('btn-crear');
    boton.disabled = true;
    try {
        const pedido = await fuentePedidos.crear({
            clienteId: clienteSeleccionado().id,
            formaPago: document.getElementById('forma-pago').value,
            descuentoPct: descuentoActual(),
            fechaEnvio: document.getElementById('fecha-envio').value || null,
            items: [...lineas.values()].map(l => ({ productoId: l.producto.id, cantidad: l.cantidad })),
        });
        vaciar();
        mostrarMensaje(`Pedido ${pedido.numeroOrden} creado para ${pedido.cliente} por ${formatoMoneda(pedido.total)}. Queda pendiente de confirmar.`);
        await cargarPedidos(0);
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
        renderLineas();
    }
}

// ---------- Lista y acciones ----------

function filtrosActuales() {
    return {
        estado: document.getElementById('filtro-estado').value,
        clienteId: document.getElementById('filtro-cliente').value,
        desde: document.getElementById('filtro-desde').value,
        hasta: document.getElementById('filtro-hasta').value,
        numeroOrden: document.getElementById('filtro-orden').value.trim().toUpperCase(),
    };
}

async function cargarPedidos(nuevaPagina) {
    try {
        const respuesta = await fuentePedidos.listar(filtrosActuales(), nuevaPagina, TAMANIO_PAGINA);
        pedidos = nuevaPagina === 0 ? respuesta.contenido : [...pedidos, ...respuesta.contenido];
        pagina = respuesta.pagina;
        totalPaginas = respuesta.totalPaginas;
        renderPedidos();
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los pedidos: ${e.message}`, 'danger');
    }
}

const badge = (mapa, clave) => clave ? `<span class="badge ${mapa[clave]?.clase ?? 'text-bg-secondary'}">${mapa[clave]?.texto ?? escaparHtml(clave)}</span>` : '-';

/** Acciones posibles según el estado y el rol (cancelar solo ADMIN, D6). */
function acciones(p) {
    const a = [`<button class="btn btn-sm btn-outline-primary" data-accion="ver" data-id="${p.id}">Ver</button>`];
    if (p.estado === 'PENDIENTE') a.push(`<button class="btn btn-sm btn-marca" data-accion="confirmar" data-id="${p.id}">Confirmar</button>`);
    if (['PENDIENTE', 'CONFIRMADO'].includes(p.estado)) a.push(`<button class="btn btn-sm btn-outline-secondary" data-accion="fecha" data-id="${p.id}">Pactar fecha</button>`);
    if (p.estado === 'CONFIRMADO') a.push(`<button class="btn btn-sm btn-outline-info" data-accion="enviar" data-id="${p.id}">Enviar</button>`);
    if (p.estado === 'ENVIADO') a.push(`<button class="btn btn-sm btn-outline-success" data-accion="entregar" data-id="${p.id}">Entregar</button>`);
    if (['CONFIRMADO', 'ENVIADO', 'ENTREGADO'].includes(p.estado)) a.push(`<button class="btn btn-sm btn-outline-dark" data-accion="comprobante" data-id="${p.id}">Comprobante</button>`);
    if (p.estado === 'ENTREGADO') a.push(`<button class="btn btn-sm btn-outline-dark" data-accion="factura" data-id="${p.id}">Factura</button>`);
    if (esAdmin && ['PENDIENTE', 'CONFIRMADO'].includes(p.estado)) a.push(`<button class="btn btn-sm btn-outline-danger" data-accion="cancelar" data-id="${p.id}">Cancelar</button>`);
    return a.join(' ');
}

function renderPedidos() {
    const cuerpo = document.getElementById('tabla-pedidos');
    cuerpo.innerHTML = pedidos.map(p => `
        <tr>
            <td class="tw-font-mono tw-font-semibold">${escaparHtml(p.numeroOrden)}</td>
            <td>${formatoFecha(p.fecha)}</td>
            <td>${escaparHtml(p.cliente)}</td>
            <td>${badge(ESTADOS_PEDIDO, p.estado)}</td>
            <td>${badge(ESTADOS_ENTREGA, p.estadoEntrega)}${p.fechaEnvio ? `<span class="tw-block tw-text-xs tw-text-slate-500">${formatoFecha(p.fechaEnvio)}</span>` : ''}</td>
            <td class="text-end">${formatoMoneda(p.total)}</td>
            <td class="text-end"><div class="tw-flex tw-flex-wrap tw-justify-end tw-gap-1">${acciones(p)}</div></td>
        </tr>`).join('') || '<tr><td colspan="7" class="text-center text-muted">No hay pedidos con esos filtros.</td></tr>';
    document.getElementById('btn-mas').classList.toggle('d-none', pagina + 1 >= totalPaginas);
    cuerpo.querySelectorAll('[data-accion]').forEach(b => b.addEventListener('click', () => ejecutar(b.dataset.accion, Number(b.dataset.id))));
}

async function ejecutar(accion, id) {
    const pedido = pedidos.find(p => p.id === id);
    try {
        if (accion === 'ver') return mostrarDocumento(await fuentePedidos.obtener(id), 'detalle');
        if (accion === 'comprobante') return mostrarDocumento(await fuentePedidos.obtener(id), 'comprobante');
        if (accion === 'factura') return mostrarDocumento(await fuentePedidos.obtener(id), 'factura');
        if (accion === 'fecha') {
            document.getElementById('fecha-pedido-id').value = id;
            document.getElementById('fecha-nueva').value = pedido.fechaEnvio ?? '';
            return modalFecha.show();
        }
        if (accion === 'confirmar') {
            const confirmado = await fuentePedidos.confirmar(id);
            mostrarMensaje(`Orden ${confirmado.numeroOrden} confirmada. Ya se descontó el stock.`);
            mostrarFaltantes(confirmado);
            (confirmado.advertencias ?? []).forEach(a => mostrarMensaje(`Stock mínimo: ${a}`, 'warning'));
        }
        if (accion === 'enviar') mostrarMensaje(`Orden ${(await fuentePedidos.enviar(id)).numeroOrden} enviada.`);
        if (accion === 'entregar') {
            const entregado = await fuentePedidos.entregar(id);
            mostrarMensaje(`Orden ${entregado.numeroOrden} entregada. Factura ${entregado.numeroFactura}.`);
        }
        if (accion === 'cancelar') {
            if (!confirm(`¿Cancelar la orden ${pedido.numeroOrden}?${pedido.estado === 'CONFIRMADO' ? ' Se devuelve el stock.' : ''}`)) return;
            mostrarMensaje(`Orden ${(await fuentePedidos.cancelar(id)).numeroOrden} cancelada.`);
        }
        await Promise.all([cargarPedidos(0), cargarProductos()]);
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

/** Confirmación parcial (#24 ampliado): si core devuelve cantidades solicitadas, se muestra el faltante. */
function mostrarFaltantes(pedido) {
    const conFaltante = pedido.items.filter(i => i.cantidadSolicitada != null && i.cantidadSolicitada > i.cantidad);
    if (!conFaltante.length) return;
    mostrarMensaje(`Se confirmó con el stock disponible. Faltante: ${conFaltante
        .map(i => `${i.producto} (pedido ${i.cantidadSolicitada}, entregado ${i.cantidad})`).join('; ')}.`, 'warning');
}

async function guardarFecha(evento) {
    evento.preventDefault();
    try {
        const pedido = await fuentePedidos.pactarFecha(Number(document.getElementById('fecha-pedido-id').value), document.getElementById('fecha-nueva').value);
        modalFecha.hide();
        mostrarMensaje(`Fecha de envío de ${pedido.numeroOrden} pactada para el ${formatoFecha(pedido.fechaEnvio)}.`);
        await cargarPedidos(0);
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

// ---------- Detalle, comprobante y factura ----------

const TITULOS = { detalle: 'Detalle del pedido', comprobante: 'Orden de compra confirmada', factura: 'Factura' };

function mostrarDocumento(p, tipo) {
    const cliente = clientes.find(c => c.id === p.clienteId);
    const hayParcial = p.items.some(i => i.cantidadSolicitada != null && i.cantidadSolicitada !== i.cantidad);
    document.getElementById('area-impresion').innerHTML = `
        <div class="tw-mb-4 tw-flex tw-flex-wrap tw-items-start tw-justify-between tw-gap-2">
            <div>
                <div class="tw-text-sm tw-text-slate-500">${NOMBRE_DISTRIBUIDOR}</div>
                <h3 class="tw-m-0 tw-text-xl tw-font-bold">${TITULOS[tipo]}</h3>
            </div>
            <div class="text-end">
                <div class="tw-font-mono tw-text-lg tw-font-bold">${escaparHtml(tipo === 'factura' ? p.numeroFactura : p.numeroOrden)}</div>
                ${tipo === 'factura' ? `<div class="tw-text-sm">Orden ${escaparHtml(p.numeroOrden)}</div>` : ''}
                <div class="tw-text-sm">Fecha del pedido: ${formatoFecha(p.fecha)}</div>
                <div class="tw-mt-1">${badge(ESTADOS_PEDIDO, p.estado)} ${badge(ESTADOS_ENTREGA, p.estadoEntrega)}</div>
            </div>
        </div>
        <dl class="row tw-mb-3">
            <dt class="col-sm-3">Cliente</dt><dd class="col-sm-9">${escaparHtml(p.cliente)} ${estrellas(p.clienteClasificacion ?? 0)}</dd>
            <dt class="col-sm-3">Dirección</dt><dd class="col-sm-9">${escaparHtml(cliente?.direccion || '-')}</dd>
            <dt class="col-sm-3">Teléfono</dt><dd class="col-sm-9">${escaparHtml(cliente?.telefono || '-')}</dd>
            <dt class="col-sm-3">Forma de pago</dt><dd class="col-sm-9">${FORMAS_PAGO[p.formaPago] ?? escaparHtml(p.formaPago)}</dd>
            <dt class="col-sm-3">Fecha de envío</dt><dd class="col-sm-9">${p.fechaEnvio ? formatoFecha(p.fechaEnvio) : 'A convenir'}</dd>
        </dl>
        <table class="table table-sm align-middle tw-tabular-nums">
            <thead><tr><th>Producto</th><th>Marca</th>${hayParcial ? '<th class="text-end">Solicitado</th>' : ''}<th class="text-end">Cantidad</th><th class="text-end">Precio unit.</th><th class="text-end">Subtotal</th></tr></thead>
            <tbody>${p.items.map(i => `
                <tr>
                    <td>${escaparHtml(i.producto)}</td><td>${escaparHtml(i.marca || '-')}</td>
                    ${hayParcial ? `<td class="text-end">${i.cantidadSolicitada ?? i.cantidad}</td>` : ''}
                    <td class="text-end">${i.cantidad}</td>
                    <td class="text-end">${formatoMoneda(i.precioUnitario)}</td><td class="text-end">${formatoMoneda(i.subtotal)}</td>
                </tr>`).join('')}</tbody>
        </table>
        <dl class="tw-ml-auto tw-grid tw-max-w-xs tw-grid-cols-2 tw-gap-x-4 tw-tabular-nums">
            <dt class="tw-font-normal">Subtotal</dt><dd class="text-end tw-mb-0">${formatoMoneda(p.subtotal)}</dd>
            <dt class="tw-font-normal">Descuento</dt><dd class="text-end tw-mb-0">${p.conDescuento ? `${p.descuentoPct}% (− ${formatoMoneda(p.importeDescuento)})` : 'Sin descuento'}</dd>
            <dt class="tw-text-lg">Total</dt><dd class="text-end tw-mb-0 tw-text-lg tw-font-bold">${formatoMoneda(p.total)}</dd>
        </dl>
        ${tipo === 'comprobante' ? '<p class="tw-mb-0 tw-mt-4 tw-text-center tw-text-sm tw-font-semibold">Documento no válido como factura</p>' : ''}`;
    document.getElementById('btn-imprimir').classList.toggle('d-none', tipo === 'detalle');
    modalDetalle.show();
}

if (sesion) iniciar();
