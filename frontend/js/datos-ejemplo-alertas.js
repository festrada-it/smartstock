// Datos de ejemplo del panel unificado de alertas mientras no existe GET /api/alertas/panel (#32, PR #44).
// Respetan el contrato de docs/PANEL-ALERTAS.md: lista de {tipo, producto_id, producto, criticidad,
// detalle, accion_sugerida} ordenada ALTA → MEDIA → BAJA, con filtros tipo y criticidad (AND).
// Borrar este archivo y apagar CONFIG.DATOS_EJEMPLO.panelAlertas cuando se mergee el endpoint.

const AlertasPanelEjemplo = (() => {
    const TIPOS = ['QUIEBRE', 'REPOSICION', 'STOCK_MINIMO', 'VENCIMIENTO', 'BAJA_ROTACION'];
    const ORDEN = { ALTA: 0, MEDIA: 1, BAJA: 2 };

    // Productos del seed (02, 04 y 05). Un producto puede tener varias alertas.
    const alertas = [
        { tipo: 'QUIEBRE', producto_id: 8, producto: 'Detergente 750ml', criticidad: 'ALTA', detalle: 'Quiebre estimado en 3 días', accion_sugerida: 'Reponer 64 unidades' },
        { tipo: 'VENCIMIENTO', producto_id: 10, producto: 'Yogur natural 190g', criticidad: 'ALTA', detalle: 'Lote 14: 2 días restantes', accion_sugerida: 'Vender primero' },
        { tipo: 'QUIEBRE', producto_id: 4, producto: 'Harina 000 1kg', criticidad: 'ALTA', detalle: 'Quiebre estimado en 5 días', accion_sugerida: 'Reponer 71 unidades' },
        { tipo: 'VENCIMIENTO', producto_id: 11, producto: 'Tomate redondo 1kg', criticidad: 'ALTA', detalle: 'Lote 17: -1 días restantes', accion_sugerida: 'Retirar de la venta' },
        { tipo: 'REPOSICION', producto_id: 8, producto: 'Detergente 750ml', criticidad: 'MEDIA', detalle: 'El stock alcanzó el punto de reposición', accion_sugerida: 'Comprar 64 unidades' },
        { tipo: 'STOCK_MINIMO', producto_id: 8, producto: 'Detergente 750ml', criticidad: 'MEDIA', detalle: 'Stock 12 en o por debajo del mínimo 30', accion_sugerida: 'Reponer hasta cubrir el stock mínimo' },
        { tipo: 'STOCK_MINIMO', producto_id: 5, producto: 'Agua mineral 2L', criticidad: 'MEDIA', detalle: 'Stock 25 en o por debajo del mínimo 30', accion_sugerida: 'Reponer hasta cubrir el stock mínimo' },
        { tipo: 'QUIEBRE', producto_id: 5, producto: 'Agua mineral 2L', criticidad: 'MEDIA', detalle: 'Quiebre estimado en 11 días', accion_sugerida: 'Reponer 58 unidades' },
        { tipo: 'REPOSICION', producto_id: 2, producto: 'Fideos tirabuzón 500g', criticidad: 'MEDIA', detalle: 'El stock alcanzó el punto de reposición', accion_sugerida: 'Comprar 40 unidades' },
        { tipo: 'BAJA_ROTACION', producto_id: 7, producto: 'Lavandina 1L', criticidad: 'MEDIA', detalle: 'Rotación por debajo del umbral del grupo', accion_sugerida: 'Comprar menos' },
        { tipo: 'BAJA_ROTACION', producto_id: 3, producto: 'Arroz largo fino 1kg', criticidad: 'MEDIA', detalle: 'Rotación por debajo del umbral del grupo', accion_sugerida: 'No recomprar / promocionar' },
        { tipo: 'VENCIMIENTO', producto_id: 9, producto: 'Leche entera 1L', criticidad: 'BAJA', detalle: 'Lote 12: 11 días restantes', accion_sugerida: 'Vender primero' },
    ];

    const responder = valor => new Promise(resolver => setTimeout(() => resolver(structuredClone(valor)), 150));

    return {
        panel: async ({ tipo = null, criticidad = null } = {}) => {
            // La API valida los filtros y responde con error explícito (docs/PANEL-ALERTAS.md).
            if (tipo !== null && !TIPOS.includes(tipo)) throw new Error('Tipo de alerta inválido');
            if (criticidad !== null && !(criticidad in ORDEN)) throw new Error('Criticidad inválida');
            const seleccionadas = alertas.filter(a => (tipo === null || a.tipo === tipo) && (criticidad === null || a.criticidad === criticidad));
            // sort estable: conserva el orden de entrada dentro de la misma criticidad.
            return responder(seleccionadas.sort((a, b) => ORDEN[a.criticidad] - ORDEN[b.criticidad]));
        },
    };
})();
