// Datos de ejemplo de pedidos mientras no se mergea #52 (#24) ni #25.
// Respetan el contrato de docs/API-PEDIDOS.md: cabecera y detalle completos, envelope paginado,
// confirmación todo-o-nada con advertencias de stock mínimo (stock <= mínimo), y los estados de #25.
// Necesita calcularTotales() de pedidos-fuente.js. Borrar este archivo y apagar
// CONFIG.DATOS_EJEMPLO.pedidos cuando se mergeen #52 y #25.

const PedidosEjemplo = (() => {
    let pedidos = [];
    let catalogo = new Map();   // productoId -> producto (con stock en memoria)
    let clientes = new Map();
    let siguienteId = 1;
    let siguienteFactura = 1;
    const hoy = () => new Date().toISOString().slice(0, 10);
    const responder = valor => new Promise(resolver => setTimeout(() => resolver(structuredClone(valor)), 150));

    function estadoEntrega(p) {
        if (p.estado === 'ENTREGADO') return 'ENTREGADA';
        return p.fechaEnvio ? 'PACTADA' : 'SIN_PACTAR';
    }

    function armar(datos, fecha) {
        const cliente = clientes.get(datos.clienteId);
        if (!cliente) throw new Error(`Cliente ${datos.clienteId} no encontrado`);
        if (!(datos.formaPago in FORMAS_PAGO)) throw new Error('La forma de pago no es válida');
        const descuentoPct = datos.descuentoPct ?? 0;
        if (descuentoPct < 0 || descuentoPct > DESCUENTO_MAXIMO) throw new Error(`El descuento debe estar entre 0 y ${DESCUENTO_MAXIMO}%`);
        if (!datos.items?.length) throw new Error('El pedido tiene que tener al menos un producto');
        if (datos.fechaEnvio && datos.fechaEnvio < fecha.slice(0, 10)) throw new Error('La fecha de envío no puede ser anterior al pedido');
        // Un producto repetido se suma en una línea; el precio se pacta al crear.
        const porProducto = new Map();
        for (const { productoId, cantidad } of datos.items) {
            if (!Number.isInteger(cantidad) || cantidad <= 0) throw new Error('Las cantidades deben ser enteros positivos');
            const producto = catalogo.get(productoId);
            if (!producto) throw new Error(`Producto ${productoId} no encontrado`);
            const linea = porProducto.get(productoId) ?? { productoId, producto: producto.nombre, marca: producto.marca ?? null, cantidad: 0, precioUnitario: producto.precio };
            linea.cantidad += cantidad;
            porProducto.set(productoId, linea);
        }
        const items = [...porProducto.values()].map(l => ({ ...l, subtotal: Math.round(l.cantidad * l.precioUnitario * 100) / 100 }));
        const totales = calcularTotales(items, descuentoPct);
        const id = siguienteId++;
        return {
            id, numeroOrden: `OC-${fecha.slice(0, 4)}-${String(id).padStart(6, '0')}`, fecha,
            clienteId: cliente.id, cliente: cliente.nombre, clienteCalidad: cliente.calidad, clienteClasificacion: cliente.clasificacion,
            formaPago: datos.formaPago, descuentoPct, conDescuento: descuentoPct > 0, ...totales,
            fechaEnvio: datos.fechaEnvio || null, estado: 'PENDIENTE', estadoEntrega: null, numeroFactura: null, items,
        };
    }

    function buscar(id) {
        const pedido = pedidos.find(p => p.id === Number(id));
        if (!pedido) throw new Error(`Pedido ${id} no encontrado`);
        return pedido;
    }

    function exigirEstado(pedido, ...estados) {
        if (!estados.includes(pedido.estado)) throw new Error(`El pedido ${pedido.numeroOrden} está ${pedido.estado.toLowerCase()}: no se puede hacer esa operación`);
    }

    const conEntrega = p => ({ ...p, estadoEntrega: estadoEntrega(p) });

    function confirmarInterno(pedido) {
        exigirEstado(pedido, 'PENDIENTE');
        // Todo o nada (docs/API-PEDIDOS.md): si falta stock de algún producto, no se descuenta nada.
        for (const l of pedido.items) {
            const p = catalogo.get(l.productoId);
            if (p.stockActual < l.cantidad) throw new Error(`Stock insuficiente para ${p.nombre} (disponible: ${p.stockActual}, pedido: ${l.cantidad})`);
        }
        const advertencias = [];
        for (const l of pedido.items) {
            const p = catalogo.get(l.productoId);
            p.stockActual -= l.cantidad;
            if (p.stockActual <= p.stockMinimo) advertencias.push(`${p.nombre} quedó con stock ${p.stockActual}, en o por debajo del mínimo ${p.stockMinimo}`);
        }
        pedido.estado = 'CONFIRMADO';
        return advertencias;
    }

    return {
        /** Carga productos y clientes reales (o de ejemplo) y arma unos pedidos en distintos estados. */
        inicializar(productos, listaClientes) {
            if (pedidos.length) return;
            catalogo = new Map(productos.map(p => [p.id, { ...p }]));
            clientes = new Map(listaClientes.map(c => [c.id, c]));
            const ps = productos.filter(p => p.stockActual > 10).slice(0, 4);
            const cs = listaClientes.filter(c => c.nombre !== 'Consumidor final');
            if (!ps.length || !cs.length) return;
            const ejemplo = (i, estado, extra = {}) => {
                const fecha = new Date(Date.now() - (6 - i) * 86400000).toISOString().slice(0, 10) + 'T10:30:00';
                const items = [{ productoId: ps[i % ps.length].id, cantidad: 2 + i }, { productoId: ps[(i + 1) % ps.length].id, cantidad: 1 + i }];
                const p = armar({ clienteId: cs[i % cs.length].id, formaPago: Object.keys(FORMAS_PAGO)[i % 4], descuentoPct: [0, 10, 5, 0, 20][i], items, ...extra }, fecha);
                pedidos.push(p);
                if (estado !== 'PENDIENTE' && estado !== 'CANCELADO') confirmarInterno(p);
                if (estado === 'ENVIADO' || estado === 'ENTREGADO') { p.estado = 'ENVIADO'; p.fechaEnvio ??= fecha.slice(0, 10); }
                if (estado === 'ENTREGADO') { p.estado = 'ENTREGADO'; p.numeroFactura = `FAC-0001-${String(siguienteFactura++).padStart(8, '0')}`; }
                if (estado === 'CANCELADO') p.estado = 'CANCELADO';
            };
            ['ENTREGADO', 'ENVIADO', 'CONFIRMADO', 'CANCELADO', 'PENDIENTE'].forEach((estado, i) => ejemplo(i, estado));
        },

        listar: async (filtros = {}, pagina = 0, tamanio = 20) => {
            if (filtros.desde && filtros.hasta && filtros.desde > filtros.hasta) throw new Error('El rango de fechas está invertido');
            const lista = pedidos.filter(p =>
                (!filtros.estado || p.estado === filtros.estado)
                && (!filtros.clienteId || p.clienteId === Number(filtros.clienteId))
                && (!filtros.desde || p.fecha.slice(0, 10) >= filtros.desde)
                && (!filtros.hasta || p.fecha.slice(0, 10) <= filtros.hasta)
                && (!filtros.numeroOrden || p.numeroOrden === filtros.numeroOrden))
                .sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id);
            return responder({
                contenido: lista.slice(pagina * tamanio, (pagina + 1) * tamanio).map(conEntrega),
                pagina, tamanio, totalElementos: lista.length, totalPaginas: Math.ceil(lista.length / tamanio),
            });
        },
        obtener: async id => responder(conEntrega(buscar(id))),
        crear: async datos => {
            const pedido = armar(datos, new Date().toISOString().slice(0, 19));
            pedidos.push(pedido);
            return responder(conEntrega(pedido));
        },
        confirmar: async id => {
            const pedido = buscar(id);
            const advertencias = confirmarInterno(pedido);
            return responder({ ...conEntrega(pedido), advertencias });
        },
        enviar: async id => {
            const pedido = buscar(id);
            exigirEstado(pedido, 'CONFIRMADO');
            pedido.fechaEnvio ??= hoy();
            pedido.estado = 'ENVIADO';
            return responder(conEntrega(pedido));
        },
        entregar: async id => {
            const pedido = buscar(id);
            exigirEstado(pedido, 'ENVIADO');
            pedido.estado = 'ENTREGADO';
            pedido.numeroFactura = `FAC-0001-${String(siguienteFactura++).padStart(8, '0')}`;
            return responder(conEntrega(pedido));
        },
        cancelar: async id => {
            const pedido = buscar(id);
            exigirEstado(pedido, 'PENDIENTE', 'CONFIRMADO');
            if (pedido.estado === 'CONFIRMADO') pedido.items.forEach(l => { catalogo.get(l.productoId).stockActual += l.cantidad; });
            pedido.estado = 'CANCELADO';
            return responder(conEntrega(pedido));
        },
        pactarFecha: async (id, fechaEnvio) => {
            const pedido = buscar(id);
            exigirEstado(pedido, 'PENDIENTE', 'CONFIRMADO');
            if (!fechaEnvio || fechaEnvio < hoy()) throw new Error('La fecha de envío no puede ser anterior a hoy');
            pedido.fechaEnvio = fechaEnvio;
            return responder(conEntrega(pedido));
        },
    };
})();
