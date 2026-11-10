// Armado de los datos que el catálogo y Grupos envían a core (contrato en docs/API-PRODUCTOS-GRUPOS.md).
// Funciones puras: las usan productos.js y grupos.js, y se prueban en frontend/tests/.

/**
 * Marca a enviar. En el PUT, null conserva la marca actual y "" la borra; por eso, al editar,
 * un campo vacío se manda como "". Al crear, vacío es null (sin marca).
 */
function marcaParaEnviar(valor, esEdicion) {
    const marca = (valor ?? '').trim();
    if (marca) return marca;
    return esEdicion ? '' : null;
}

/** 0.1234 → 12.34: el umbral de rotación (0 a 1, hasta 4 decimales) como porcentaje con hasta 2 decimales. */
function porcentajeDesdeUmbral(umbral) {
    return Number((umbral * 100).toFixed(2));
}

/** 0.1234 → "12,34%"; 0.1 → "10%". */
function formatoPorcentaje(umbral) {
    return `${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(porcentajeDesdeUmbral(umbral))}%`;
}

/**
 * Umbral a enviar desde el porcentaje del formulario. Si no cambió respecto de lo que se mostró,
 * se reenvía el valor original de core tal cual (no se omite: core lo volvería a 0.10).
 */
function umbralParaEnviar(porcentaje, umbralOriginal) {
    if (umbralOriginal != null && Number(porcentaje) === porcentajeDesdeUmbral(umbralOriginal)) return umbralOriginal;
    return Number((Number(porcentaje) / 100).toFixed(4));
}

if (typeof module !== 'undefined') {
    module.exports = { marcaParaEnviar, porcentajeDesdeUmbral, formatoPorcentaje, umbralParaEnviar };
}
