// Calidad (BUENA / REGULAR / MALA) y clasificación de 1 a 5 estrellas: las usan proveedores y clientes.

const CALIDADES = {
    BUENA: { texto: 'Buena', clase: 'text-bg-success' },
    REGULAR: { texto: 'Regular', clase: 'text-bg-warning' },
    MALA: { texto: 'Mala', clase: 'text-bg-danger' },
};

/** Clasificación de 1 a 5 como estrellas, con texto alternativo para lectores de pantalla. */
function estrellas(clasificacion, clase = '') {
    const llenas = '★'.repeat(clasificacion);
    const vacias = '☆'.repeat(5 - clasificacion);
    return `<span class="tw-whitespace-nowrap tw-text-amber-500 ${clase}" role="img" aria-label="${clasificacion} de 5 estrellas"
                  title="${clasificacion} de 5">${llenas}<span class="tw-text-slate-300">${vacias}</span></span>`;
}

function badgeCalidad(calidad) {
    const c = CALIDADES[calidad] ?? { texto: escaparHtml(calidad), clase: 'text-bg-secondary' };
    return `<span class="badge ${c.clase}">${c.texto}</span>`;
}

/** ≤ 2 estrellas o calidad MALA: proveedor al que hay que pedirle antes / cliente al que conviene venderle con cuidado. */
function esDeRiesgo(calificado) {
    return calificado.clasificacion <= 2 || calificado.calidad === 'MALA';
}
