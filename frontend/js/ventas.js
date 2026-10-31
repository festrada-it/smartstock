const sesion = Sesion.requerir(['ADMIN', 'VENDEDOR']);

let productos = [];
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

    await Promise.all([cargarProductos(), cargarVentasDelDia()]);
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
        return `
            <tr>
                <td>${escaparHtml(producto.nombre)}</td>
                <td><input type="number" class="form-control form-control-sm" min="1" max="${producto.stockActual}"
                           value="${cantidad}" data-cantidad="${producto.id}"></td>
                <td class="text-end">${formatoMoneda(producto.precio)}</td>
                <td class="text-end">${formatoMoneda(subtotal)}</td>
                <td class="text-end"><button class="btn btn-sm btn-link text-danger" data-quitar="${producto.id}" title="Quitar">✕</button></td>
            </tr>`;
    }).join('') || '<tr><td colspan="5" class="text-center text-muted">Buscá un producto y hacé click para agregarlo</td></tr>';

    document.getElementById('total').textContent = formatoMoneda(total);
    document.getElementById('btn-finalizar').disabled = carrito.size === 0;

    cuerpo.querySelectorAll('[data-cantidad]').forEach(input => input.addEventListener('change', () => {
        const item = carrito.get(Number(input.dataset.cantidad));
        const cantidad = Math.max(1, Math.min(Number(input.value) || 1, item.producto.stockActual));
        item.cantidad = cantidad;
        renderCarrito();
    }));
    cuerpo.querySelectorAll('[data-quitar]').forEach(boton => boton.addEventListener('click', () => {
        carrito.delete(Number(boton.dataset.quitar));
        renderCarrito();
    }));
}

async function finalizarVenta() {
    const boton = document.getElementById('btn-finalizar');
    boton.disabled = true;
    try {
        const venta = await coreApi('/ventas', {
            method: 'POST',
            body: { items: [...carrito.values()].map(i => ({ productoId: i.producto.id, cantidad: i.cantidad })) },
        });
        carrito.clear();
        renderCarrito();
        mostrarMensaje(`Venta #${venta.id} registrada por ${formatoMoneda(venta.total)}`);
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
            <td>${escaparHtml(v.vendedor)}</td>
            <td class="tw-text-sm">${v.items.map(i => `${i.cantidad} × ${escaparHtml(i.producto)}`).join(', ')}</td>
            <td class="text-end">${formatoMoneda(v.total)}</td>
        </tr>`).join('') || '<tr><td colspan="5" class="text-center text-muted">Todavía no hay ventas hoy</td></tr>';
}

if (sesion) {
    iniciar();
    renderCarrito();
}
