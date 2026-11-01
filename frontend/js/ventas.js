const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);

let productos = [];
let clientes = [];
// productoId -> { producto, cantidad }
const carrito = new Map();

async function iniciar() {
    renderLayout(sesion);
    const buscador = document.getElementById('buscador');
    buscador.addEventListener('input', renderResultados);
    buscador.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const primero = filtrarProductos()[0];
            if (primero) agregar(primero.id);
        }
    });
    document.getElementById('btn-vaciar').addEventListener('click', () => { carrito.clear(); renderCarrito(); });
    document.getElementById('btn-finalizar').addEventListener('click', finalizarVenta);
    document.getElementById('cliente').addEventListener('change', renderCliente);

    await Promise.all([cargarProductos(), cargarClientes(), cargarVentasDelDia()]);
}

async function cargarClientes() {
    const selector = document.getElementById('cliente');
    try {
        clientes = await fuenteClientes.listar();
        selector.innerHTML = clientes.map(c => `<option value="${c.id}">${escaparHtml(c.nombre)}</option>`).join('');
        // Por defecto "Consumidor final", buscado por nombre (su id depende de la base).
        const porDefecto = clientes.find(esConsumidorFinal);
        if (porDefecto) selector.value = porDefecto.id;
    } catch (e) {
        selector.innerHTML = '<option value="">Sin cliente (no se pudieron cargar)</option>';
        mostrarMensaje(`No se pudieron cargar los clientes: ${e.message}`, 'warning');
    }
    renderCliente();
}

function clienteSeleccionado() {
    return clientes.find(c => c.id === Number(document.getElementById('cliente').value)) ?? null;
}

/** Muestra la clasificación del cliente de forma visible para decidir cuánto venderle. */
function renderCliente() {
    const cliente = clienteSeleccionado();
    const calificacion = document.getElementById('cliente-calificacion');
    const aviso = document.getElementById('cliente-aviso');
    if (!cliente) {
        calificacion.innerHTML = '';
        aviso.innerHTML = '';
        return;
    }
    calificacion.innerHTML = `${estrellas(cliente.clasificacion, 'tw-text-2xl')} ${badgeCalidad(cliente.calidad)}`;
    aviso.innerHTML = esDeRiesgo(cliente)
        ? '<span class="tw-font-semibold tw-text-amber-700">⚠️ Cliente de riesgo: conviene limitar la cantidad o pedir pago por adelantado.</span>'
        : `<span class="tw-text-slate-500">${cliente.clasificacion >= 4 ? 'Cliente confiable.' : 'Cliente regular.'}</span>`;
}

async function cargarProductos() {
    try {
        productos = await coreApi('/productos');
        renderResultados();
    } catch (e) {
        mostrarMensaje(`No se pudieron cargar los productos: ${e.message}`, 'danger');
    }
}

function filtrarProductos() {
    const texto = document.getElementById('buscador').value.trim().toLowerCase();
    return productos.filter(p => !texto || p.nombre.toLowerCase().includes(texto) || p.sku.toLowerCase().includes(texto));
}

function renderResultados() {
    const contenedor = document.getElementById('resultados');
    contenedor.innerHTML = filtrarProductos().map(p => {
        const sinStock = p.stockActual === 0;
        return `
            <button type="button" class="list-group-item list-group-item-action tw-flex tw-justify-between tw-items-center"
                    data-id="${p.id}" ${sinStock ? 'disabled' : ''}>
                <span>
                    <span class="tw-font-semibold">${escaparHtml(p.nombre)}</span>
                    <span class="tw-block tw-text-xs tw-text-slate-500">${escaparHtml(p.sku)} · stock ${p.stockActual}${sinStock ? ' · ⛔ sin stock' : ''}</span>
                </span>
                <span class="tw-tabular-nums">${formatoMoneda(p.precio)}</span>
            </button>`;
    }).join('') || '<div class="tw-text-sm tw-text-slate-500">Sin resultados</div>';
    contenedor.querySelectorAll('[data-id]').forEach(b => b.addEventListener('click', () => agregar(Number(b.dataset.id))));
}

function agregar(productoId) {
    const producto = productos.find(p => p.id === productoId);
    const item = carrito.get(productoId) ?? { producto, cantidad: 0 };
    if (item.cantidad + 1 > producto.stockActual) {
        mostrarMensaje(`No hay más stock de ${producto.nombre} (disponible: ${producto.stockActual})`, 'warning');
        return;
    }
    item.cantidad += 1;
    carrito.set(productoId, item);
    renderCarrito();
}

function renderCarrito() {
    const cuerpo = document.getElementById('carrito');
    let total = 0;
    cuerpo.innerHTML = [...carrito.values()].map(({ producto, cantidad }) => {
        const subtotal = producto.precio * cantidad;
        total += subtotal;
        const aviso = avisoStockInsuficiente(cantidad, producto.stockActual);
        return `
            <tr>
                <td>${escaparHtml(producto.nombre)}</td>
                <td><input type="number" class="form-control form-control-sm${aviso ? ' is-invalid' : ''}" min="1" step="1"
                           value="${cantidad}" data-cantidad="${producto.id}" aria-label="Cantidad de ${escaparHtml(producto.nombre)}">
                    ${aviso ? `<div class="tw-mt-1 tw-text-xs tw-font-semibold text-danger" role="alert">⛔ ${aviso}</div>` : ''}</td>
                <td class="text-end">${formatoMoneda(producto.precio)}</td>
                <td class="text-end">${formatoMoneda(subtotal)}</td>
                <td class="text-end"><button class="btn btn-sm btn-link text-danger" data-quitar="${producto.id}" title="Quitar">✕</button></td>
            </tr>`;
    }).join('') || '<tr><td colspan="5" class="text-center text-muted">Buscá un producto y hacé click para agregarlo</td></tr>';

    document.getElementById('total').textContent = formatoMoneda(total);
    // No se puede finalizar mientras alguna línea pida más que el stock (#72).
    document.getElementById('btn-finalizar').disabled = carrito.size === 0 || lineasSinStock([...carrito.values()]).length > 0;

    cuerpo.querySelectorAll('[data-cantidad]').forEach(input => input.addEventListener('change', () => {
        // Se respeta lo que escribió el vendedor; si supera el stock, se avisa en la línea.
        carrito.get(Number(input.dataset.cantidad)).cantidad = cantidadIngresada(input.value);
        renderCarrito();
    }));
    cuerpo.querySelectorAll('[data-quitar]').forEach(boton => boton.addEventListener('click', () => {
        carrito.delete(Number(boton.dataset.quitar));
        renderCarrito();
    }));
}

async function finalizarVenta() {
    const boton = document.getElementById('btn-finalizar');
    if (lineasSinStock([...carrito.values()]).length) return;
    boton.disabled = true;
    try {
        const cliente = clienteSeleccionado();
        const venta = await coreApi('/ventas', {
            method: 'POST',
            body: {
                clienteId: cliente?.id ?? null,
                items: [...carrito.values()].map(i => ({ productoId: i.producto.id, cantidad: i.cantidad })),
            },
        });
        carrito.clear();
        renderCarrito();
        mostrarMensaje(`Venta #${venta.id} a ${venta.cliente ?? cliente?.nombre ?? 'sin cliente'} registrada por ${formatoMoneda(venta.total)}`);
        document.getElementById('buscador').value = '';
        await Promise.all([cargarProductos(), cargarVentasDelDia()]);
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
        boton.disabled = false;
    }
}

async function cargarVentasDelDia() {
    const ventas = await coreApi('/ventas/hoy');
    document.getElementById('ventas-hoy').innerHTML = ventas.map(v => `
        <tr>
            <td>${v.id}</td>
            <td>${v.fecha.slice(11, 16)}</td>
            <td>${escaparHtml(v.cliente ?? '-')}</td>
            <td>${escaparHtml(v.vendedor)}</td>
            <td class="tw-text-sm">${v.items.map(i => `${i.cantidad} × ${escaparHtml(i.producto)}`).join(', ')}</td>
            <td class="text-end">${formatoMoneda(v.total)}</td>
        </tr>`).join('') || '<tr><td colspan="6" class="text-center text-muted">Todavía no hay ventas hoy</td></tr>';
}

if (sesion) {
    iniciar();
    renderCarrito();
}
